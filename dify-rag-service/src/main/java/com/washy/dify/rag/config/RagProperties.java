package com.washy.dify.rag.config;

import com.washy.dify.rag.enums.EmbeddingTypeEnum;
import com.washy.dify.rag.enums.VectorStoreTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG配置属性
 * @author washy
 */
@Data
@ConfigurationProperties(prefix = "dify.rag")
public class RagProperties {

    /**
     * 向量模型类型
     */
    private EmbeddingTypeEnum embeddingType = EmbeddingTypeEnum.OLLAMA;

    /**
     * 向量库类型
     */
    private VectorStoreTypeEnum vectorStoreType = VectorStoreTypeEnum.CHROMA;

    /**
     * 向量模型配置
     */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /**
     * 向量库配置
     */
    private VectorStoreConfig vectorStore = new VectorStoreConfig();

    @Data
    public static class EmbeddingConfig {
        // Ollama配置
        private String ollamaBaseUrl;
        private String ollamaModel;
        private Integer ollamaTimeout;

        // 文心一言配置
        private String wenxinApiKey;
        private String wenxinSecretKey;

        // 阿里云配置
        private String aliyunApiKey;
        private String aliyunModel;

        // OpenAI配置
        private String openaiApiKey;
        private String openaiBaseUrl = "https://api.openai.com";
    }

    @Data
    public static class VectorStoreConfig {
        // Chroma配置
        private String chromaHost;
        private Integer chromaPort;
        private String chromaTenant;
        private String chromaDatabase;
        private String chromaCollection;

        // Milvus配置
        private String milvusHost;
        private Integer milvusPort;
        private String milvusUsername;
        private String milvusPassword;

        // ES配置
        private String esHosts;
        private String esUsername;
        private String esPassword;
    }
}