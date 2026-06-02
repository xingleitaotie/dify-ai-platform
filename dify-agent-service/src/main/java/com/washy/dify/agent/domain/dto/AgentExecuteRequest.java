package com.washy.dify.agent.domain.dto;

import lombok.Data;
import java.util.Map;

/**
 * Agent 执行请求参数
 */
@Data
public class AgentExecuteRequest {

    /**
     * Agent ID
     */
    private Long agentId;

    /**
     * 函数入参
     */
    private Map<String, Object> params;

    /**
     * 用户提问（可选，后续给 LLM 使用）
     */
    private String query;
}