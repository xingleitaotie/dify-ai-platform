package com.washy.dify.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("workflow")
public class Workflow {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    private Long appId;
    private Long userId;
    private Integer version;
    private String status;
    private String graph;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
