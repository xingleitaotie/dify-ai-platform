package com.washy.dify.common.entity.workflow;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class WorkflowDetailDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private Long appId;
    private Long userId;
    private Integer version;
    private String status;
    private WorkflowGraphDTO graph;
    private Date createTime;
    private Date updateTime;
}