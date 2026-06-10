package com.washy.dify.provider.client.rerank.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.provider.client.rerank.RerankClient;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.exception.ModelProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
public class CohereRerankClient implements RerankClient {
    
    private static final String RERANK_API_PATH = "/rerank";
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ProviderEntity provider;
    private final ModelConfigEntity modelConfig;
    
    public CohereRerankClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
        this.provider = provider;
        this.modelConfig = modelConfig;
    }
    
    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topK) {
        try {
            String url = provider.getBaseUrl() + RERANK_API_PATH;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (provider.getApiKey() != null && !provider.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + provider.getApiKey());
            }
            
            Map<String, Object> request = new HashMap<>();
            request.put("model", modelConfig.getModelKey());
            request.put("query", query);
            request.put("documents", documents);
            request.put("top_n", topK);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            JSONObject response = restTemplate.postForObject(url, entity, JSONObject.class);
            return parseResponse(response, documents);
            
        } catch (Exception e) {
            log.error("Rerank调用失败", e);
            throw new ModelProviderException("Rerank调用失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean testConnection() {
        try {
            List<String> documents = Arrays.asList("测试文档1", "测试文档2");
            List<RerankResult> results = rerank("测试查询", documents, 2);
            return results != null && !results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    private List<RerankResult> parseResponse(JSONObject response, List<String> documents) {
        if (response == null || !response.containsKey("results")) {
            return new ArrayList<>();
        }
        
        JSONArray resultsArray = response.getJSONArray("results");
        List<RerankResult> results = new ArrayList<>();
        
        for (int i = 0; i < resultsArray.size(); i++) {
            JSONObject item = resultsArray.getJSONObject(i);
            int index = item.getIntValue("index");
            double score = item.getDoubleValue("relevance_score");
            String document = index < documents.size() ? documents.get(index) : "";
            results.add(new RerankResult(index, document, score));
        }
        
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results;
    }
}