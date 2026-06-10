// dify-model-provider - UnifiedChatService.java
package com.washy.dify.provider.service;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.provider.client.chat.ChatClient;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.exception.ModelProviderException;
import com.washy.dify.provider.factory.UnifiedClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 统一对话服务 - 自动路由到系统配置的模型
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UnifiedChatService {

    private final SystemConfigService systemConfigService;
    private final ModelConfigService modelConfigService;
    private final UnifiedClientFactory clientFactory;

    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L;

    /**
     * 缓存模型配置
     */
    private ModelConfigEntity cachedChatModel = null;
    private long lastCacheTime = 0;
    private static final long CACHE_TTL = 60000;

    /**
     * 获取系统配置的Chat模型（带缓存）
     */
    private ModelConfigEntity getSystemChatModel() {
        // 缓存检查
        if (cachedChatModel != null && (System.currentTimeMillis() - lastCacheTime) < CACHE_TTL) {
            return cachedChatModel;
        }

        // 从系统配置获取模型ID
        ModelConfigEntity modelConfig = systemConfigService.getCapabilityConfig("chat");
        if (modelConfig == null) {
            throw new ModelProviderException("未配置系统大语言模型，请先在系统模型配置中设置");
        }

        cachedChatModel = modelConfig;
        lastCacheTime = System.currentTimeMillis();
        return modelConfig;
    }

    /**
     * 同步对话
     */
    public String chat(List<ChatMessage> messages) {
        ModelConfigEntity modelConfig = getSystemChatModel();
        ProviderEntity provider = modelConfig.getProvider();

        ChatClient client = clientFactory.createChatClient(provider, modelConfig);
        return client.chat(messages);
    }

    /**
     * 流式对话
     */
    public SseEmitter chatStream(List<ChatMessage> messages) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        ModelConfigEntity modelConfig = getSystemChatModel();
        ProviderEntity provider = modelConfig.getProvider();

        ChatClient client = clientFactory.createChatClient(provider, modelConfig);

        // 异步处理
        clientFactory.getThreadPoolExecutor().execute(() -> {
            try {
                StringBuilder buffer = new StringBuilder();

                client.chatStream(messages, chunk -> {
                    buffer.append(chunk);
                    try {
                        emitter.send(chunk);
                    } catch (IOException e) {
                        log.error("发送SSE失败", e);
                        emitter.completeWithError(e);
                    }
                });

                emitter.send("[DONE]");
                emitter.complete();

            } catch (Exception e) {
                log.error("流式对话异常", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}