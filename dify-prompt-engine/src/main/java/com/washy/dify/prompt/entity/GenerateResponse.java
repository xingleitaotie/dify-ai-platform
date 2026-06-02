package com.washy.dify.prompt.entity;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 提示词生成响应
 */
@Data
public class GenerateResponse {
    
    /**
     * 模板ID
     */
    private String templateId;
    
    /**
     * 模板名称
     */
    private String name;
    
    /**
     * 版本
     */
    private String version;
    
    /**
     * 生成的提示词内容
     */
    private String prompt;
    
    /**
     * 系统提示词
     */
    private String systemPrompt;
    
    /**
     * 用户提示词模板
     */
    private String userPromptTemplate;
    
    /**
     * 推荐参数
     */
    private ModelParamsDTO modelParams;
    
    /**
     * 生成时间
     */
    private Date createdAt;
    
    /**
     * 置信度评分(0-100)
     */
    private Integer confidenceScore;
    
    /**
     * 优化建议
     */
    private List<String> suggestions;
    
    @Data
    public static class ModelParamsDTO {
        private Float temperature;
        private Integer maxTokens;
        private Float topP;
        private Float repeatPenalty;
    }
}