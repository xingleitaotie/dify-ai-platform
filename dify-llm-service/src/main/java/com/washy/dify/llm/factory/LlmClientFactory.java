package com.washy.dify.llm.factory;

import com.washy.dify.llm.client.*;
import com.washy.dify.llm.config.DynamicLlmProperties;
import com.washy.dify.llm.config.LlmProperties;
import com.washy.dify.llm.config.LlmThreadPoolConfig;
import com.washy.dify.llm.domain.entity.LlmConfigEntity;
import com.washy.dify.llm.service.LlmClient;
import com.washy.dify.llm.service.LlmConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * LLM 客户端工厂（支持按类型获取）
 */
@Slf4j
@Component
public class LlmClientFactory {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private DynamicLlmProperties dynamicLlmProperties;

    @Resource
    private LlmConfigService llmConfigService;

    @Resource
    private LlmThreadPoolConfig threadPoolConfig;

    /**
     * 客户端缓存（按配置ID）
     */
    private final ConcurrentHashMap<Long, LlmClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 默认客户端（从配置文件加载）
     */
    private LlmClient defaultClient;

    @PostConstruct
    public void init() {
        // 初始化默认客户端
        String defaultType = dynamicLlmProperties.getType();
        if (defaultType != null) {
            this.defaultClient = getClientByType(defaultType);
        }

        // 从数据库加载默认配置
        LlmConfigEntity defaultConfig = llmConfigService.getDefaultConfig();
        if (defaultConfig != null) {
            dynamicLlmProperties.refreshConfig(defaultConfig);
            log.info("初始化加载默认配置: {} -> {}", defaultConfig.getConfigName(), defaultConfig.getType());
        }
    }

    /**
     * 获取当前配置的 LLM 客户端（用于内部调用）
     */
    public LlmClient getLlmClient() {
        Long configId = dynamicLlmProperties.getCurrentConfigId();
        if (configId != null) {
            return getClientByConfigId(configId);
        }

        // 如果没有当前配置，从数据库获取默认配置
        LlmConfigEntity defaultConfig = llmConfigService.getDefaultConfig();
        if (defaultConfig != null) {
            log.info("使用数据库默认模型配置: {}", defaultConfig.getConfigName());
            return getClientByConfig(defaultConfig);
        }

        // 降级到默认客户端
        return defaultClient;
    }

    /**
     * 根据模型类型获取客户端（前端传入）
     * @param modelType 模型类型，如 modelScope, openai, ollama 等，可以为null
     */
    public LlmClient getLlmClientByType(String modelType) {
        // 直接调用 getClientByType，它已经处理了空值情况
        return getClientByType(modelType);
    }

    /**
     * 根据配置ID获取客户端
     */
    public LlmClient getLlmClientByConfigId(Long configId) {
        if (configId == null) {
            return getLlmClient();
        }
        return getClientByConfigId(configId);
    }

