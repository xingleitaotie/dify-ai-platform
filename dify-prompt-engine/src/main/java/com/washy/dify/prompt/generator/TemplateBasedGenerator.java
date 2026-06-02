package com.washy.dify.prompt.generator;

import com.washy.dify.prompt.entity.GenerateRequest;
import com.washy.dify.prompt.entity.GenerateResponse;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 基于模板的快速生成器（不依赖 LLM）
 */
@Component
public class TemplateBasedGenerator implements PromptGenerator {
    
    private static final Map<String, String> TEMPLATE_MAP = new HashMap<>();
    
    static {
        // 预置高质量模板
        TEMPLATE_MAP.put("RAG_ZH", buildRAGPromptZH());
        TEMPLATE_MAP.put("RAG_EN", buildRAGPromptEN());
        TEMPLATE_MAP.put("FC_ZH", buildFCPromptZH());
        TEMPLATE_MAP.put("SUMMARY_ZH", buildSummaryPromptZH());
        TEMPLATE_MAP.put("AGENT_DECISION_ZH", buildAgentDecisionPromptZH());
    }

    @Override
    public GenerateResponse generateWithModel(GenerateRequest request, Long modelConfigId) {
        GenerateResponse response = new GenerateResponse();
        response.setTemplateId(UUID.randomUUID().toString());
        response.setCreatedAt(new Date());

        String lang = "ZH".equals(request.getLanguage()) ? "ZH" : "EN";
        String key = request.getType().name() + "_" + lang;

        String template = TEMPLATE_MAP.getOrDefault(key, TEMPLATE_MAP.get("RAG_ZH"));

        // 根据参数定制
        template = customizeTemplate(template, request);

        response.setPrompt(template);
        response.setName(request.getType().name().toLowerCase() + "_generated");
        response.setVersion("v1.0.0");

        GenerateResponse.ModelParamsDTO params = new GenerateResponse.ModelParamsDTO();
        params.setTemperature(0.3f);
        params.setMaxTokens(2048);
        response.setModelParams(params);
        response.setConfidenceScore(85);

        return response;
    }

    private String customizeTemplate(String template, GenerateRequest request) {
        if (request.getParameters() != null) {
            for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
                template = template.replace("${" + entry.getKey() + "}", entry.getValue().toString());
            }
        }
        return template;
    }
    
    private static String buildRAGPromptZH() {
        return "<|im_start|>system\n" +
               "你是一个知识库问答助手。请严格遵守以下规则：\n\n" +
               "1. 必须基于【参考知识】回答问题，不要使用自己的知识\n" +
               "2. 如果参考知识中没有相关信息，回答：根据现有知识库，我无法回答这个问题\n" +
               "3. 回答要简洁准确，不要编造信息\n" +
               "4. 用中文回答\n" +
               "<|im_end|>\n" +
               "<|im_start|>user\n" +
               "【参考知识】\n${contexts}\n\n" +
               "问题：${question}\n" +
               "<|im_end|>\n" +
               "<|im_start|>assistant\n";
    }
    
    private static String buildFCPromptZH() {
        return "<|im_start|>system\n" +
               "你是一个函数调用助手。将用户请求转换为JSON格式的函数调用。\n\n" +
               "【可用工具】\n${tools}\n\n" +
               "【输出格式】\n" +
               "需要调用函数时输出：{\"function\": \"函数名\", \"arguments\": {}}\n" +
               "不需要时直接回答问题。\n" +
               "<|im_end|>\n" +
               "<|im_start|>user\n${question}\n<|im_end|>\n" +
               "<|im_start|>assistant\n";
    }
    
    private static String buildSummaryPromptZH() {
        return "<|im_start|>system\n" +
               "你是文档摘要助手。请对以下文本生成30-50字的摘要。\n\n" +
               "要求：\n" +
               "1. 只输出摘要，不要有任何前缀\n" +
               "2. 字数30-50字\n" +
               "3. 提取核心主题和关键信息\n" +
               "<|im_end|>\n" +
               "<|im_start|>user\n${content}\n<|im_end|>\n" +
               "<|im_start|>assistant\n";
    }
    
    private static String buildAgentDecisionPromptZH() {
        return "<|im_start|>system\n" +
               "你是Agent决策引擎。根据用户问题决定执行什么动作。\n\n" +
               "【输出格式】\n" +
               "- 检索知识库：{\"action\": \"rag\", \"query\": \"关键词\"}\n" +
               "- 调用工具：{\"action\": \"function\", \"function\": \"工具名\", \"arguments\": {}}\n" +
               "- 直接回答：{\"action\": \"direct\"}\n" +
               "<|im_end|>\n" +
               "<|im_start|>user\n${question}\n<|im_end|>\n" +
               "<|im_start|>assistant\n";
    }
    
    private static String buildRAGPromptEN() {
        return "<|im_start|>system\n" +
               "You are a knowledge base QA assistant. Follow these rules:\n\n" +
               "1. Answer based ONLY on the provided knowledge\n" +
               "2. If no relevant info, say: I cannot answer based on the knowledge base\n" +
               "3. Be concise and accurate\n" +
               "<|im_end|>\n" +
               "<|im_start|>user\n" +
               "【Knowledge】\n${contexts}\n\n" +
               "Question: ${question}\n" +
               "<|im_end|>\n" +
               "<|im_start|>assistant\n";
    }
}