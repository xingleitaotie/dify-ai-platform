package com.washy.dify.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.washy.dify.agent.domain.AgentKbBind;

import java.math.BigDecimal;
import java.util.List;

public interface AgentKbBindService extends IService<AgentKbBind> {
    AgentKbBind bindKnowledgeBase(Long agentId, String kbId, String kbName, Integer retrieveTopK, BigDecimal scoreThreshold);

    List<AgentKbBind> getByAgentId(Long agentId);

    boolean unbindKnowledgeBase(Long bindId);

    boolean updateBindConfig(Long bindId, Integer retrieveTopK, BigDecimal scoreThreshold);
}