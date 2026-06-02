package com.washy.dify.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.washy.dify.agent.domain.AgentToolBind;

public interface AgentToolBindService extends IService<AgentToolBind> {
    boolean updateToolStatus(Long bindId, Integer isEnabled);

    boolean unbindTool(Long bindId);
}