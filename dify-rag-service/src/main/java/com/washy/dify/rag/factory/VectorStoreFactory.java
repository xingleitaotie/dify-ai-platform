package com.washy.dify.rag.factory;

import com.washy.dify.rag.config.RagProperties;
import com.washy.dify.rag.enums.VectorStoreTypeEnum;
import com.washy.dify.rag.service.VectorStoreService;
import com.washy.dify.rag.service.impl.ChromaVectorStoreServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 向量库工厂
 * @author washydify
 */
@Component
@RequiredArgsConstructor
public class VectorStoreFactory {

    private final RagProperties ragProperties;
    private final ChromaVectorStoreServiceImpl chromaVectorStoreService;

    public VectorStoreService getVectorStoreService() {
        VectorStoreTypeEnum type = ragProperties.getVectorStoreType();
        switch (type) {
            case CHROMA:
                return chromaVectorStoreService;
            // 扩展其他向量库只需新增case和实现类
            default:
                return chromaVectorStoreService;
        }
    }
}