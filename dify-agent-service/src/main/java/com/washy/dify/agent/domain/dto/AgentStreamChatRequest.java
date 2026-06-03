package com.washy.dify.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Agent 流式对话请求
 */
@Data
public class AgentStreamChatRequest {

    @NotNull(message = "agentId 不能为空")
    private Long agentId;

    @NotNull(message = "用户提问不能为空")
    private String query;

    /**
     * 会话ID（多轮对话、记忆使用）
     * 不传则使用默认会话
     */
    private String sessionId;

    private String modelConfigId;
}