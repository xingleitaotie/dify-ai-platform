package com.washy.dify.prompt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 提示词配置
 */
@Data
@ConfigurationProperties(prefix = "dify.prompt")
public class PromptConfig {
    
    /**
     * 模板加载路径
     */
    private String templatePath = "classpath:prompts/";
    
    /**
     * 模板文件格式 (yaml, json, txt)
     */
    private String templateFormat = "yaml";
    
    /**
     * 是否启用缓存
     */
    private boolean enableCache = true;
    
    /**
     * 最大内容长度
     */
    private int maxContentLength = 2000;
    
    /**
     * 默认温度
     */
    private float defaultTemperature = 0.3f;
    
    /**
     * 自定义模板映射
     */
    private Map<String, String> customTemplates;
}