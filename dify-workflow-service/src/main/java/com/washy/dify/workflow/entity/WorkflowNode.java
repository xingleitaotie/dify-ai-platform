package com.washy.dify.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("workflow_node")
public class WorkflowNode {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;
    private String nodeId;
    private String name;
    private String nodeType;
    private String config;
    private String position;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}