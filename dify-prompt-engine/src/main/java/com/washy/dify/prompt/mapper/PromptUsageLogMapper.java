package com.washy.dify.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.prompt.entity.PromptUsageLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PromptUsageLogMapper extends BaseMapper<PromptUsageLogEntity> {
    
    /**
     * 统计使用情况
     */
    @Select("SELECT DATE(created_at) as date, COUNT(*) as count, AVG(response_time_ms) as avg_time " +
            "FROM prompt_usage_log " +
            "WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(created_at)")
    List<Map<String, Object>> getWeeklyStats();
}