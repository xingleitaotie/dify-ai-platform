package com.washy.dify.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.washy.dify.agent.domain.AgentToolBind;
import com.washy.dify.agent.mapper.AgentToolBindMapper;
import com.washy.dify.agent.service.AgentToolBindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
public class AgentToolBindServiceImpl extends ServiceImpl<AgentToolBindMapper, AgentToolBind> implements AgentToolBindService {
    @Resource
    private AgentToolBindMapper agentToolBindMapper;

    @Transactional
    public boolean unbindTool(Long bindId) {
        int result = agentToolBindMapper.deleteById(bindId);
        log.info("解绑工具成功: bindId={}", bindId);
        return result > 0;
    }

    @Transactional
    public boolean updateToolStatus(Long bindId, Integer isEnabled) {
        AgentToolBind bind = agentToolBindMapper.selectById(bindId);
        if (bind == null) {
            throw new RuntimeException("绑定记录不存在");
        }
        bind.setIsEnabled(isEnabled);
        bind.setUpdateTime(LocalDateTime.now());
        int result = agentToolBindMapper.updateById(bind);
        log.info("更新工具状态: bindId={}, isEnabled={}", bindId, isEnabled);
        return result > 0;
    }

}