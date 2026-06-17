package com.washy.dify.common.entity.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowEdgeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String source;
    private String target;
    private String sourceHandle;
    private String targetHandle;
    private String condition;
}