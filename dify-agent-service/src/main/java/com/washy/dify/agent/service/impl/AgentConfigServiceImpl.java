package com.washy.dify.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.washy.dify.agent.domain.AgentConfig;
import com.washy.dify.agent.mapper.AgentConfigMapper;
import com.washy.dify.agent.service.AgentConfigService;
import org.springframework.stereotype.Service;

@Service
public class AgentConfigServiceImpl extends ServiceImpl<AgentConfigMapper, AgentConfig> implements AgentConfigService {}