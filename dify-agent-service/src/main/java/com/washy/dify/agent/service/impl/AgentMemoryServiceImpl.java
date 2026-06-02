package com.washy.dify.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.washy.dify.agent.domain.AgentMemory;
import com.washy.dify.agent.mapper.AgentMemoryMapper;
import com.washy.dify.agent.service.AgentMemoryService;
import org.springframework.stereotype.Service;

@Service
public class AgentMemoryServiceImpl extends ServiceImpl<AgentMemoryMapper, AgentMemory> implements AgentMemoryService {}