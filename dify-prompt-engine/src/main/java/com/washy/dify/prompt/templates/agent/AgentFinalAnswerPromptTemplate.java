package com.washy.dify.prompt.templates.agent;

import com.washy.dify.prompt.core.ModelParams;
import com.washy.dify.prompt.core.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 最终答案生成提示词模板
 */
@Component
public class AgentFinalAnswerPromptTemplate implements PromptTemplate {
    
    private static final String TEMPLATE = 
        "<|im_start|>system\n" +
        "${systemPrompt}\n\n" +
        "【对话历史】\n" +
        "${history}\n\n" +
        "【知识库检索结果】\n" +
        "${ragResult}\n\n" +
        "【工具调用结果】\n" +
        "${functionResult}\n\n" +
        "【回答要求】\n" +
        "1. 优先使用知识库和工具结果\n" +
        "2. 保持与历史对话的一致性\n" +
        "3. 回答要自然、连贯\n" +
        "4. 如果信息不足，诚实告知用户\n" +
        "5. 用中文回答\n" +
        "<|im_end|>\n" +
        "<|im_start|>user\n" +
        "${question}\n" +
        "<|im_end|>\n" +
        "<|im_start|>assistant\n";
    
    @Override
    public String getName() {
        return "agent.final.answer";
    }
    
    @Override
    public String getVersion() {
        return "v1.2.0";
    }
    
    @Override
    public String getDescription() {
        return "Agent 最终答案生成模板，整合 RAG 和 FC 结果";
    }
    
    @Override
    public String render(Map<String, Object> context) {
        String question = getString(context, "question", "");
        String systemPrompt = getString(context, "systemPrompt", "你是一个智能助手");
        String ragResult = getString(context, "ragResult", "无");
        String functionResult = getString(context, "functionResult", "无");
        
        Object historyObj = context.get("history");
        List<Map<String, String>> history = new ArrayList<>();
        if (historyObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<?> historyList = (List<?>) historyObj;
            for (Object item : historyList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> msg = (Map<String, String>) item;
                    history.add(msg);
                }
            }
        }
        
        String historyStr = history.stream()
            .map(msg -> msg.get("role") + ": " + msg.get("content"))
            .collect(Collectors.joining("\n"));
        
        return TEMPLATE
            .replace("${systemPrompt}", systemPrompt)
            .replace("${history}", historyStr)
            .replace("${ragResult}", ragResult)
            .replace("${functionResult}", functionResult)
            .replace("${question}", question);
    }
    
    private String getString(Map<String, Object> context, String key, String defaultValue) {
        Object value = context.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
    
    @Override
    public ModelParams getModelParams() {
        return ModelParams.forChat();
    }
    
    @Override
    public TemplateType getType() {
        return TemplateType.AGENT_ANSWER;
    }
}