package com.washy.dify.rag.service.impl;

import com.washy.dify.rag.client.OllamaEmbeddingClient;
import com.washy.dify.rag.service.EmbeddingService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class OllamaEmbeddingServiceImpl implements EmbeddingService {

    @Resource
    private OllamaEmbeddingClient embeddingClient;

    @Override
    public List<Float> getEmbedding(String text) {
        return embeddingClient.getEmbedding(text);
    }

}
