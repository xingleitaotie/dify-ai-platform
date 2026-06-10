package com.washy.dify.rag.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.common.exception.GlobalExceptionHandler;
import com.washy.dify.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChromaApiClient {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private RagProperties ragProperties;

    private String chromaBaseUrl;
    private String tenant;
    private String database;

    @PostConstruct
    public void init() {
        chromaBaseUrl = String.format("http://%s:%s/api/v2",
                ragProperties.getVectorStore().getChromaHost(),
                ragProperties.getVectorStore().getChromaPort());
        tenant = ragProperties.getVectorStore().getChromaTenant();
        database = ragProperties.getVectorStore().getChromaDatabase();

        log.info("Chroma 客户端初始化完成");
    }


    // 缓存集合ID
    private final Map<String, String> collectionIdCache = new ConcurrentHashMap<>();

    /**
     * 获取或创建集合
     */
    public String getOrCreateCollection(String collectionName) {
        if (collectionIdCache.containsKey(collectionName)) {
            return collectionIdCache.get(collectionName);
        }

        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            collectionId = createCollection(collectionName);
        }

        collectionIdCache.put(collectionName, collectionId);
        return collectionId;
    }

    /**
     * 获取所有集合列表
     */
    public List<Map<String, Object>> listAllCollections() {
        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections",
                    chromaBaseUrl, tenant, database);

            String response = restTemplate.getForObject(url, String.class);
            List<JSONObject> collections = JSON.parseArray(response, JSONObject.class);

            List<Map<String, Object>> result = new ArrayList<>();
            for (JSONObject coll : collections) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", coll.getString("id"));
                map.put("name", coll.getString("name"));

                JSONObject metadata = coll.getJSONObject("metadata");
                if (metadata != null) {
                    map.put("description", metadata.getString("description"));
                }
                result.add(map);
            }
            return result;
        } catch (Exception e) {
            log.error("获取集合列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据名称获取集合ID
     */
    public String getCollectionIdByName(String collectionName) {
        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections",
                    chromaBaseUrl, tenant, database);

            String response = restTemplate.getForObject(url, String.class);
            List<JSONObject> list = JSON.parseArray(response, JSONObject.class);

            for (JSONObject item : list) {
                if (collectionName.equals(item.getString("name"))) {
                    return item.getString("id");
                }
            }
            return null;
        } catch (Exception e) {
            log.error("获取集合ID失败: {}", collectionName, e);
            return null;
        }
    }

    /**
     * 创建集合
     */
    public String createCollection(String collectionName) {
        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections",
                    chromaBaseUrl, tenant, database);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("name", collectionName);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("dimension", 768);
            metadata.put("hnsw:space", "cosine");
            body.put("metadata", metadata);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<JSONObject> resp = restTemplate.exchange(url, HttpMethod.POST, request, JSONObject.class);

            String collectionId = resp.getBody().getString("id");
            log.info("创建集合成功: {}, id: {}", collectionName, collectionId);
            return collectionId;
        } catch (Exception e) {
            log.error("创建集合失败: {}", collectionName, e);
            throw new GlobalExceptionHandler("创建集合失败: " + e.getMessage());
        }
    }

    /**
     * 删除集合
     */
    public void deleteCollection(String collectionName) {
        try {
            String collectionId = getCollectionIdByName(collectionName);
            if (collectionId == null) {
                log.warn("集合不存在: {}", collectionName);
                return;
            }

            String url = String.format("%s/tenants/%s/databases/%s/collections/%s",
                    chromaBaseUrl, tenant, database, collectionName);

            restTemplate.delete(url);
            collectionIdCache.remove(collectionName);
            log.info("删除集合成功: {}", collectionName);
        } catch (Exception e) {
            log.error("删除集合失败: {}", collectionName, e);
            throw new GlobalExceptionHandler("删除集合失败: " + e.getMessage());
        }
    }

    /**
     * 插入向量到指定集合
     */
    public void addEmbeddings(String collectionName, List<String> ids,
                              List<List<Float>> embeddings,
                              List<String> documents,
                              List<Map<String, Object>> metadatas) {
        String collectionId = getOrCreateCollection(collectionName);

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/add",
                    chromaBaseUrl, tenant, database, collectionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("ids", ids);
            body.put("embeddings", embeddings);
            body.put("documents", documents);
            if (metadatas != null && !metadatas.isEmpty()) {
                body.put("metadatas", metadatas);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, request, String.class);
            log.info("向量入库成功，集合: {}, 数量: {}", collectionName, ids.size());
        } catch (Exception e) {
            log.error("向量入库失败", e);
            throw new GlobalExceptionHandler("向量入库失败: " + e.getMessage());
        }
    }

    /**
     * 查询指定集合的所有分块
     */
    public List<Map<String, Object>> listAllVectors(String collectionName) {
        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            return new ArrayList<>();
        }

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/get",
                    chromaBaseUrl, tenant, database, collectionId);

            JSONObject body = new JSONObject();
            body.put("include", Arrays.asList("documents", "embeddings", "metadatas"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<JSONObject> request = new HttpEntity<>(body, headers);

            ResponseEntity<JSONObject> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, JSONObject.class);
            JSONObject result = response.getBody();

            List<String> ids = (List<String>) result.get("ids");
            List<String> documents = (List<String>) result.get("documents");
            List<List<Float>> embeddings = (List<List<Float>>) result.get("embeddings");

            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", ids.get(i));
                map.put("text", documents.get(i));
                map.put("embedding", embeddings.get(i));
                list.add(map);
            }
            log.info("查询集合 {} 分块，共 {} 条", collectionName, list.size());
            return list;
        } catch (Exception e) {
            log.error("查询分块失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据ID查询单条向量详情（文本+完整向量）
     */
    public Map<String, Object> getChunkDetail(String collectionName, String chunkId) {
        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            log.warn("集合不存在: {}", collectionName);
            return new HashMap<>();
        }

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/get",
                    chromaBaseUrl, tenant, database, collectionId);

            JSONObject body = new JSONObject();
            body.put("ids", Collections.singletonList(chunkId));
            body.put("include", Arrays.asList("documents", "embeddings", "metadatas"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<JSONObject> request = new HttpEntity<>(body, headers);

            ResponseEntity<JSONObject> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, JSONObject.class);
            JSONObject result = response.getBody();

            List<String> ids = (List<String>) result.get("ids");
            List<String> documents = (List<String>) result.get("documents");
            List<List<Float>> embeddings = (List<List<Float>>) result.get("embeddings");

            Map<String, Object> map = new HashMap<>();
            if (!ids.isEmpty()) {
                map.put("id", ids.get(0));
                map.put("text", documents.get(0));
                map.put("embedding", embeddings.get(0));

                // 添加元数据
                List<Map<String, Object>> metadatas = (List<Map<String, Object>>) result.get("metadatas");
                if (metadatas != null && !metadatas.isEmpty()) {
                    map.put("metadata", metadatas.get(0));
                }
            }
            log.info("查询分块详情: collection={}, chunkId={}", collectionName, chunkId);
            return map;
        } catch (Exception e) {
            log.error("查询分块详情异常: chunkId={}", chunkId, e);
            throw new GlobalExceptionHandler("查询分块详情失败: " + e.getMessage());
        }
    }

    /**
     * 在指定集合中检索
     */
    public JSONObject queryEmbedding(String collectionName, List<Float> queryVector, int topN) {
        String collectionId = getOrCreateCollection(collectionName);

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/query",
                    chromaBaseUrl, tenant, database, collectionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("query_embeddings", Collections.singletonList(queryVector));
            body.put("n_results", topN);
            body.put("include", Arrays.asList("documents", "metadatas", "distances"));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<JSONObject> resp = restTemplate.exchange(url, HttpMethod.POST, request, JSONObject.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("向量检索失败", e);
            throw new GlobalExceptionHandler("向量检索失败: " + e.getMessage());
        }
    }

    /**
     * 获取集合中的文档数量
     */
    public int getCollectionCount(String collectionName) {
        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            return 0;
        }

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/get",
                    chromaBaseUrl, tenant, database, collectionId);

            // 不指定 include，让它默认只返回 ids（默认行为）
            JSONObject body = new JSONObject();
            // 限制只获取 1 条来获取总数？不，这样不对
            // 正确做法：不设置 limit，但只取 ids 数组的长度

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<JSONObject> request = new HttpEntity<>(body, headers);

            ResponseEntity<JSONObject> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, JSONObject.class);
            JSONObject result = response.getBody();

            List<String> ids = (List<String>) result.get("ids");
            return ids != null ? ids.size() : 0;
        } catch (Exception e) {
            log.error("获取集合中的文档数量失败: {}", collectionName, e);
            return 0;
        }
    }

    /**
     * 获取指定集合的所有文档（去重）
     */
    public List<Map<String, Object>> listDocuments(String collectionName) {
        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            log.warn("集合不存在: {}", collectionName);
            return new ArrayList<>();
        }

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/get",
                    chromaBaseUrl, tenant, database, collectionId);

            JSONObject body = new JSONObject();
            // 只需要 metadatas，不需要 documents，减少数据传输
            body.put("include", Collections.singletonList("metadatas"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<JSONObject> request = new HttpEntity<>(body, headers);

            ResponseEntity<JSONObject> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, JSONObject.class);
            JSONObject result = response.getBody();

            if (result == null || !result.containsKey("metadatas")) {
                log.warn("获取文档元数据失败: {}", collectionName);
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metadatas = (List<Map<String, Object>>) result.get("metadatas");

            if (metadatas == null || metadatas.isEmpty()) {
                return new ArrayList<>();
            }

            // 按文档名去重，同时统计每个文档的分块数量
            Map<String, Map<String, Object>> docMap = new LinkedHashMap<>();

            for (Map<String, Object> meta : metadatas) {
                if (meta == null) continue;

                // 获取文档标识（优先使用 documentId，其次使用 documentName）
                String documentId = (String) meta.get("documentId");
                String documentName = (String) meta.get("documentName");

                // 使用 documentId 作为唯一标识，如果没有则用 documentName
                String docKey = documentId != null ? documentId : documentName;
                if (docKey == null) {
                    docKey = "未知文档_" + System.currentTimeMillis();
                }

                Map<String, Object> doc = docMap.get(docKey);
                if (doc == null) {
                    doc = new HashMap<>();
                    doc.put("id", docKey.hashCode());
                    doc.put("documentId", documentId != null ? documentId : "");
                    doc.put("name", documentName != null ? documentName : "未知文档");
                    doc.put("chunkCount", 1);
                    docMap.put(docKey, doc);
                } else {
                    Integer count = (Integer) doc.get("chunkCount");
                    doc.put("chunkCount", (count != null ? count : 0) + 1);
                }
            }

            List<Map<String, Object>> resultList = new ArrayList<>(docMap.values());
            log.debug("获取文档列表成功，集合: {}, 文档数: {}", collectionName, resultList.size());
            return resultList;

        } catch (Exception e) {
            log.error("获取文档列表失败，集合: {}", collectionName, e);
            return new ArrayList<>();
        }
    }

    /**
     * 更新知识库配置
     */
    public void updateKbConfig(String collectionName, Map<String, Object> config) {
        // Chroma 集合配置更新（如果需要）
        // 目前 Chroma API 支持更新 metadata
        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            throw new RuntimeException("知识库不存在: " + collectionName);
        }

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s",
                    chromaBaseUrl, tenant, database, collectionId);

            Map<String, Object> metadata = new HashMap<>();
            if (config.containsKey("description")) {
                metadata.put("description", config.get("description"));
            }
            if (config.containsKey("chunkSize")) {
                metadata.put("chunkSize", config.get("chunkSize"));
            }
            if (config.containsKey("chunkOverlap")) {
                metadata.put("chunkOverlap", config.get("chunkOverlap"));
            }

            Map<String, Object> body = new HashMap<>();
            body.put("metadata", metadata);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
            log.info("更新知识库配置成功: {}", collectionName);
        } catch (Exception e) {
            log.error("更新知识库配置失败", e);
            throw new RuntimeException("更新知识库配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据文档ID删除该文档的所有块
     * @param collectionName 集合名称
     * @param documentId 文档ID
     */
    public void deleteDocumentChunks(String collectionName, String documentId) {
        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            log.warn("集合不存在: {}", collectionName);
            return;
        }

        try {
            // 1. 先查询该文档的所有块ID
            List<String> chunkIds = getChunkIdsByDocumentId(collectionName, documentId);

            if (chunkIds == null || chunkIds.isEmpty()) {
                log.info("没有找到文档 {} 的块", documentId);
                return;
            }

            // 2. 批量删除
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/delete",
                    chromaBaseUrl, tenant, database, collectionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("ids", chunkIds);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            log.info("删除文档块成功: collection={}, documentId={}, 删除 {} 个块",
                    collectionName, documentId, chunkIds.size());

        } catch (Exception e) {
            log.error("删除文档块失败: collection={}, documentId={}", collectionName, documentId, e);
            throw new RuntimeException("删除文档块失败: " + e.getMessage());
        }
    }

    /**
     * 获取文档的所有块ID
     */
    private List<String> getChunkIdsByDocumentId(String collectionName, String documentId) {
        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            return new ArrayList<>();
        }

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/get",
                    chromaBaseUrl, tenant, database, collectionId);

            JSONObject body = new JSONObject();
            body.put("include", Collections.singletonList("metadatas"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<JSONObject> request = new HttpEntity<>(body, headers);

            ResponseEntity<JSONObject> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, JSONObject.class);
            JSONObject result = response.getBody();

            if (result == null || !result.containsKey("ids")) {
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            List<String> allIds = (List<String>) result.get("ids");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metadatas = (List<Map<String, Object>>) result.get("metadatas");

            if (allIds == null || metadatas == null || allIds.size() != metadatas.size()) {
                return new ArrayList<>();
            }

            // 过滤出属于该文档的块ID
            List<String> chunkIds = new ArrayList<>();
            for (int i = 0; i < metadatas.size(); i++) {
                Map<String, Object> meta = metadatas.get(i);
                if (meta != null && documentId.equals(meta.get("documentId"))) {
                    chunkIds.add(allIds.get(i));
                }
            }

            return chunkIds;

        } catch (Exception e) {
            log.error("获取文档块ID失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 删除指定ID的向量（支持批量）
     * @param collectionName 集合名称
     * @param ids 要删除的向量ID列表
     */
    public void deleteVectors(String collectionName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            log.warn("删除向量时ID列表为空");
            return;
        }

        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            log.warn("集合不存在: {}", collectionName);
            return;
        }

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/delete",
                    chromaBaseUrl, tenant, database, collectionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("ids", ids);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            log.info("删除向量成功: collection={}, 数量={}", collectionName, ids.size());

        } catch (Exception e) {
            log.error("删除向量失败: collection={}, ids={}", collectionName, ids, e);
            throw new RuntimeException("删除向量失败: " + e.getMessage());
        }
    }

    /**
     * 按条件删除向量（通过metadata过滤）
     * @param collectionName 集合名称
     * @param where 过滤条件，如 {"templateId": "xxx"}
     */
    public void deleteVectorsByWhere(String collectionName, Map<String, Object> where) {
        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            log.warn("集合不存在: {}", collectionName);
            return;
        }

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/delete",
                    chromaBaseUrl, tenant, database, collectionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("where", where);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            log.info("按条件删除向量成功: collection={}, where={}", collectionName, where);

        } catch (Exception e) {
            log.error("按条件删除向量失败: collection={}, where={}", collectionName, where, e);
            throw new RuntimeException("按条件删除向量失败: " + e.getMessage());
        }
    }

    /**
     * 更新向量（按ID更新文档和元数据）
     * @param collectionName 集合名称
     * @param ids 要更新的向量ID列表
     * @param documents 新的文档内容
     * @param metadatas 新的元数据
     */
    public void updateVectors(String collectionName, List<String> ids,
                              List<String> documents, List<Map<String, Object>> metadatas) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            log.warn("集合不存在: {}", collectionName);
            return;
        }

        try {
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/update",
                    chromaBaseUrl, tenant, database, collectionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("ids", ids);
            if (documents != null && !documents.isEmpty()) {
                body.put("documents", documents);
            }
            if (metadatas != null && !metadatas.isEmpty()) {
                body.put("metadatas", metadatas);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            log.info("更新向量成功: collection={}, 数量={}", collectionName, ids.size());

        } catch (Exception e) {
            log.error("更新向量失败: collection={}", collectionName, e);
            throw new RuntimeException("更新向量失败: " + e.getMessage());
        }
    }

    /**
     * 获取集合数量（优化版，使用count API）
     */
    public int getCollectionCountOptimized(String collectionName) {
        String collectionId = getCollectionIdByName(collectionName);
        if (collectionId == null) {
            return 0;
        }

        try {
            // ChromaDB 有专门的 count API
            String url = String.format("%s/tenants/%s/databases/%s/collections/%s/count",
                    chromaBaseUrl, tenant, database, collectionId);

            String response = restTemplate.getForObject(url, String.class);
            JSONObject result = JSON.parseObject(response);
            return result.getInteger("count");

        } catch (Exception e) {
            log.error("获取集合数量失败: {}", collectionName, e);
            return 0;
        }
    }
}