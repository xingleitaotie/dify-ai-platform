package com.washy.dify.common.entity.workflow;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;

@Data
public class WorkflowExecuteDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工作流ID不能为空")
    private Long workflowId;

    private String sessionId;

    private String userId;

    private Map<String, Object> inputs;
}