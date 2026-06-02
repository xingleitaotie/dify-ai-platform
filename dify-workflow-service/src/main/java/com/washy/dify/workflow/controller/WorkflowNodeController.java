package com.washy.dify.workflow.controller;

import com.washy.dify.common.result.Result;
import com.washy.dify.common.result.ResultCode;
import com.washy.dify.workflow.entity.NodeOutputType;
import com.washy.dify.workflow.entity.WorkflowNode;
import com.washy.dify.workflow.mapper.WorkflowNodeMapper;
import com.washy.dify.workflow.service.NodeOutputTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/workflow/node")
public class WorkflowNodeController {
    
    @Autowired
    private NodeOutputTypeService nodeOutputTypeService;
    @Autowired
    private WorkflowNodeMapper nodeMapper;
    
    /**
     * 获取节点的输出类型信息
     */
    @GetMapping("/output-type/{nodeId}")
    public Result<NodeOutputType> getNodeOutputType(@PathVariable String nodeId) {
        WorkflowNode node = nodeMapper.selectByNodeId(nodeId);
        if (node == null) {
            return Result.failed(ResultCode.WORKFLOW_NOT_NODE);
        }
        NodeOutputType outputType = nodeOutputTypeService.getNodeOutputType(node);
        return Result.success(outputType);
    }
    
    /**
     * 获取工作流所有节点的输出类型
     */
    @GetMapping("/output-types/{workflowId}")
    public Result<List<NodeOutputType>> getAllNodeOutputTypes(@PathVariable Long workflowId) {
        List<WorkflowNode> nodes = nodeMapper.selectByWorkflowId(workflowId);
        List<NodeOutputType> result = new ArrayList<>();
        for (WorkflowNode node : nodes) {
            result.add(nodeOutputTypeService.getNodeOutputType(node));
        }
        return Result.success(result);
    }
}