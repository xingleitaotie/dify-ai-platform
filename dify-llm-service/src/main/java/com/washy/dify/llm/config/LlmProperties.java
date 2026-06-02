package com.washy.dify.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置属性
 * 支持所有主流大模型配置，纯配置驱动切换
 * @author washy
 * @date 2025/12/19
 */
@Data
@Component
@ConfigurationProperties(prefix = "dify.llm")
public class LlmProperties {

    /**
     * 模型类型：ollama/openai/qwen/ernie/spark/chatglm/zhipu
     */
    private String type;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * API base url
     */
    private String baseUrl;

    /**
     * API key
     */
    private String apiKey;

    /**
     * 密钥secret（部分厂商需要）
     */
    private String secret;

    /**
     * 最大token
     */
    private Integer maxTokens = 2048;

    /**
     * 温度参数
     */
    private Double temperature = 0.7D;

    /**
     * 超时时间
     */
    private Integer timeout = 60;


}