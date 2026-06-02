package com.washy.dify.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.workflow.entity.WorkflowExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {

    /**
     * 根据执行ID查询
     */
    @Select("SELECT * FROM workflow_execution WHERE execution_id = #{executionId}")
    WorkflowExecution selectByExecutionId(@Param("executionId") String executionId);

    /**
     * 分页查询工作流执行记录
     */
    @Select("SELECT * FROM workflow_execution WHERE workflow_id = #{workflowId} ORDER BY start_time DESC LIMIT #{offset}, #{size}")
    List<WorkflowExecution> selectByWorkflowIdWithPage(@Param("workflowId") Long workflowId,
                                                       @Param("offset") int offset,
                                                       @Param("size") int size);

    /**
     * 查询工作流执行记录总数
     */
    @Select("SELECT COUNT(*) FROM workflow_execution WHERE workflow_id = #{workflowId}")
    int countByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 根据会话ID查询执行记录
     */
    @Select("SELECT * FROM workflow_execution WHERE session_id = #{sessionId} ORDER BY start_time DESC")
    List<WorkflowExecution> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询最近的执行记录
     */
    @Select("SELECT * FROM workflow_execution WHERE workflow_id = #{workflowId} ORDER BY start_time DESC LIMIT #{limit}")
    List<WorkflowExecution> selectRecentByWorkflowId(@Param("workflowId") Long workflowId,
                                                     @Param("limit") int limit);

    /**
     * 更新执行状态
     */
    @Update("UPDATE workflow_execution SET status = #{status}, end_time = NOW(), cost_time = #{costTime}, error_msg = #{errorMsg} WHERE execution_id = #{executionId}")
    int updateStatus(@Param("executionId") String executionId,
                     @Param("status") String status,
                     @Param("costTime") Long costTime,
                     @Param("errorMsg") String errorMsg);
}
