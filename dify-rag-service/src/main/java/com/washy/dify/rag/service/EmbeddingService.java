package com.washy.dify.rag.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface EmbeddingService {

    /**
     * 使用 nomic-embed-text 模型生成单个 embedding
     * 使用专用的 /api/embeddings 接口
     */
    List<Float> getEmbedding(String text);

}
