package com.washy.dify.rag.service;

import com.washy.dify.rag.domain.DocumentChunk;
import com.washy.dify.rag.factory.VectorStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HybridStoreService {

    @Autowired
    private VectorStoreFactory vectorStoreFactory;

    @Autowired
    private AIOptimizationService aiOptimizationService;
    
    /**
     * 混合策略：
     * 1. 先快速存储原始内容（用户立即可查）
     * 2. 后台异步进行AI优化（提升质量）
     * 3. 优化完成后更新向量库
     */
    public void storeWithHybridStrategy(List<DocumentChunk> chunks,
                                        String documentId, String fileName,String collectionName) {

        // 第一步：快速存储原始内容
        int immediateCount = vectorStoreFactory.getVectorStoreService().storeDocumentChunks(collectionName,
                chunks, documentId, fileName
        );
        log.info("快速存储完成: {} 个块", immediateCount);

        // 第二步：异步AI优化（带超时保护）
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始异步AI优化: {}", fileName);
                long startTime = System.currentTimeMillis();

                // 只优化重要块
                List<DocumentChunk> importantChunks = chunks.stream()
                        .filter(c -> c.getContent() != null && c.getContent().length() > 200)
                        .collect(Collectors.toList());

                // AI优化（内部已有超时保护）
                List<DocumentChunk> optimizedChunks = aiOptimizationService
                        .optimizeChunks(importantChunks);

                // 合并未优化的块
                Set<String> optimizedIds = optimizedChunks.stream()
                        .map(DocumentChunk::getChunkId)
                        .collect(Collectors.toSet());

                List<DocumentChunk> finalChunks = new ArrayList<>(optimizedChunks);
                chunks.stream()
                        .filter(c -> !optimizedIds.contains(c.getChunkId()))
                        .forEach(finalChunks::add);

                // 按序号排序
                finalChunks.sort(Comparator.comparingInt(c -> {
                    try {
                        String[] parts = c.getChunkId().split("_");
                        return Integer.parseInt(parts[parts.length - 1]);
                    } catch (Exception e) {
                        return 0;
                    }
                }));

                // 更新向量库
                vectorStoreFactory.getVectorStoreService().deleteDocumentChunks(collectionName,documentId);
                vectorStoreFactory.getVectorStoreService().storeDocumentChunks(
                        collectionName,finalChunks, documentId, fileName
                );

                long elapsed = System.currentTimeMillis() - startTime;
                log.info("异步AI优化完成: {} ({}ms, {}个块)", fileName, elapsed, optimizedChunks.size());

            } catch (Exception e) {
                log.error("异步AI优化失败: {}", fileName, e);
            }
        }).exceptionally(ex -> {
            log.error("异步任务异常: {}", ex.getMessage());
            return null;
        });
    }
}