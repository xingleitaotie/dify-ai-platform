package com.washy.dify.rag.controller;

import com.washy.dify.common.entity.rag.EmbeddingDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.rag.domain.dto.ChunkResult;
import com.washy.dify.rag.domain.dto.RagSearchRequest;
import com.washy.dify.rag.factory.EmbeddingFactory;
import com.washy.dify.rag.factory.VectorStoreFactory;
import com.washy.dify.rag.service.AdaptiveTextChunker;
import com.washy.dify.rag.service.RagQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * RAG控制器 - Day2 文档上传+分块
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/rag")
public class RagController {

    private final EmbeddingFactory embeddingFactory;
    private final VectorStoreFactory vectorStoreFactory;
    private final AdaptiveTextChunker chunker;
    private final RagQueryService ragQueryService;
    /**
     * 文档上传并自动分块
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kbName", required = false) String kbName) {
        long start = System.currentTimeMillis();
        log.info("收到文档上传请求：{}", file.getOriginalFilename());
        String collectionName = kbName != null && !kbName.isEmpty() ? kbName : "default_knowledge_base";
        ChunkResult result = chunker.processDocument(file,collectionName);
        long elapsed = System.currentTimeMillis() - start;
            // ===== 构建响应 =====
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("documentId", result.getDocumentId());
        response.put("fileName", result.getFileName());
        response.put("chunks", result.getChunkCount());
        response.put("images", result.getImageCount());
        response.put("tables", result.getTableCount());
        response.put("processingTime", elapsed + "ms");
        response.put("method", "file_upload");

        if (!result.isSuccess()) {
            response.put("error", result.getError());
        }

        return ResponseEntity.ok(response);

    }

    @PostMapping("/embedding/single")
    public Result<List<Float>> singleEmbedding(@RequestBody EmbeddingDTO dto) {
        log.info("收到单文本向量化请求");
        return Result.success(embeddingFactory.getEmbeddingService().getEmbedding(dto.getText()));
    }


    /**
     * 直接传入文本内容
     * 暂未实现指定知识库存储，只会写入默认知识库中
     */
    @PostMapping("/text")
    public ResponseEntity<Map<String, Object>> processText(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String title = request.getOrDefault("title", "文本文档");

        if (content == null || content.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "内容不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        long start = System.currentTimeMillis();
        ChunkResult result = chunker.processTextContent(content, title);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("documentId", result.getDocumentId());
        response.put("chunks", result.getChunkCount());
        response.put("processingTime", elapsed + "ms");
        response.put("method", "text_api");

        return ResponseEntity.ok(response);
    }

    /**
     * 工作流查询向量库测试检索功能
     */
    @PostMapping("/search/single/document")
    public Result<Map<String, Object>> searchSignleDocument(@RequestBody RagSearchRequest dto) {

        try {
            if (dto != null && !dto.getQuery().isEmpty()) {
                String query = dto.getQuery();
                String kb = dto.getKb() == null ? "" : dto.getKb();
                int topK = dto.getTopK() == 0 ? 5 : dto.getTopK();

                // 1. 检索相关文档块
                Map<String, Object> content = ragQueryService.queryForWorkflowStructured(query,kb,topK);

                return Result.success(content);
            }else{
                log.error("RAG查询添加为空");
                return Result.error("RAG查询添加为空");
            }

        } catch (Exception e) {
            log.error("RAG查询失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 多个知识库节点查询
     * 工作流查询向量库形成提示词
     */
    @PostMapping("/search/much/document")
    public Result<String> searchMuchDocument(@RequestBody Map<String,Object> request) {

        try {
            if (request != null && null != request.get("query") && !"".equals(request.get("query"))) {
                String query = request.get("query").toString();
                List<String> kbs = request.get("kbs") == null
                        ? Collections.singletonList("")
                        : (List<String>) request.get("kbs");
                int topN = request.get("topN") == "5" ? Integer.parseInt(request.get("topN").toString()) : 5;

                // 1. 检索相关文档块
                String response = ragQueryService.queryMultipleForWorkflow(query,kbs,topN);

                return Result.success(response);
            }else{
                log.error("RAG查询添加为空");
                return Result.error("RAG查询添加为空");
            }

        } catch (Exception e) {
            log.error("RAG查询失败", e);
            return Result.error(e.getMessage());
        }
    }


    /**
     * 查看所有存储的块
     */
    @GetMapping("/chunks")
    public ResponseEntity<Map<String, Object>> listAllChunks() {
        try {
            List<Map<String, Object>> chunks = vectorStoreFactory.getVectorStoreService().listAllChunks();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("total", chunks.size());
            result.put("chunks", chunks);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取块列表失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * 获取单个块详情
     */
    @GetMapping("/chunks/{chunkId}")
    public ResponseEntity<Map<String, Object>> getChunkDetail(@PathVariable String chunkId) {
        try {
            Map<String, Object> detail = vectorStoreFactory.getVectorStoreService().getChunkDetail(chunkId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("chunk", detail);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取块详情失败: {}", chunkId, e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * 删除集合
     */
    @PostMapping("/vector/clear")
    public Result<String> clearVector() {

        log.info("开始删除集合");
        vectorStoreFactory.getVectorStoreService().clearCollection();
        return Result.success("删除集合成功");
    }

}