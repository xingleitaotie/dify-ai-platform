package com.washy.dify.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.prompt.entity.PromptVersionHistoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PromptVersionHistoryMapper extends BaseMapper<PromptVersionHistoryEntity> {
    
    @Select("SELECT * FROM prompt_version_history WHERE template_id = #{templateId} ORDER BY created_at DESC")
    List<PromptVersionHistoryEntity> getVersionHistory(@Param("templateId") String templateId);
}