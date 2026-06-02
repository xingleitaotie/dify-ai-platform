package com.washy.dify.rag.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Ollama 向量模型客户端
 */
@Component
@Slf4j
public class OllamaEmbeddingClient {

    @Resource
    private RagProperties ragProperties;

    private OkHttpClient client;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(ragProperties.getEmbedding().getOllamaTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(ragProperties.getEmbedding().getOllamaTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(ragProperties.getEmbedding().getOllamaTimeout(), TimeUnit.MILLISECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 使用 nomic-embed-text 模型生成单个 embedding（带重试）
     */
    public List<Float> getEmbedding(String text) {
        // 文本截断，避免过长
        if (text.length() > 8000) {
            text = text.substring(0, 8000);
            log.debug("文本过长，已截断至 8000 字符");
        }

        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", ragProperties.getEmbedding().getOllamaModel());
                requestBody.put("prompt", text);

                String json = objectMapper.writeValueAsString(requestBody);

                Request request = new Request.Builder()
                        .url(ragProperties.getEmbedding().getOllamaBaseUrl() + "/api/embeddings")
                        .post(RequestBody.create(json, MediaType.parse("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        lastException = new RuntimeException("HTTP " + response.code());
                        log.warn("Embedding 请求失败 (尝试 {}/{}): {}", attempt, maxRetries, response.code());
                        if (attempt < maxRetries) {
                            Thread.sleep(1000 * attempt);
                            continue;
                        }
                        throw lastException;
                    }

                    String body = response.body().string();
                    JsonNode node = objectMapper.readTree(body);
                    JsonNode embeddingArray = node.get("embedding");

                    if (embeddingArray == null || !embeddingArray.isArray()) {
                        throw new RuntimeException("返回的 embedding 为空");
                    }

                    List<Float> embedding = new ArrayList<>();
                    for (JsonNode value : embeddingArray) {
                        embedding.add(value.floatValue());
                    }

                    if (attempt > 1) {
                        log.info("Embedding 生成成功 (重试 {} 次后成功)", attempt - 1);
                    }
                    return embedding;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Embedding 生成失败 (尝试 {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("线程被中断", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Embedding 生成失败，已重试 " + maxRetries + " 次", lastException);
    }

    /**
     * 获取 embedding 维度
     */
    public int getEmbeddingDimension() {
        List<Float> testEmbedding = getEmbedding("test");
        return testEmbedding.size();
    }

}