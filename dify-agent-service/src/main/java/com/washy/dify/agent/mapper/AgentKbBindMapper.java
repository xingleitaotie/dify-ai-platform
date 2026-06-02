package com.washy.dify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.washy.dify.agent.domain.AgentKbBind;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent配置Mapper
 */
@Mapper
public interface AgentKbBindMapper extends BaseMapper<AgentKbBind> {
    @Select("SELECT * FROM agent_kb_bind WHERE agent_id = #{agentId}")
    List<AgentKbBind> selectByAgentId(@Param("agentId") Long agentId);

    @Delete("DELETE FROM agent_kb_bind WHERE agent_id = #{agentId} AND kb_id = #{kbId}")
    int deleteByAgentAndKb(@Param("agentId") Long agentId, @Param("kbId") Long kbId);
}