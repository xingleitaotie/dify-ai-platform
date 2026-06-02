package com.washy.dify.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("workflow_node_execution")
public class WorkflowNodeExecution {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String executionId;
    private Long workflowId;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String nodeInput;
    private String nodeOutput;
    private String status;
    private Date startTime;
    private Date endTime;
    private Long costTime;
    private String errorMsg;
}