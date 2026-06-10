package com.washy.dify.provider.factory;

import com.washy.dify.provider.client.chat.ChatClient;
import com.washy.dify.provider.client.chat.impl.AliyunChatClient;
import com.washy.dify.provider.client.chat.impl.ModelScopeChatClient;
import com.washy.dify.provider.client.embedding.EmbeddingClient;
import com.washy.dify.provider.client.embedding.impl.OpenAiEmbeddingClient;
import com.washy.dify.provider.client.rerank.RerankClient;
import com.washy.dify.provider.client.rerank.impl.CohereRerankClient;
import com.washy.dify.provider.config.LlmThreadPoolConfig;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 统一客户端工厂 - 根据能力类型返回对应客户端
 */
@Slf4j
@Component
public class UnifiedClientFactory {
    
    // Chat客户端缓存
    private final Map<Long, ChatClient> chatClientCache = new ConcurrentHashMap<>();
    
    // Embedding客户端缓存
    private final Map<Long, EmbeddingClient> embeddingClientCache = new ConcurrentHashMap<>();
    
    // Rerank客户端缓存
    private final Map<Long, RerankClient> rerankClientCache = new ConcurrentHashMap<>();

    @Resource
    private LlmThreadPoolConfig threadPoolConfig;
    
    /**
     * 获取Chat客户端
     */
    public ChatClient getChatClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
        Long key = modelConfig.getId();
        return chatClientCache.computeIfAbsent(key, k -> createChatClient(provider, modelConfig));
    }
    
    /**
     * 获取Embedding客户端
     */
    public EmbeddingClient getEmbeddingClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
        Long key = modelConfig.getId();
        return embeddingClientCache.computeIfAbsent(key, k -> createEmbeddingClient(provider, modelConfig));
    }
    
    /**
     * 获取Rerank客户端
     */
    public RerankClient getRerankClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
        Long key = modelConfig.getId();
        return rerankClientCache.computeIfAbsent(key, k -> createRerankClient(provider, modelConfig));
    }
    
    /**
     * 创建Chat客户端
     */
    public ChatClient createChatClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
        String schema = modelConfig.getModelSchema() != null ? modelConfig.getModelSchema() : "openai";
        String providerKey = provider.getProviderKey();
        
        log.info("创建Chat客户端: provider={}, schema={}, model={}", providerKey, schema, modelConfig.getModelKey());
        
        switch (schema.toLowerCase()) {
            case "modelscope":
                return new ModelScopeChatClient(provider, modelConfig);
            case "dashscope":
            case "aliyun":
                return new AliyunChatClient(provider, modelConfig);
            default:
                return new ModelScopeChatClient(provider, modelConfig);
        }
    }
    
    /**
     * 创建Embedding客户端
     */
    public EmbeddingClient createEmbeddingClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
        String schema = modelConfig.getModelSchema() != null ? modelConfig.getModelSchema() : "openai";
        log.info("创建Embedding客户端: provider={}, schema={}", provider.getProviderKey(), schema);
        
        switch (schema.toLowerCase()) {
            case "openai":
                return new OpenAiEmbeddingClient(provider, modelConfig);
            default:
                return new OpenAiEmbeddingClient(provider, modelConfig);
        }
    }
    
    /**
     * 创建Rerank客户端
     */
    public RerankClient createRerankClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
        String schema = modelConfig.getModelSchema() != null ? modelConfig.getModelSchema() : "cohere";
        log.info("创建Rerank客户端: provider={}, schema={}", provider.getProviderKey(), schema);
        
        switch (schema.toLowerCase()) {
            case "cohere":
                return new CohereRerankClient(provider, modelConfig);
            default:
                return new CohereRerankClient(provider, modelConfig);
        }
    }

    /**
     * 获取线程池
     */
    public ExecutorService getThreadPoolExecutor() {
        return threadPoolConfig.llmThreadPoolExecutor();
    }
    
    /**
     * 清除缓存
     */
    public void clearCache(Long modelConfigId) {
        chatClientCache.remove(modelConfigId);
        embeddingClientCache.remove(modelConfigId);
        rerankClientCache.remove(modelConfigId);
    }
}