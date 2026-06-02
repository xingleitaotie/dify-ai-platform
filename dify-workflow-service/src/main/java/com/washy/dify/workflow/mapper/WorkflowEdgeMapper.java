package com.washy.dify.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.workflow.entity.WorkflowEdge;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WorkflowEdgeMapper extends BaseMapper<WorkflowEdge> {

    /**
     * 根据工作流ID查询所有边
     */
    @Select("SELECT * FROM workflow_edge WHERE workflow_id = #{workflowId} ORDER BY create_time")
    List<WorkflowEdge> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 查询节点的出边
     */
    @Select("SELECT * FROM workflow_edge WHERE workflow_id = #{workflowId} AND source_id = #{sourceId}")
    List<WorkflowEdge> selectOutgoingEdges(@Param("workflowId") Long workflowId,
                                           @Param("sourceId") String sourceId);

    /**
     * 查询节点的入边
     */
    @Select("SELECT * FROM workflow_edge WHERE workflow_id = #{workflowId} AND target_id = #{targetId}")
    List<WorkflowEdge> selectIncomingEdges(@Param("workflowId") Long workflowId,
                                           @Param("targetId") String targetId);

    /**
     * 删除工作流的所有边
     */
    @Delete("DELETE FROM workflow_edge WHERE workflow_id = #{workflowId}")
    int deleteByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 删除指定节点的边
     */
    @Delete("DELETE FROM workflow_edge WHERE workflow_id = #{workflowId} AND (source_id = #{nodeId} OR target_id = #{nodeId})")
    int deleteByNodeId(@Param("workflowId") Long workflowId, @Param("nodeId") String nodeId);

    /**
     * 批量插入边
     */
    @Insert({
            "<script>",
            "INSERT INTO workflow_edge (workflow_id, source_id, target_id, source_handle, target_handle, `condition`, create_time) VALUES ",
            "<foreach collection='edges' item='edge' separator=','>",
            "(#{edge.workflowId}, #{edge.sourceId}, #{edge.targetId}, #{edge.sourceHandle}, #{edge.targetHandle}, #{edge.condition}, #{edge.createTime})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("edges") List<WorkflowEdge> edges);
}