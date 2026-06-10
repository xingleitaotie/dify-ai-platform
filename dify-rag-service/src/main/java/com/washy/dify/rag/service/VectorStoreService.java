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


    /**
     * 获取指定知识库的文档列表
     */
    List<Map<String, Object>> getDocuments(String kbId);


    /**
     * 更新知识库配置
     */
    void updateConfig(String kbId, Map<String, Object> config);

    /**
     * 获取指定知识库的配置
     */
    Map<String, Object> getConfig(String kbId);


    // ==================== 新增：提示词模板专用方法 ====================

    // ==================== 提示词模板专用方法 ====================

    /**
     * 存储提示词模板到向量库
     * @param templateId 模板ID
     * @param templateName 模板名称
     * @param content 模板内容（用于向量化）
     * @param metadata 元数据（类型、标签、分类等）
     */
    void storePromptTemplate(String templateId, String templateName,
                             String content, Map<String, Object> metadata);

    /**
     * 批量存储提示词模板
     */
    void batchStorePromptTemplates(List<Map<String, Object>> templates);

    /**
     * 删除提示词模板向量
     * @param templateId 模板ID
     */
    void deletePromptTemplate(String templateId);

    /**
     * 批量删除提示词模板
     */
    void batchDeletePromptTemplates(List<String> templateIds);

    /**
     * 搜索相似的提示词模板
     * @param query 查询文本
     * @param topK 返回数量
     * @return 搜索结果（包含模板ID和相似度）
     */
    List<Map<String, Object>> searchPromptTemplates(String query, int topK);

    /**
     * 更新提示词模板向量
     */
    void updatePromptTemplate(String templateId, String templateName,
                              String content, Map<String, Object> metadata);

    /**
     * 获取所有提示词模板
     */
    List<Map<String, Object>> listAllPromptTemplates();

    /**
     * 获取提示词模板数量
     */
    int getPromptTemplateCount();

    /**
     * 获取提示词模板集合名称
     */
    String getPromptTemplateCollectionName();
}
