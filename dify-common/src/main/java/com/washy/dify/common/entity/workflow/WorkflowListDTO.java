package com.washy.dify.common.entity.workflow;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class WorkflowListDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private Integer version;
    private String status;
    private Date createTime;
    private Date updateTime;
}
