package com.washy.dify.prompt.entity;

import lombok.Data;
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
    
    /**
     * 是否需要流式输出
     */
    private Boolean streaming = false;

    private Long modelConfigId;  // 使用的模型配置ID

    private String modelType;     // 模型类型（如果没有指定configId，可以通过类型查找）

    private String modelName;     // 模型名称（直接指定）

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public TemplateType getType() {
        return type;
    }

    public void setType(TemplateType type) {
        this.type = type;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public List<Example> getExamples() {
        return examples;
    }

    public void setExamples(List<Example> examples) {
        this.examples = examples;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Boolean getStreaming() {
        return streaming;
    }

    public void setStreaming(Boolean streaming) {
        this.streaming = streaming;
    }

    public Long getModelConfigId() {
        return modelConfigId;
    }

    public void setModelConfigId(Long modelConfigId) {
        this.modelConfigId = modelConfigId;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    /**
     * 期望的输出格式
     */
    private OutputFormat outputFormat = OutputFormat.TEXT;
    
    public enum TemplateType {
        RAG, FUNCTION_CALLING, AGENT_DECISION, AGENT_ANSWER, SUMMARY, CUSTOM
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