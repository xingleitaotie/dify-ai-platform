package com.washy.dify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.agent.domain.AgentConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent配置Mapper
 */
@Mapper
public interface AgentConfigMapper extends BaseMapper<AgentConfig> {

}