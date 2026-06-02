package com.washy.dify.rag.service;

import com.washy.dify.rag.domain.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface VectorStoreService {
    // ==================== 知识库管理 ====================

    /**
     * 获取所有知识库列表
     */
    List<Map<String, Object>> listKnowledgeBases();

    /**
     * 创建知识库
     */
    Map<String, Object> createKnowledgeBase(String name, String description);

    /**
     * 删除知识库
     */
    void deleteKnowledgeBase(String name);

    // ==================== 文档管理 ====================

    /**
     * 将文档块存入指定知识库
     */
    int storeDocumentChunks(String kbName, List<DocumentChunk> chunks,
                            String documentId, String documentName);

    /**
     * 删除文档的所有块
     */
    void deleteDocumentChunks(String documentId);

    /**
     * 删除指定知识库中对应文档的所有块
     */
    void deleteDocumentChunks(String kbName, String documentId);

    /**
     * 获取指定知识库的所有分块
     */
    List<Map<String, Object>> listAllChunks(String kbName);

    /**
     * 在指定知识库中搜索
     */
    List<Map<String, Object>> search(String kbName, String query, int topK);


    // ==================== 兼容旧接口 ====================

    /**
     * 将文档块存入默认知识库（兼容旧代码）
     */
    int storeDocumentChunks(List<DocumentChunk> chunks, String documentId, String documentName);

    /**
     * 获取默认知识库的所有分块（兼容旧代码）
     */
    List<Map<String, Object>> listAllChunks();

    /**
     * 在默认知识库中搜索（兼容旧代码）
     */
    List<Map<String, Object>> search(String query, int topK);

    /**
     * 清空默认知识库
     */
    void clearCollection();

    /**
     * 获取单个块详情
     */
    Map<String, Object> getChunkDetail(String chunkId);

    /**
     * 获取指定知识库的文档列表
     */
    List<Map<String, Object>> getDocuments(String kbId);

    /**
     * 获取默认知识库配置
     */
    Map<String, Object> getDefaultConfig();

    /**
     * 更新知识库配置
     */
    void updateConfig(String kbId, Map<String, Object> config);

    /**
     * 获取指定知识库的配置
     */
    Map<String, Object> getConfig(String kbId);
}
