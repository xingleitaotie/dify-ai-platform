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
 * LLM 响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {
    
    /**
     * 响应ID
     */
    private String id;
    
    /**
     * 对象类型
     */
    private String object;
    
    /**
     * 创建时间戳
     */
    private Long created;
    
    /**
     * 模型名称
     */
    private String model;
    
    /**
     * 消息列表
     */
    private List<ChatMessage> choices;
    
    /**
     * Token 使用统计
     */
    private Map<String, Integer> usage;

    private String content;

    private String createdAt;
    
    /**
     * 第一个选择的消息内容（便捷方法）
     */
    public String getFirstChoiceContent() {
        if (choices != null && !choices.isEmpty()) {
            ChatMessage message = choices.get(0);
            return message != null ? message.getContent() : null;
        }
        return null;
    }

    /**
     * Token 使用统计内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}