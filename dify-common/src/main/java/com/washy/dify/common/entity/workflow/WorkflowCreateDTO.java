package com.washy.dify.common.entity.workflow;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class WorkflowCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "工作流名称不能为空")
    private String name;

    private String description;

    private Long appId;

    private WorkflowGraphDTO graph;
}
