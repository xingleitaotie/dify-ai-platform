package com.washy.dify.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("workflow_edge")
public class WorkflowEdge {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;
    private String sourceId;
    private String targetId;
    private String sourceHandle;
    private String targetHandle;
    private String condition;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}