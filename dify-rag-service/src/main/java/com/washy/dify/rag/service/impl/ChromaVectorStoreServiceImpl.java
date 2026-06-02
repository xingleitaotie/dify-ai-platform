package com.washy.dify.rag.service.impl;

import com.alibaba.fastjson2.JSONObject;
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

import javax.annotation.PostConstruct;
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

    // 默认知识库名称
    private static final String DEFAULT_KB_NAME = "dify_knowledge_base";

    @PostConstruct
    public void init() {
        if (embeddingClient != null) {
            log.info("Embedding 模型维度: {}", embeddingClient.getEmbeddingDimension());
        }
    }

    // ==================== 知识库管理 ====================

    @Override
    public List<Map<String, Object>> listKnowledgeBases() {
        List<Map<String, Object>> collections = chromaApiClient.listAllCollections();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> collection : collections) {
            String name = (String) collection.get("name");
            Map<String, Object> kb = new HashMap<>();
            kb.put("id", collection.get("id"));
            kb.put("name", name);
            kb.put("description", collection.getOrDefault("description", ""));
            kb.put("documents", chromaApiClient.listDocuments(name) == null ? 0 : chromaApiClient.listDocuments(name).size());
            kb.put("chunkCount", chromaApiClient.getCollectionCount(name));
            result.add(kb);
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

        String collectionName = kbName != null ? kbName : DEFAULT_KB_NAME;
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
                chromaApiClient.addEmbeddings(collectionName, ids, embeddings, texts, metadatas);
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
        String collectionName = kbName != null ? kbName : DEFAULT_KB_NAME;
        return chromaApiClient.listAllVectors(collectionName);
    }

    @Override
    public List<Map<String, Object>> search(String kbName, String query, int topK) {
        String collectionName = kbName != null ? kbName : DEFAULT_KB_NAME;

        try {
            List<Float> queryEmbedding = embeddingClient.getEmbedding(query);
            JSONObject result = chromaApiClient.queryEmbedding(collectionName, queryEmbedding, topK);
            return parseSearchResult(result);
        } catch (Exception e) {
            log.error("搜索失败: kbName={}, query={}", kbName, query, e);
            return new ArrayList<>();
        }
    }

    // ==================== 兼容旧接口 ====================

    @Override
    public int storeDocumentChunks(List<DocumentChunk> chunks, String documentId, String documentName) {
        return storeDocumentChunks(DEFAULT_KB_NAME, chunks, documentId, documentName);
    }

    @Override
    public List<Map<String, Object>> listAllChunks() {
        return listAllChunks(DEFAULT_KB_NAME);
    }

    @Override
    public List<Map<String, Object>> search(String query, int topK) {
        return search(DEFAULT_KB_NAME, query, topK);
    }

    @Override
    public void clearCollection() {
        chromaApiClient.clearCollection();
    }

    @Override
    public void deleteDocumentChunks(String documentId) {
        deleteDocumentChunks(DEFAULT_KB_NAME, documentId);
    }

    /**
     * 删除指定知识库中对应文档的所有块
     */
    @Override
    public void deleteDocumentChunks(String collectionName, String documentId){
        log.info("删除集合：{}中的文档块: documentId={}",collectionName, documentId);
        String kbName = collectionName != null ? collectionName : DEFAULT_KB_NAME;
        chromaApiClient.deleteDocumentChunks(kbName, documentId);
    }

    @Override
    public Map<String, Object> getChunkDetail(String chunkId) {
        return chromaApiClient.getChunkDetail(chunkId);
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
        String collectionName = kbId != null ? kbId : DEFAULT_KB_NAME;
        return chromaApiClient.listDocuments(collectionName);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        return chromaApiClient.getDefaultKbConfig();
    }

    @Override
    public void updateConfig(String kbId, Map<String, Object> config) {
        String collectionName = kbId != null ? kbId : DEFAULT_KB_NAME;
        chromaApiClient.updateKbConfig(collectionName, config);
    }

    @Override
    public Map<String, Object> getConfig(String kbId) {
        String collectionName = kbId != null ? kbId : DEFAULT_KB_NAME;
        Map<String, Object> config = new HashMap<>();
        config.put("name", collectionName);
        config.put("chunkCount", chromaApiClient.getCollectionCount(collectionName));
        config.put("embeddingModel", embeddingClient != null ? ragProperties.getEmbedding().getOllamaModel() : "nomic-embed-text");
        config.put("chunkSize", 500);
        config.put("chunkOverlap", 50);
        config.put("description", "Chroma 向量知识库");
        return config;
    }
}