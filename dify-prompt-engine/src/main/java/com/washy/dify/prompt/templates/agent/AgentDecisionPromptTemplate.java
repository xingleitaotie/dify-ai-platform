package com.washy.dify.prompt.templates.agent;

import com.washy.dify.prompt.core.ModelParams;
import com.washy.dify.prompt.core.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 决策提示词模板
 */
@Component
public class AgentDecisionPromptTemplate implements PromptTemplate {
    
    private static final String TEMPLATE = 
        "<|im_start|>system\n" +
        "你是 Agent 决策引擎。根据用户问题，决定执行什么动作。\n\n" +
        "【规则】\n" +
        "${rules}\n\n" +
        "【可用工具】\n" +
        "${tools}\n\n" +
        "【输出格式】\n" +
        "只输出 JSON，格式如下：\n" +
        "- 需要检索知识库：{\"action\": \"rag\", \"query\": \"检索关键词\"}\n" +
        "- 需要调用工具：{\"action\": \"function\", \"function\": \"工具名\", \"arguments\": {...}}\n" +
        "- 直接回答：{\"action\": \"direct\"}\n\n" +
        "【示例】\n" +
        "用户问：\"公司2024年财报怎么样？\"\n" +
        "输出：{\"action\": \"rag\", \"query\": \"2024年财报\"}\n\n" +
        "用户问：\"现在几点了？\"\n" +
        "输出：{\"action\": \"function\", \"function\": \"getCurrentTime\", \"arguments\": {}}\n\n" +
        "用户问：\"你好\"\n" +
        "输出：{\"action\": \"direct\"}\n" +
        "<|im_end|>\n" +
        "<|im_start|>user\n" +
        "${question}\n" +
        "<|im_end|>\n" +
        "<|im_start|>assistant\n";
    
    @Override
    public String getName() {
        return "agent.decision";
    }
    
    @Override
    public String getVersion() {
        return "v1.1.0";
    }
    
    @Override
    public String getDescription() {
        return "Agent 决策模板，决定是否需要 RAG 或 Function Calling";
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public String render(Map<String, Object> context) {
        String question = (String) context.getOrDefault("question", "");
        Boolean hasKnowledgeBase = (Boolean) context.getOrDefault("hasKnowledgeBase", false);
        List<String> availableTools = (List<String>) context.getOrDefault("availableTools", new ArrayList<>());
        
        String rules = buildRules(hasKnowledgeBase, availableTools);
        String toolsStr = availableTools.isEmpty() ? "无" : String.join(", ", availableTools);
        
        return TEMPLATE
            .replace("${rules}", rules)
            .replace("${tools}", toolsStr)
            .replace("${question}", question);
    }
    
    private String buildRules(boolean hasKnowledgeBase, List<String> tools) {
        StringBuilder rules = new StringBuilder();
        
        if (hasKnowledgeBase) {
            rules.append("- 你绑定了知识库，对于需要检索知识的问题，你应该输出：{\"action\": \"rag\", \"query\": \"检索关键词\"}\n");
        } else {
            rules.append("- 你没有绑定知识库，不要尝试检索知识\n");
        }
        
        if (!tools.isEmpty()) {
            rules.append("- 你有以下工具可用，根据问题判断是否需要调用\n");
        } else {
            rules.append("- 你没有绑定任何工具\n");
        }
        
        rules.append("- 普通闲聊场景，直接回答\n");
        
        return rules.toString();
    }
    
    @Override
    public ModelParams getModelParams() {
        return ModelParams.forAgentDecision();
    }
    
    @Override
    public TemplateType getType() {
        return TemplateType.AGENT_DECISION;
    }
}