package com.washy.dify.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.llm.domain.entity.LlmConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LlmConfigMapper extends BaseMapper<LlmConfigEntity> {
    
    @Select("SELECT * FROM llm_config WHERE is_default = 1 AND status = 1 LIMIT 1")
    LlmConfigEntity selectDefaultConfig();
}