package com.washy.dify.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.washy.dify.agent.domain.AgentKbBind;
import com.washy.dify.agent.mapper.AgentKbBindMapper;
import com.washy.dify.agent.service.AgentKbBindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentKbBindServiceImpl extends ServiceImpl<AgentKbBindMapper, AgentKbBind> implements AgentKbBindService {

    private final AgentKbBindMapper agentKbBindMapper;

    /**
     * 绑定知识库
     */
    @Transactional
    public AgentKbBind bindKnowledgeBase(Long agentId, String kbId, String kbName,
                                         Integer retrieveTopK, BigDecimal scoreThreshold) {
        // 检查是否已绑定
        LambdaQueryWrapper<AgentKbBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKbBind::getAgentId, agentId)
                .eq(AgentKbBind::getKbId, kbId);
        AgentKbBind existing = agentKbBindMapper.selectOne(wrapper);

        if (existing != null) {
            throw new RuntimeException("该知识库已绑定");
        }

        AgentKbBind bind = new AgentKbBind();
        bind.setAgentId(agentId);
        bind.setKbId(kbId);
        bind.setKbName(kbName);
        bind.setRetrieveTopK(retrieveTopK != null ? retrieveTopK : 5);
        bind.setScoreThreshold(scoreThreshold != null ? scoreThreshold : BigDecimal.valueOf(0.7));
        bind.setCreateTime(LocalDateTime.now());

        agentKbBindMapper.insert(bind);
        log.info("绑定知识库成功: agentId={}, kbId={}", agentId, kbId);
        return bind;
    }

    /**
     * 获取 Agent 绑定的知识库列表
     */
    public List<AgentKbBind> getByAgentId(Long agentId) {
        return agentKbBindMapper.selectByAgentId(agentId);
    }

    /**
     * 解绑知识库
     */
    @Transactional
    public boolean unbindKnowledgeBase(Long bindId) {
        int result = agentKbBindMapper.deleteById(bindId);
        log.info("解绑知识库成功: bindId={}", bindId);
        return result > 0;
    }

    /**
     * 更新知识库绑定配置
     */
    @Transactional
    public boolean updateBindConfig(Long bindId, Integer retrieveTopK, BigDecimal scoreThreshold) {
        AgentKbBind bind = agentKbBindMapper.selectById(bindId);
        if (bind == null) {
            throw new RuntimeException("绑定记录不存在");
        }

        if (retrieveTopK != null) {
            bind.setRetrieveTopK(retrieveTopK);
        }
        if (scoreThreshold != null) {
            bind.setScoreThreshold(scoreThreshold);
        }

        int result = agentKbBindMapper.updateById(bind);
        log.info("更新知识库绑定配置: bindId={}, retrieveTopK={}, scoreThreshold={}",
                bindId, retrieveTopK, scoreThreshold);
        return result > 0;
    }

}