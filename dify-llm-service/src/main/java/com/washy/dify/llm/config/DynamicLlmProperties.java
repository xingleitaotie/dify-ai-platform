package com.washy.dify.llm.config;

import com.washy.dify.llm.domain.entity.LlmConfigEntity;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 动态LLM配置（支持热加载）
 */
@Data
@Slf4j
@Component
public class DynamicLlmProperties {
    
    private volatile Long currentConfigId;
    private volatile String type;
    private volatile String modelName;
    private volatile String baseUrl;
    private volatile String apiKey;
    private volatile String secret;
    private volatile Integer maxTokens = 2048;
    private volatile Double temperature = 0.7;
    private volatile Integer timeout = 60;
    
    /**
     * 刷新配置
     */
    public void refreshConfig(LlmConfigEntity config) {
        if (config == null) return;
        
        this.currentConfigId = config.getId();
        this.type = config.getType();
        this.modelName = config.getModelName();
        this.baseUrl = config.getBaseUrl();
        this.apiKey = config.getApiKey();
        this.secret = config.getSecret();
        this.maxTokens = config.getMaxTokens();
        this.temperature = config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7;
        this.timeout = config.getTimeout();
        
        log.info("动态配置已刷新: type={}, modelName={}", type, modelName);
    }
    
    /**
     * 切换配置
     */
    public void switchConfig(LlmConfigEntity config) {
        refreshConfig(config);
    }
    
    /**
     * 转换为LlmProperties（兼容旧代码）
     */
    public LlmProperties toLlmProperties() {
        LlmProperties props = new LlmProperties();
        props.setType(this.type);
        props.setModelName(this.modelName);
        props.setBaseUrl(this.baseUrl);
        props.setApiKey(this.apiKey);
        props.setSecret(this.secret);
        props.setMaxTokens(this.maxTokens);
        props.setTemperature(this.temperature);
        props.setTimeout(this.timeout);
        return props;
    }
}