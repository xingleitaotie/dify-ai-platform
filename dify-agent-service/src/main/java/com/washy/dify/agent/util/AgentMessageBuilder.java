package com.washy.dify.agent.util;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.washy.dify.agent.domain.AgentConfig;
import com.washy.dify.common.entity.llm.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 对话消息构建器
 */
@Component
@Slf4j
public class AgentMessageBuilder {
    
    /**
     * 构建 Agent 的消息列表
     * 策略：System 放固定指令，User 放动态内容
     */
    public List<ChatMessage> buildForAgent(AgentConfig agent,
                                           String query,
                                           String ragContext,
                                           Object toolResult) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 1. System: Agent 的角色定义和行为规范（固定部分）
        String systemContent = buildSystemContent(agent);
        messages.add(ChatMessage.system(systemContent));
        
        // 2. User: 动态上下文和问题
        String userContent = buildUserContent(query, ragContext, toolResult);
        messages.add(ChatMessage.user(userContent));
        
        log.debug("构建 Agent 消息 - System长度: {}, User长度: {}", 
            systemContent.length(), userContent.length());
        
        return messages;
    }
    
    /**
     * 构建 System Prompt（角色定义）
     */
    private String buildSystemContent(AgentConfig agent) {
        StringBuilder sb = new StringBuilder();
        
        // 基础角色
        sb.append(agent.getSystemPrompt()).append("\n\n");
        
        // 行为规范
        sb.append("【行为规范】\n");
        sb.append("1. 基于提供的参考信息回答问题\n");
        sb.append("2. 如果信息不足，请明确告知用户\n");
        sb.append("3. 保持回答简洁、专业、准确\n");
        sb.append("4. 不要编造不存在的信息\n\n");
        
        // 输出格式要求
        sb.append("【输出格式】\n");
        sb.append("- 直接回答用户问题，不要说多余的开场白\n");
        sb.append("- 如需引用来源，请标注出处\n");
        sb.append("- 使用清晰的分点或段落组织内容\n");
        
        return sb.toString();
    }
    
    /**
     * 构建 User Prompt（动态内容）
     */
    private String buildUserContent(String query, String ragContext, Object toolResult) {
        StringBuilder sb = new StringBuilder();
        
        // 参考知识
        if (StringUtils.hasText(ragContext)) {
            sb.append("【参考知识】\n");
            sb.append(ragContext).append("\n\n");
        }
        
        // 工具结果
        if (toolResult != null) {
            sb.append("【工具执行结果】\n");
            sb.append(JSONUtil.toJsonPrettyStr(toolResult));
            sb.append("\n\n");
        }
        
        // 用户问题
        sb.append("【用户问题】\n");
        sb.append(query);
        
        return sb.toString();
    }
    
    /**
     * 构建合并模式的 Prompt（用于不支持 system role 的模型）
     */
    public String buildMergedPrompt(AgentConfig agent, String query, String ragContext, Object toolResult) {
        StringBuilder sb = new StringBuilder();
        
        // 系统指令
        sb.append("【系统指令】\n");
        sb.append(agent.getSystemPrompt()).append("\n\n");
        
        // 参考信息
        if (StringUtils.hasText(ragContext)) {
            sb.append("【参考信息】\n");
            sb.append(ragContext).append("\n\n");
        }
        
        // 工具结果
        if (toolResult != null) {
            sb.append("【工具结果】\n");
            sb.append(JSON.toJSONString(toolResult)).append("\n\n");
        }
        
        // 用户问题
        sb.append("【用户问题】\n");
        sb.append(query).append("\n\n");
        
        // 回答指令
        sb.append("请基于以上信息回答用户问题。");
        
        return sb.toString();
    }
}