package com.washy.dify.prompt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 提示词版本配置
 */
@Data
@ConfigurationProperties(prefix = "dify.prompt.version")
public class PromptVersionConfig {
    
    /**
     * 当前使用的版本
     */
    private Map<String, String> activeVersions;
    
    /**
     * 是否启用 A/B 测试
     */
    private boolean enableABTest = false;
    
    /**
     * A/B 测试配置
     */
    private Map<String, Map<String, Integer>> abTestConfig;
}