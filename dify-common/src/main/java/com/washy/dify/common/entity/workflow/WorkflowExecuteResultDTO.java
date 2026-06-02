package com.washy.dify.common.entity.workflow;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class WorkflowExecuteResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String executionId;
    private String status;
    private Map<String, Object> output;
    private Long costTime;
    private List<NodeExecutionResultDTO> nodeResults;
    private String errorMsg;

    @Data
    public static class NodeExecutionResultDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String nodeId;
        private String nodeName;
        private String nodeType;
        private String status;
        private Map<String, Object> input;
        private Map<String, Object> output;
        private Long costTime;
        private String errorMsg;
    }
}