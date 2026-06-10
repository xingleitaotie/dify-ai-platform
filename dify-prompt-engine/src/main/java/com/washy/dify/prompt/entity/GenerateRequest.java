package com.washy.dify.prompt.entity;

import lombok.Data;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 提示词生成请求
 */
@Data
public class GenerateRequest {
    
    /**
     * 需求描述
     */
    private String requirement;
    
    /**
     * 模板类型
     */
    private TemplateType type;
    
    /**
     * 可选参数
     */
    private Map<String, Object> parameters;
    
    /**
     * 参考示例
     */
    private List<Example> examples;
    
    /**
     * 语言（中文/英文）
     */
    private String language = "zh-CN";

    private Double temperature;

    
    /**
     * 是否需要流式输出
     */
    private Boolean streaming = false;

    private Long modelConfigId;  // 使用的模型配置ID

    private String modelType;     // 模型类型（如果没有指定configId，可以通过类型查找）

    private String modelName;     // 模型名称（直接指定）

    /**
     * 期望的输出格式
     */
    private OutputFormat outputFormat = OutputFormat.TEXT;

    /**
     * 模板类型枚举
     */
    @Getter
    public enum TemplateType {
        GENERAL("general", "通用聊天"),
        CUSTOM("custom", "自定义"),
        SUMMARY("summary", "内容总结"),
        AGENT_ANSWER("agent_answer", "Agent回答"),
        FUNCTION_CALLING("function_calling", "函数调用"),
        AGENT_DECISION("agent_decision", "Agent决策"),
        RAG("rag", "RAG问答"),
        CODE("code", "代码生成"),
        CREATIVE("creative", "创意写作"),
        DATA("data", "数据分析"),
        STREAMING("streaming", "流式对话");

        private final String value;
        private final String label;

        TemplateType(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public static TemplateType fromValue(String value) {
            for (TemplateType type : TemplateType.values()) {
                if (type.value.equals(value) || type.name().equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return CUSTOM;
        }
    }
    
    public enum OutputFormat {
        TEXT, JSON, YAML, MARKDOWN
    }
    
    @Data
    public static class Example {
        private String input;
        private String output;
    }
}