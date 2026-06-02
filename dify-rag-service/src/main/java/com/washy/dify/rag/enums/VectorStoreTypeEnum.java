package com.washy.dify.rag.enums;

/**
 * 向量库类型枚举
 * @author washydify
 */
public enum VectorStoreTypeEnum {

    /**
     * Chroma向量库
     */
    CHROMA,

    /**
     * Milvus向量库
     */
    MILVUS,

    /**
     * ElasticSearch向量库
     */
    ES,

    /**
     * Pinecone向量库
     */
    PINECONE;

    public static VectorStoreTypeEnum getByCode(String code) {
        for (VectorStoreTypeEnum type : values()) {
            if (type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return CHROMA;
    }

}