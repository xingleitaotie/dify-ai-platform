package com.washy.dify.rag.enums;

/**
 * 向量模型类型枚举
 * @author washydify
 */
public enum EmbeddingTypeEnum {

    /**
     * 本地Ollama向量模型
     */
    OLLAMA,

    /**
     * 百度文心向量
     */
    WENXIN,

    /**
     * 阿里云向量模型
     */
    ALIYUN,

    /**
     * 智谱AI向量模型
     */
    ZHIPU,

    /**
     * OpenAI Embedding
     */
    OPENAI;

    public static EmbeddingTypeEnum getByCode(String code) {
        for (EmbeddingTypeEnum type : values()) {
            if (type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return OLLAMA;
    }

}