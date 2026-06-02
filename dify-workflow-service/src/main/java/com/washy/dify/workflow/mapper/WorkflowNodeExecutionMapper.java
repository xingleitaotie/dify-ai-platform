package com.washy.dify.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.workflow.entity.WorkflowNodeExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WorkflowNodeExecutionMapper extends BaseMapper<WorkflowNodeExecution> {

    /**
     * 根据执行ID查询节点执行记录
     */
    @Select("SELECT * FROM workflow_node_execution WHERE execution_id = #{executionId} ORDER BY start_time")
    List<WorkflowNodeExecution> selectByExecutionId(@Param("executionId") String executionId);

    /**
     * 查询指定节点的执行记录
     */
    @Select("SELECT * FROM workflow_node_execution WHERE execution_id = #{executionId} AND node_id = #{nodeId}")
    WorkflowNodeExecution selectByExecutionIdAndNodeId(@Param("executionId") String executionId,
                                                       @Param("nodeId") String nodeId);

    /**
     * 查询失败的节点执行记录
     */
    @Select("SELECT * FROM workflow_node_execution WHERE execution_id = #{executionId} AND status = 'FAILED'")
    List<WorkflowNodeExecution> selectFailedNodes(@Param("executionId") String executionId);

    /**
     * 更新节点执行状态
     */
    @Update("UPDATE workflow_node_execution SET status = #{status}, node_output = #{nodeOutput}, end_time = NOW(), cost_time = #{costTime}, error_msg = #{errorMsg} WHERE id = #{id}")
    int updateNodeStatus(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("nodeOutput") String nodeOutput,
                         @Param("costTime") Long costTime,
                         @Param("errorMsg") String errorMsg);

    /**
     * 查询执行记录中所有节点的耗时统计
     */
    @Select("SELECT node_type, AVG(cost_time) as avg_cost, MAX(cost_time) as max_cost, MIN(cost_time) as min_cost, COUNT(*) as count " +
            "FROM workflow_node_execution WHERE workflow_id = #{workflowId} AND status = 'SUCCESS' " +
            "GROUP BY node_type")
    List<java.util.Map<String, Object>> selectNodeTypeStats(@Param("workflowId") Long workflowId);

}