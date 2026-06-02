package com.washy.dify.common.entity.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * LLM 对话消息实体
 * 符合 OpenAI API 标准格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息角色：system, user, assistant, function
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息名称（可选）
     */
    private String name;

    /**
     * 时间戳（用于会话管理）
     */
    private Long timestamp;

    /**
     * 函数调用
     */
    @JsonProperty("function_call")
    private FunctionCall functionCall;

    // 便捷构造方法
    public static ChatMessage system(String content) {
        return ChatMessage.builder()
                .role("system")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ChatMessage user(String content) {
        return ChatMessage.builder()
                .role("user")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder()
                .role("assistant")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 函数调用定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private String arguments;
    }
}