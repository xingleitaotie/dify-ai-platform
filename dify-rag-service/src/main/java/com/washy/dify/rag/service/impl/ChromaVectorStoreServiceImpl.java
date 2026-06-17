package com.washy.dify.rag.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.common.constants.SystemConstants;
import com.washy.dify.rag.client.ChromaApiClient;
import com.washy.dify.rag.client.OllamaEmbeddingClient;
import com.washy.dify.rag.config.RagProperties;
import com.washy.dify.rag.domain.DocumentChunk;
import com.washy.dify.rag.domain.ImageInfo;
import com.washy.dify.rag.domain.TableInfo;
import com.washy.dify.rag.service.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class ChromaVectorStoreServiceImpl implements VectorStoreService {

    @Autowired
    private ChromaApiClient chromaApiClient;

    @Autowired(required = false)
    private OllamaEmbeddingClient embeddingClient;

    @Resource
    private RagProperties ragProperties;

    // 提示词模板专用集合
    private static final String PROMPT_TEMPLATE_COLLECTION = SystemConstants.PROMPT_TEMPLATE_COLLECTION;

    // ==================== 知识库管理 ====================

    @Override
    public List<Map<String, Object>> listKnowledgeBases() {
        List<Map<String, Object>> collections = chromaApiClient.listAllCollections();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> collection : collections) {
            if(null != collection.get("name") && !SystemConstants.PROMPT_TEMPLATE_COLLECTION.equals(collection.get("name"))){
                String name = (String) collection.get("name");
                Map<String, Object> kb = new HashMap<>();
                kb.put("id", collection.get("id"));
                kb.put("name", name);
                kb.put("description", collection.getOrDefault("description", ""));
                kb.put("documents", chromaApiClient.listDocuments(name) == null ? 0 : chromaApiClient.listDocuments(name).size());
                kb.put("chunkCount", chromaApiClient.getCollectionCount(name));
                result.add(kb);
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> createKnowledgeBase(String name, String description) {
        String existingId = chromaApiClient.getCollectionIdByName(name);
        if (existingId != null) {
            throw new RuntimeException("知识库名称已存在");
        }

        String collectionId = chromaApiClient.createCollection(name);

        Map<String, Object> result = new HashMap<>();
        result.put("id", collectionId);
        result.put("name", name);
        result.put("description", description);
        result.put("chunkCount", 0);

        return result;
    }

    @Override
    public void deleteKnowledgeBase(String name) {
        chromaApiClient.deleteCollection(name);
    }

    // ==================== 文档管理 ====================

    @Override
    public int storeDocumentChunks(String kbName, List<DocumentChunk> chunks,
                                   String documentId, String documentName) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }
        int successCount = 0;

        log.info("开始存储文档，共 {} 个块", chunks.size());

        // 逐块处理，避免并发过高
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);

            try {
                // 1. 构建存储文本
                String text = buildStorageText(chunk);

                // 2. 生成 embedding（单个生成，避免并发）
                List<Float> embedding;
                try {
                    embedding = embeddingClient.getEmbedding(text);
                } catch (Exception e) {
                    log.error("生成 embedding 失败，跳过当前块: chunkId={}, error={}",
                            chunk.getChunkId(), e.getMessage());
                    continue;
                }

                // 3. 准备数据
                List<String> ids = Collections.singletonList(chunk.getChunkId());
                List<List<Float>> embeddings = Collections.singletonList(embedding);
                List<String> texts = Collections.singletonList(text);
                List<Map<String, Object>> metadatas = Collections.singletonList(
                        buildMetadata(chunk, documentId, documentName)
                );

                // 4. 存入 ChromaDB
                chromaApiClient.addEmbeddings(kbName, ids, embeddings, texts, metadatas);
                successCount++;

                // 打印进度
                if ((i + 1) % 10 == 0 || i == chunks.size() - 1) {
                    log.info("存储进度: {}/{}", i + 1, chunks.size());
                }

                // 每个块处理后短暂休息，避免 Ollama 过载
                if ((i + 1) % 3 == 0) {
                    Thread.sleep(100);
                }

            } catch (Exception e) {
                log.error("存储块失败，跳过: chunkId={}, error={}", chunk.getChunkId(), e.getMessage());
            }
        }

        log.info("存储完成，成功 {}/{} 个块", successCount, chunks.size());
        return successCount;
    }

    @Override
    public List<Map<String, Object>> listAllChunks(String kbName) {
        return chromaApiClient.listAllVectors(kbName);
    }

    @Override
    public List<Map<String, Object>> search(String kbName, String query, int topK) {

        try {
            List<Float> queryEmbedding = embeddingClient.getEmbedding(query);
            JSONObject result = chromaApiClient.queryEmbedding(kbName, queryEmbedding, topK);
            return parseSearchResult(result);
        } catch (Exception e) {
            log.error("搜索失败: kbName={}, query={}", kbName, query, e);
            return new ArrayList<>();
        }
    }

    /**
     * 删除指定知识库中对应文档的所有块
     */
    @Override
    public void deleteDocumentChunks(String collectionName, String documentId){
        log.info("删除集合：{}中的文档块: documentId={}",collectionName, documentId);
        chromaApiClient.deleteDocumentChunks(collectionName, documentId);
    }


    // ==================== 私有辅助方法 ====================

    private String buildStorageText(DocumentChunk chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append("【章节】").append(chunk.getSectionNumber())
                .append(" ").append(chunk.getSectionTitle()).append("\n\n");
        sb.append(chunk.getContent());

        List<ImageInfo> images = chunk.getImages();
        if (images != null && !images.isEmpty()) {
            sb.append("\n\n【相关图片】\n");
            for (ImageInfo img : images) {
                sb.append("- ").append(img.getOriginalName())
                        .append(" (").append(img.getFormat())
                        .append(", ").append(img.getSize() / 1024).append("KB)\n");
            }
        }

        List<TableInfo> tables = chunk.getTables();
        if (tables != null && !tables.isEmpty()) {
            sb.append("\n\n【相关表格】\n");
            for (TableInfo table : tables) {
                sb.append("- ").append(table.getCaption())
                        .append(" (").append(table.getRowCount()).append("行")
                        .append(" x ").append(table.getColumnCount()).append("列)\n");
            }
        }

        return sb.toString();
    }

    /**
     * 构建符合 Chroma 要求的 metadata
     * Chroma metadata 只支持: string, number, boolean, null
     */
    private Map<String, Object> buildMetadata(DocumentChunk chunk, String documentId, String documentName) {
        Map<String, Object> metadata = new HashMap<>();

        // 字符串类型
        metadata.put("documentId", documentId != null ? documentId : "");
        metadata.put("documentName", documentName != null ? documentName : "");
        metadata.put("chunkId", chunk.getChunkId() != null ? chunk.getChunkId() : "");
        metadata.put("sectionTitle", chunk.getSectionTitle() != null ? chunk.getSectionTitle() : "");
        String sectionNumberStr = "0";
        // 修复关键点：sectionNumber 转换为数字类型
        if(null != chunk.getSectionNumber() && !"".equals(chunk.getSectionNumber())) {
            sectionNumberStr = chunk.getSectionNumber();
        }
        if (sectionNumberStr != null && !sectionNumberStr.isEmpty()) {
            try {
                // 尝试转换为整数
                metadata.put("sectionNumber", Integer.parseInt(sectionNumberStr));
            } catch (NumberFormatException e) {
                try {
                    // 尝试转换为浮点数
                    metadata.put("sectionNumber", Double.parseDouble(sectionNumberStr));
                } catch (NumberFormatException e2) {
                    // 转换失败，使用默认值 0
                    metadata.put("sectionNumber", 0);
                }
            }
        } else {
            metadata.put("sectionNumber", 0);
        }

        // sectionLevel 转换为数字
        Integer sectionLevel = chunk.getSectionLevel();
        if (sectionLevel != null) {
            metadata.put("sectionLevel", sectionLevel);
        } else {
            metadata.put("sectionLevel", 1);
        }

        // 内容长度（数字）
        String content = chunk.getContent();
        metadata.put("contentLength", content != null ? content.length() : 0);

        // 其他 metadata 中的值也需要清理
        Map<String, Object> originalMetadata = chunk.getMetadata();
        if (originalMetadata != null && !originalMetadata.isEmpty()) {
            for (Map.Entry<String, Object> entry : originalMetadata.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value == null) {
                    metadata.put(key, null);
                } else if (value instanceof String) {
                    metadata.put(key, value);
                } else if (value instanceof Number) {
                    metadata.put(key, value);
                } else if (value instanceof Boolean) {
                    metadata.put(key, value);
                } else if (value instanceof Date) {
                    metadata.put(key, ((Date) value).getTime());
                } else if (value instanceof Collection) {
                    // 集合类型转为 JSON 字符串或忽略
                    metadata.put(key, value.toString());
                } else {
                    // 其他类型转为字符串
                    metadata.put(key, value.toString());
                }
            }
        }

        return metadata;
    }

    private List<Map<String, Object>> parseSearchResult(JSONObject result) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (result == null) return results;

        try {
            Object distancesObj = result.get("distances");
            Object documentsObj = result.get("documents");
            Object metadatasObj = result.get("metadatas");

            if (distancesObj == null) return results;

            List<?> distancesOuter = (List<?>) distancesObj;
            if (distancesOuter.isEmpty()) return results;

            List<?> distances = (List<?>) distancesOuter.get(0);
            List<?> documents = (documentsObj instanceof List && !((List<?>) documentsObj).isEmpty())
                    ? (List<?>) ((List<?>) documentsObj).get(0) : new ArrayList<>();
            List<?> metadatas = (metadatasObj instanceof List && !((List<?>) metadatasObj).isEmpty())
                    ? (List<?>) ((List<?>) metadatasObj).get(0) : new ArrayList<>();

            for (int i = 0; i < distances.size(); i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("rank", i + 1);

                Object distObj = distances.get(i);
                double distance = 0.0;
                if (distObj instanceof Number) {
                    distance = ((Number) distObj).doubleValue();
                }

                item.put("score", String.format("%.4f", 1.0 - distance));
                item.put("distance", String.format("%.4f", distance));
                item.put("document", i < documents.size() ? documents.get(i) : "");
                item.put("metadata", i < metadatas.size() ? metadatas.get(i) : new HashMap<>());
                results.add(item);
            }
        } catch (Exception e) {
            log.error("解析搜索结果失败", e);
        }

        return results;
    }

    @Override
    public List<Map<String, Object>> getDocuments(String kbId) {
        return chromaApiClient.listDocuments(kbId);
    }

    @Override
    public void updateConfig(String kbId, Map<String, Object> config) {
        chromaApiClient.updateKbConfig(kbId, config);
    }

    @Override
    public Map<String, Object> getConfig(String kbId) {
        Map<String, Object> config = new HashMap<>();
        config.put("name", kbId);
        config.put("chunkCount", chromaApiClient.getCollectionCount(kbId));
        config.put("embeddingModel", embeddingClient != null ? ragProperties.getEmbedding().getOllamaModel() : "nomic-embed-text");
        config.put("chunkSize", 500);
        config.put("chunkOverlap", 50);
        config.put("description", "Chroma 向量知识库");
        return config;
    }

    // ==================== 提示词模板实现 ====================

    @Override
    public void storePromptTemplate(String templateId, String templateName,
                                    String content, Map<String, Object> metadata) {
        try {
            log.info("📝 存储提示词模板到向量库: templateId={}, name={}", templateId, templateName);

            // 1. 构建用于向量化的完整文本
            String vectorText = buildPromptTemplateVectorText(templateName, content, metadata);

            // 2. 生成向量
            List<Float> embedding = embeddingClient.getEmbedding(vectorText);

            // 3. 准备Chroma数据（使用templateId作为文档ID）
            List<String> ids = Collections.singletonList(templateId);
            List<List<Float>> embeddings = Collections.singletonList(embedding);
            List<String> documents = Collections.singletonList(vectorText);

            // 4. 构建元数据
            Map<String, Object> cleanedMetadata = buildTemplateMetadata(templateId, templateName, metadata);
            List<Map<String, Object>> metadatas = Collections.singletonList(cleanedMetadata);

            // 5. 存入Chroma
            chromaApiClient.addEmbeddings(PROMPT_TEMPLATE_COLLECTION, ids, embeddings, documents, metadatas);

            log.info("✅ 提示词模板存储成功: {}", templateId);

        } catch (Exception e) {
            log.error("❌ 存储提示词模板失败: templateId={}", templateId, e);
            throw new RuntimeException("存储模板失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void batchStorePromptTemplates(List<Map<String, Object>> templates) {
        if (templates == null || templates.isEmpty()) {
            return;
        }

        log.info("📦 批量存储提示词模板，数量: {}", templates.size());

        List<String> ids = new ArrayList<>();
        List<List<Float>> embeddings = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();

        for (Map<String, Object> template : templates) {
            try {
                String templateId = (String) template.get("templateId");
                String templateName = (String) template.get("templateName");
                String content = (String) template.get("content");
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) template.getOrDefault("metadata", new HashMap<>());

                if (templateId == null || content == null) {
                    log.warn("跳过无效模板: {}", template);
                    continue;
                }

                // 构建向量文本
                String vectorText = buildPromptTemplateVectorText(templateName, content, metadata);

                // 生成向量
                List<Float> embedding = embeddingClient.getEmbedding(vectorText);

                ids.add(templateId);
                embeddings.add(embedding);
                documents.add(vectorText);
                metadatas.add(buildTemplateMetadata(templateId, templateName, metadata));

            } catch (Exception e) {
                log.error("构建模板失败: {}", template.get("templateId"), e);
            }
        }

        if (!ids.isEmpty()) {
            chromaApiClient.addEmbeddings(PROMPT_TEMPLATE_COLLECTION, ids, embeddings, documents, metadatas);
            log.info("✅ 批量存储完成，成功: {}/{}", ids.size(), templates.size());
        }
    }

    @Override
    public void deletePromptTemplate(String templateId) {
        try {
            log.info("🗑️ 删除提示词模板向量: templateId={}", templateId);

            List<String> ids = Collections.singletonList(templateId);
            chromaApiClient.deleteVectors(PROMPT_TEMPLATE_COLLECTION, ids);

            log.info("✅ 提示词模板删除成功: {}", templateId);

        } catch (Exception e) {
            log.error("❌ 删除提示词模板失败: templateId={}", templateId, e);
            // 删除失败不抛出异常
        }
    }

    @Override
    public void batchDeletePromptTemplates(List<String> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return;
        }

        log.info("🗑️ 批量删除提示词模板，数量: {}", templateIds.size());

        try {
            chromaApiClient.deleteVectors(PROMPT_TEMPLATE_COLLECTION, templateIds);
            log.info("✅ 批量删除成功");
        } catch (Exception e) {
            log.error("❌ 批量删除失败", e);
        }
    }

    @Override
    public List<Map<String, Object>> searchPromptTemplates(String query, int topK) {
        try {
            log.info("🔍 搜索提示词模板: query={}, topK={}", query, topK);

            // 1. 生成查询向量
            List<Float> queryEmbedding = embeddingClient.getEmbedding(query);

            // 2. 执行查询
            JSONObject result = chromaApiClient.queryEmbedding(PROMPT_TEMPLATE_COLLECTION, queryEmbedding, topK);

            // 3. 解析结果
            return parseTemplateSearchResult(result);

        } catch (Exception e) {
            log.error("❌ 搜索提示词模板失败: query={}", query, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void updatePromptTemplate(String templateId, String templateName,
                                     String content, Map<String, Object> metadata) {
        // 先删除后添加
        deletePromptTemplate(templateId);
        storePromptTemplate(templateId, templateName, content, metadata);
        log.info("🔄 更新提示词模板: {}", templateId);
    }

    @Override
    public List<Map<String, Object>> listAllPromptTemplates() {
        try {
            log.info("📋 获取所有提示词模板");
            return chromaApiClient.listAllVectors(PROMPT_TEMPLATE_COLLECTION);
        } catch (Exception e) {
            log.error("❌ 获取提示词模板列表失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public int getPromptTemplateCount() {
        try {
            return chromaApiClient.getCollectionCount(PROMPT_TEMPLATE_COLLECTION);
        } catch (Exception e) {
            log.error("❌ 获取模板数量失败", e);
            return 0;
        }
    }

    @Override
    public String getPromptTemplateCollectionName() {
        return PROMPT_TEMPLATE_COLLECTION;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建提示词模板的向量化文本
     */
    private String buildPromptTemplateVectorText(String templateName, String content,
                                                 Map<String, Object> metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("模板名称：").append(templateName != null ? templateName : "").append("\n");
        sb.append("模板类型：").append(metadata != null ? metadata.getOrDefault("type", "CUSTOM") : "CUSTOM").append("\n");
        sb.append("分类：").append(metadata != null ? metadata.getOrDefault("category", "") : "").append("\n");
        sb.append("标签：").append(metadata != null ? metadata.getOrDefault("tags", "") : "").append("\n");
        sb.append("描述：").append(metadata != null ? metadata.getOrDefault("description", "") : "").append("\n");
        sb.append("内容：\n").append(content != null ? content : "");
        return sb.toString();
    }

    /**
     * 构建模板元数据
     */
    private Map<String, Object> buildTemplateMetadata(String templateId, String templateName,
                                                      Map<String, Object> originalMetadata) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("templateId", templateId);
        metadata.put("templateName", templateName != null ? templateName : "");
        metadata.put("templateType", originalMetadata != null ? originalMetadata.getOrDefault("type", "CUSTOM") : "CUSTOM");
        metadata.put("category", originalMetadata != null ? originalMetadata.getOrDefault("category", "") : "");
        metadata.put("tags", originalMetadata != null ? originalMetadata.getOrDefault("tags", "") : "");
        metadata.put("status", originalMetadata != null ? originalMetadata.getOrDefault("status", "ACTIVE") : "ACTIVE");
        metadata.put("useCount", originalMetadata != null ? originalMetadata.getOrDefault("useCount", 0) : 0);
        metadata.put("rating", originalMetadata != null ? originalMetadata.getOrDefault("rating", 0) : 0);
        metadata.put("createdAt", System.currentTimeMillis());

        return metadata;
    }

    /**
     * 解析模板搜索结果
     */
    private List<Map<String, Object>> parseTemplateSearchResult(JSONObject result) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (result == null) return results;

        try {
            List<List<String>> idsOuter = (List<List<String>>) result.get("ids");
            List<List<Double>> distancesOuter = (List<List<Double>>) result.get("distances");
            List<List<Map<String, Object>>> metadatasOuter = (List<List<Map<String, Object>>>) result.get("metadatas");
            List<List<String>> documentsOuter = (List<List<String>>) result.get("documents");

            if (idsOuter == null || idsOuter.isEmpty()) return results;

            List<String> ids = idsOuter.get(0);
            List<Double> distances = distancesOuter != null && !distancesOuter.isEmpty() ? distancesOuter.get(0) : new ArrayList<>();
            List<Map<String, Object>> metadatas = metadatasOuter != null && !metadatasOuter.isEmpty() ? metadatasOuter.get(0) : new ArrayList<>();
            List<String> documents = documentsOuter != null && !documentsOuter.isEmpty() ? documentsOuter.get(0) : new ArrayList<>();

            for (int i = 0; i < ids.size(); i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("templateId", ids.get(i));
                item.put("rank", i + 1);

                double distance = i < distances.size() ? distances.get(i) : 1.0;
                double similarity = 1.0 - distance;
                item.put("score", String.format("%.4f", similarity));
                item.put("distance", String.format("%.4f", distance));

                if (i < documents.size() && documents.get(i) != null) {
                    String doc = documents.get(i);
                    item.put("preview", doc.length() > 200 ? doc.substring(0, 200) + "..." : doc);
                }

                if (i < metadatas.size() && metadatas.get(i) != null) {
                    Map<String, Object> metadata = metadatas.get(i);
                    item.put("templateName", metadata.getOrDefault("templateName", ""));
                    item.put("templateType", metadata.getOrDefault("templateType", ""));
                    item.put("category", metadata.getOrDefault("category", ""));
                    item.put("tags", metadata.getOrDefault("tags", ""));
                    item.put("status", metadata.getOrDefault("status", ""));
                    item.put("metadata", metadata);
                }

                results.add(item);
            }

        } catch (Exception e) {
            log.error("解析模板搜索结果失败", e);
        }

        return results;
    }
}