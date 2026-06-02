package com.washy.dify.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.workflow.entity.WorkflowNode;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {

    /**
     * 根据工作流ID查询所有节点
     */
    @Select("SELECT * FROM workflow_node WHERE workflow_id = #{workflowId} ORDER BY create_time")
    List<WorkflowNode> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 根据工作流ID和节点类型查询节点
     */
    @Select("SELECT * FROM workflow_node WHERE workflow_id = #{workflowId} AND node_type = #{nodeType}")
    List<WorkflowNode> selectByWorkflowIdAndType(@Param("workflowId") Long workflowId,
                                                 @Param("nodeType") String nodeType);

    /**
     * 查询开始节点
     */
    @Select("SELECT * FROM workflow_node WHERE workflow_id = #{workflowId} AND node_type = 'START'")
    WorkflowNode selectStartNode(@Param("workflowId") Long workflowId);

    /**
     * 查询结束节点
     */
    @Select("SELECT * FROM workflow_node WHERE workflow_id = #{workflowId} AND node_type = 'END'")
    WorkflowNode selectEndNode(@Param("workflowId") Long workflowId);

    /**
     * 删除工作流的所有节点
     */
    @Delete("DELETE FROM workflow_node WHERE workflow_id = #{workflowId}")
    int deleteByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 根据节点ID删除节点
     */
    @Delete("DELETE FROM workflow_node WHERE workflow_id = #{workflowId} AND node_id = #{nodeId}")
    int deleteByNodeId(@Param("workflowId") Long workflowId, @Param("nodeId") String nodeId);

    /**
     * 批量插入节点
     */
    @Insert({
            "<script>",
            "INSERT INTO workflow_node (workflow_id, node_id, name, node_type, config, position, create_time, update_time) VALUES ",
            "<foreach collection='nodes' item='node' separator=','>",
            "(#{node.workflowId}, #{node.nodeId}, #{node.name}, #{node.nodeType}, #{node.config}, #{node.position}, #{node.createTime}, #{node.updateTime})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("nodes") List<WorkflowNode> nodes);

    /**
     * 根据节点ID查询节点
     */
    @Select("SELECT * FROM workflow_node WHERE node_id = #{nodeId}")
    WorkflowNode selectByNodeId(@Param("nodeId") String nodeId);
}
