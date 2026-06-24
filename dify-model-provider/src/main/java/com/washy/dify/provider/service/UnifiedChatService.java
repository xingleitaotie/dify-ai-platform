// dify-model-provider - UnifiedChatService.java
package com.washy.dify.provider.service;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.provider.client.chat.ChatClient;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.exception.ModelProviderException;
import com.washy.dify.provider.factory.UnifiedClientFactory;
import com.washy.dify.provider.mapper.ModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final ModelConfigMapper modelConfigMapper;

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
     * 获取系统配置的Chat模型（带缓存）
     */
    private ModelConfigEntity getConditionChatModel(Long configId) {

        ModelConfigEntity modelConfig = modelConfigService.getById(configId);
        if (modelConfig == null) {
            throw new ModelProviderException("未配置系统大语言模型，请先在系统模型配置中设置");
        }

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
     * 支持工具调用的对话
     */
    public String conditionChat(ChatRequestDTO request) {
        ChatClient client = null;
        Double temperature = 0.0;
        Integer maxTokens = 0;
        if (null != request.getConfigId()){
            ModelConfigEntity conditionModel = getConditionChatModel(request.getConfigId());
            Map<String,Object> params = new HashMap<>();
            if (!request.getParams().isEmpty()){
                params = request.getParams();
            }
            maxTokens = params.get("maxTokens") == null ? conditionModel.getMaxTokens() : Integer.parseInt(params.get("maxTokens").toString());
            temperature = params.get("temperature") == null ? conditionModel.getTemperature() : Double.parseDouble(params.get("temperature").toString());
            ProviderEntity provider = conditionModel.getProvider();
            client = clientFactory.createChatClient(provider, conditionModel);
        }else {
            ModelConfigEntity modelConfig = getSystemChatModel();
            Map<String,Object> params = new HashMap<>();
            if (!request.getParams().isEmpty()){
                params = request.getParams();
            }
            maxTokens = params.get("maxTokens") == null ? modelConfig.getMaxTokens() : Integer.parseInt(params.get("maxTokens").toString());
            temperature = params.get("temperature") == null ? modelConfig.getTemperature() : Double.parseDouble(params.get("temperature").toString());

            ProviderEntity provider = modelConfig.getProvider();
            client = clientFactory.createChatClient(provider, modelConfig);
        }

        List<ChatMessage> messages = request.getMessages();

        return client.chat(messages, temperature, maxTokens);

    }

    /**
     * 支持工具调用的对话
     */
    public String chatWithTools(List<ChatMessage> messages, List<Map<String, Object>> tools, String toolChoice) {
        ModelConfigEntity modelConfig = getSystemChatModel();
        ProviderEntity provider = modelConfig.getProvider();

        ChatClient client = clientFactory.createChatClient(provider, modelConfig);

        return client.chatWithTools(messages, tools, toolChoice);

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