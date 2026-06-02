package com.washy.dify.rag.factory;

import com.washy.dify.rag.config.RagProperties;
import com.washy.dify.rag.enums.EmbeddingTypeEnum;
import com.washy.dify.rag.service.EmbeddingService;
import com.washy.dify.rag.service.impl.OllamaEmbeddingServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 向量模型工厂
 * @author washydify
 */
@Component
@RequiredArgsConstructor
public class EmbeddingFactory {

    private final RagProperties ragProperties;
    private final OllamaEmbeddingServiceImpl embeddingService;

    public EmbeddingService getEmbeddingService() {
        EmbeddingTypeEnum type = ragProperties.getEmbeddingType();
        switch (type) {
            case OLLAMA:
                return embeddingService;
            // 扩展其他模型只需新增case和实现类
            default:
                return embeddingService;
        }
    }
}