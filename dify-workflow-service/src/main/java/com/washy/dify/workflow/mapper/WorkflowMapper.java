package com.washy.dify.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.workflow.entity.Workflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WorkflowMapper extends BaseMapper<Workflow> {

    /**
     * 根据应用ID查询工作流列表
     */
    @Select("SELECT * FROM workflow WHERE app_id = #{appId} AND status != 'ARCHIVED' ORDER BY create_time DESC")
    List<Workflow> selectByAppId(@Param("appId") Long appId);

    /**
     * 根据用户ID查询工作流列表
     */
    @Select("SELECT * FROM workflow WHERE user_id = #{userId} AND status != 'ARCHIVED' ORDER BY create_time DESC")
    List<Workflow> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询已发布的工作流列表
     */
    @Select("SELECT * FROM workflow WHERE user_id = #{userId} AND status = 'PUBLISHED' ORDER BY create_time DESC")
    List<Workflow> selectPublishedByUserId(@Param("userId") Long userId);

    /**
     * 根据应用ID查询已发布的工作流
     */
    @Select("SELECT * FROM workflow WHERE app_id = #{appId} AND status = 'PUBLISHED' ORDER BY create_time DESC")
    List<Workflow> selectPublishedByAppId(@Param("appId") Long appId);

    /**
     * 更新工作流状态
     */
    @Update("UPDATE workflow SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 查询工作流数量（按用户）
     */
    @Select("SELECT COUNT(*) FROM workflow WHERE user_id = #{userId} AND status != 'ARCHIVED'")
    int countByUserId(@Param("userId") Long userId);
}