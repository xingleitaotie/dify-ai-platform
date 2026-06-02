package com.washy.dify.common.entity.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * LLM 请求参数封装
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {
    
    /**
     * 模型名称
     */
    private String model;
    
    /**
     * 消息列表
     */
    private List<ChatMessage> messages;
    
    /**
     * 温度参数（0-1），控制输出随机性
     */
    private Double temperature;
    
    /**
     * 最大生成token数
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    /**
     * Top P 采样参数
     */
    @JsonProperty("top_p")
    private Double topP;
    
    /**
     * 频率惩罚参数
     */
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;
    
    /**
     * 存在惩罚参数
     */
    @JsonProperty("presence_penalty")
    private Double presencePenalty;
    
    /**
     * 是否流式输出
     */
    private Boolean stream;
    
    /**
     * 停止词列表
     */
    private List<String> stop;
    
    /**
     * 用户标识
     */
    private String user;
    
    /**
     * 工具定义列表
     */
    private List<Map<String, Object>> tools;
    
    /**
     * 工具选择策略
     */
    @JsonProperty("tool_choice")
    private Object toolChoice;
    
    /**
     * 响应格式（json_object 或 text）
     */
    @JsonProperty("response_format")
    private Map<String, String> responseFormat;
    
    /**
     * 种子值，用于确定性输出
     */
    private Integer seed;
}