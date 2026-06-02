package com.washy.dify.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("workflow_execution")
public class WorkflowExecution {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String executionId;
    private Long workflowId;
    private Integer workflowVersion;
    private String sessionId;
    private String input;
    private String output;
    private String status;
    private Date startTime;
    private Date endTime;
    private Long costTime;
    private String errorMsg;
}