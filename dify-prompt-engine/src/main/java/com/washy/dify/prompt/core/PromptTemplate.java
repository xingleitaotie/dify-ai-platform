package com.washy.dify.prompt.core;

import java.util.Map;

/**
 * 提示词模板接口
 */
public interface PromptTemplate {
    
    /**
     * 模板名称（唯一标识）
     */
    String getName();
    
    /**
     * 模板版本
     */
    String getVersion();
    
    /**
     * 模板描述
     */
    default String getDescription() {
        return "";
    }
    
    /**
     * 渲染提示词
     * @param context 上下文参数
     * @return 渲染后的提示词字符串
     */
    String render(Map<String, Object> context);
    
    /**
     * 获取模型参数配置
     */
    ModelParams getModelParams();
    
    /**
     * 是否为流式输出
     */
    default boolean isStreaming() {
        return false;
    }
    
    /**
     * 获取模板类型
     */
    default TemplateType getType() {
        return TemplateType.COMMON;
    }
    
    /**
     * 模板类型枚举
     */
    enum TemplateType {
        RAG, FUNCTION_CALLING, AGENT_DECISION, AGENT_ANSWER, SUMMARY, SYSTEM, COMMON
    }
}