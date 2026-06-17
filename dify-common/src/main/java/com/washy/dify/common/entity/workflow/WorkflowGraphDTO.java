package com.washy.dify.common.entity.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowGraphDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<WorkflowNodeDTO> nodes;
    private List<WorkflowEdgeDTO> edges;
}
