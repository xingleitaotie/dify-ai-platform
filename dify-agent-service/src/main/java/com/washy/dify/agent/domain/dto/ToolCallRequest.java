package com.washy.dify.agent.domain.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ToolCallRequest {
    private String toolName;
    private Map<String, Object> parameters;
}