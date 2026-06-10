// client/embedding/impl/OpenAiEmbeddingClient.java
package com.washy.dify.provider.client.embedding.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.provider.client.embedding.EmbeddingClient;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.exception.ModelProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiEmbeddingClient implements EmbeddingClient {
    
    private static final String EMBEDDING_API_PATH = "/embeddings";
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ProviderEntity provider;
    private final ModelConfigEntity modelConfig;
    
    public OpenAiEmbeddingClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
        this.provider = provider;
        this.modelConfig = modelConfig;
    }
    
    @Override
    public float[] embed(String text) {
        try {
            String url = provider.getBaseUrl() + EMBEDDING_API_PATH;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (provider.getApiKey() != null && !provider.getApiKey().isEmpty()) {
                headers.setBearerAuth(provider.getApiKey());
            }
            
            Map<String, Object> request = new HashMap<>();
            request.put("model", modelConfig.getModelKey());
            request.put("input", text);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            JSONObject response = restTemplate.postForObject(url, entity, JSONObject.class);
            return parseResponse(response);
            
        } catch (Exception e) {
            log.error("Embedding调用失败", e);
            throw new ModelProviderException("Embedding调用失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }
    
    @Override
    public int getDimension() {
        return modelConfig.getDimension() != null ? modelConfig.getDimension() : 1536;
    }
    
    @Override
    public boolean testConnection() {
        try {
            float[] embedding = embed("测试文本");
            return embedding != null && embedding.length > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private float[] parseResponse(JSONObject response) {
        if (response == null || !response.containsKey("data")) {
            throw new ModelProviderException("Embedding响应格式异常");
        }
        JSONArray data = response.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new ModelProviderException("Embedding返回数据为空");
        }
        JSONArray embedding = data.getJSONObject(0).getJSONArray("embedding");
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.getFloatValue(i);
        }
        return result;
    }
}