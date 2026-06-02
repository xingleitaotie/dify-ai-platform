package com.washy.dify.common.entity.workflow;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class WorkflowUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工作流ID不能为空")
    private Long id;

    private String name;

    private String description;

    private WorkflowGraphDTO graph;
}