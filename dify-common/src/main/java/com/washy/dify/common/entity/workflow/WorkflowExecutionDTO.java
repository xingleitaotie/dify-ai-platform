package com.washy.dify.common.entity.workflow;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class WorkflowExecutionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String executionId;
    private Long workflowId;
    private Integer workflowVersion;
    private String sessionId;
    private String status;
    private Date startTime;
    private Date endTime;
    private Long costTime;
    private String errorMsg;
}
