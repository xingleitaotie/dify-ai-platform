// service/ModelTestService.java
package com.washy.dify.provider.service;

import com.washy.dify.provider.client.chat.ChatClient;
import com.washy.dify.provider.client.embedding.EmbeddingClient;
import com.washy.dify.provider.client.rerank.RerankClient;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.factory.UnifiedClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelTestService {
    
    private final UnifiedClientFactory clientFactory;
    
    /**
     * 测试Chat模型
     */
    public boolean testChat(ProviderEntity provider, ModelConfigEntity modelConfig) {
        ChatClient client = clientFactory.getChatClient(provider, modelConfig);
        return client.testConnection();
    }
    
    /**
     * 测试Embedding模型
     */
    public boolean testEmbedding(ProviderEntity provider, ModelConfigEntity modelConfig) {
        EmbeddingClient client = clientFactory.getEmbeddingClient(provider, modelConfig);
        return client.testConnection();
    }
    
    /**
     * 测试Rerank模型
     */
    public boolean testRerank(ProviderEntity provider, ModelConfigEntity modelConfig) {
        RerankClient client = clientFactory.getRerankClient(provider, modelConfig);
        return client.testConnection();
    }
}