    /**
     * 根据模型类型获取内置客户端（不依赖数据库）
     * @param type 模型类型，如果为null或空，则从数据库获取第一个启用的模型配置
     */
    public LlmClient getClientByType(String type) {
        // 如果没有指定类型，从数据库获取第一个启用的模型配置
        if (type == null || type.trim().isEmpty()) {
            log.info("未指定模型类型，尝试从数据库获取默认模型配置");
            LlmConfigEntity defaultConfig = llmConfigService.getDefaultConfig();
            if (defaultConfig != null) {
                log.info("使用数据库默认模型配置: {} ({})", defaultConfig.getConfigName(), defaultConfig.getType());
                return getClientByConfig(defaultConfig);
            }

            // 如果没有默认配置，获取第一个启用的配置
            List<LlmConfigEntity> enabledConfigs = llmConfigService.getEnabledConfigs();
            if (enabledConfigs != null && !enabledConfigs.isEmpty()) {
                LlmConfigEntity firstConfig = enabledConfigs.get(0);
                log.info("使用第一个启用的模型配置: {} ({})", firstConfig.getConfigName(), firstConfig.getType());
                return getClientByConfig(firstConfig);
            }

            log.warn("数据库中没有启用的模型配置，尝试使用内置客户端");
        }

        // 尝试从Spring容器获取Bean
        String beanName = type.toLowerCase() + "Client";
        try {
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName, LlmClient.class);
            }
        } catch (Exception e) {
            log.warn("获取客户端失败: {}", beanName, e);
        }

        // 根据类型手动创建客户端（兼容Java 1.8）
        return createClientByType(type);
    }

    /**
     * 根据配置实体获取客户端（使用数据库配置）
     */
    public LlmClient getClientByConfig(LlmConfigEntity config) {
        if (config == null) {
            return getLlmClient();
        }

        return clientCache.computeIfAbsent(config.getId(), id -> {
            log.info("创建新的客户端实例，配置ID: {}, 类型: {}", id, config.getType());
            return createClient(config);
        });
    }

    /**
     * 根据配置ID获取客户端
     */
    private LlmClient getClientByConfigId(Long configId) {
        LlmConfigEntity config = llmConfigService.getConfigById(configId);
        if (config == null || config.getStatus() != 1) {
            log.warn("配置不存在或已禁用: {}, 使用默认客户端", configId);
            return getLlmClient();
        }
        return getClientByConfig(config);
    }

    /**
     * 根据配置实体创建客户端（静态方法，供外部调用）
     */
    public static LlmClient createClient(LlmConfigEntity config) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }

        String type = config.getType().toLowerCase();

        // 创建临时配置
        LlmProperties tempProps = new LlmProperties();
        tempProps.setType(type);
        tempProps.setModelName(config.getModelName());
        tempProps.setBaseUrl(config.getBaseUrl());
        tempProps.setApiKey(config.getApiKey());
        tempProps.setSecret(config.getSecret());
        tempProps.setMaxTokens(config.getMaxTokens());
        tempProps.setTemperature(config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7);
        tempProps.setTimeout(config.getTimeout());

        // 根据类型创建客户端并设置配置
        switch (type) {
            case "ollama":
                OllamaClient ollamaClient = new OllamaClient();
                ollamaClient.setLlmProperties(tempProps);
                return ollamaClient;
            case "openai":
                OpenAiClient openAiClient = new OpenAiClient();
                openAiClient.setLlmProperties(tempProps);
                return openAiClient;
            case "modelscope":
                ModelScopeLlmClient modelScopeClient = new ModelScopeLlmClient();
                modelScopeClient.setLlmProperties(tempProps);
                return modelScopeClient;
            case "qwen":
                QwenClient qwenClient = new QwenClient();
                qwenClient.setLlmProperties(tempProps);
                return qwenClient;
            case "ernie":
                ErnieClient ernieClient = new ErnieClient();
                ernieClient.setLlmProperties(tempProps);
                return ernieClient;
            case "spark":
                SparkClient sparkClient = new SparkClient();
                sparkClient.setLlmProperties(tempProps);
                return sparkClient;
            case "zhipu":
                ZhipuClient zhipuClient = new ZhipuClient();
                zhipuClient.setLlmProperties(tempProps);
                return zhipuClient;
            default:
                throw new IllegalArgumentException("不支持的模型类型: " + type);
        }
    }

    /**
     * 根据类型字符串创建客户端（不使用Spring Bean）
     */
    private LlmClient createClientByType(String type) {
        LlmProperties props = dynamicLlmProperties.toLlmProperties();

        switch (type.toLowerCase()) {
            case "ollama":
                OllamaClient ollamaClient = new OllamaClient();
                ollamaClient.setLlmProperties(props);
                return ollamaClient;
            case "openai":
                OpenAiClient openAiClient = new OpenAiClient();
                openAiClient.setLlmProperties(props);
                return openAiClient;
            case "modelscope":
                ModelScopeLlmClient modelScopeClient = new ModelScopeLlmClient();
                modelScopeClient.setLlmProperties(props);
                return modelScopeClient;
            case "qwen":
                QwenClient qwenClient = new QwenClient();
                qwenClient.setLlmProperties(props);
                return qwenClient;
            case "ernie":
                ErnieClient ernieClient = new ErnieClient();
                ernieClient.setLlmProperties(props);
                return ernieClient;
            case "spark":
                SparkClient sparkClient = new SparkClient();
                sparkClient.setLlmProperties(props);
                return sparkClient;
            case "zhipu":
                ZhipuClient zhipuClient = new ZhipuClient();
                zhipuClient.setLlmProperties(props);
                return zhipuClient;
            default:
                log.warn("不支持的模型类型: {}, 使用默认客户端", type);
                return defaultClient;
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache(Long configId) {
        clientCache.remove(configId);
    }

    /**
     * 获取线程池
     */
    public ExecutorService getThreadPoolExecutor() {
        return threadPoolConfig.llmThreadPoolExecutor();
    }
}