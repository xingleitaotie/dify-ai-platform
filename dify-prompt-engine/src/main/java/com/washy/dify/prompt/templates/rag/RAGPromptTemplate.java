package com.washy.dify.prompt.templates.rag;

import com.washy.dify.prompt.core.ModelParams;
import com.washy.dify.prompt.core.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 问答提示词模板
 */
@Component
public class RAGPromptTemplate implements PromptTemplate {
    
    private static final String TEMPLATE = 
        "<|im_start|>system\n" +
        "你是一个知识库问答助手。请严格遵守以下规则：\n\n" +
        "1. **必须基于【参考知识】回答问题**，不要使用你自己的知识\n" +
        "2. 如果【参考知识】中没有相关信息，请明确回答：\"根据现有知识库，我无法回答这个问题\"\n" +
        "3. 回答要简洁、准确，不要编造信息\n" +
        "4. 如果有多条相关信息，请综合整理后回答\n" +
        "5. 回答时用中文\n\n" +
        "【参考知识】\n" +
        "${contexts}\n" +
        "<|im_end|>\n" +
        "<|im_start|>user\n" +
        "${question}\n" +
        "<|im_end|>\n" +
        "<|im_start|>assistant\n";
    
    @Override
    public String getName() {
        return "rag.qa";
    }
    
    @Override
    public String getVersion() {
        return "v1.2.0";
    }
    
    @Override
    public String getDescription() {
        return "RAG 增强问答模板，基于知识库检索结果回答问题";
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public String render(Map<String, Object> context) {
        String question = (String) context.getOrDefault("question", "");
        List<String> contexts = (List<String>) context.getOrDefault("contexts", new ArrayList<>());
        
        String contextsStr = contexts.stream()
            .limit(5)  // 最多取5个片段
            .collect(Collectors.joining("\n---\n"));
        
        return TEMPLATE
            .replace("${contexts}", contextsStr)
            .replace("${question}", question);
    }
    
    @Override
    public ModelParams getModelParams() {
        return ModelParams.forRAG();
    }
    
    @Override
    public TemplateType getType() {
        return TemplateType.RAG;
    }
}