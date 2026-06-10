package com.washy.dify.provider.client.embedding;

import java.util.List;

/**
 * 向量嵌入客户端接口
 */
public interface EmbeddingClient {
    
    /**
     * 单条文本向量化
     */
    float[] embed(String text);
    
    /**
     * 批量文本向量化
     */
    List<float[]> embedBatch(List<String> texts);
    
    /**
     * 获取向量维度
     */
    int getDimension();
    
    /**
     * 测试连接
     */
    boolean testConnection();
}