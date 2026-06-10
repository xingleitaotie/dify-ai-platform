package com.washy.dify.common.entity.prompt;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 提示词模板VO
 */
@Data
public class PromptTemplateVO {
    
    private String id;
    private String name;
    private String version;
    private String description;
    private String template;
    private ModelParamsDTO modelParams;
    private Boolean streaming;
    private String status;  // DRAFT, ACTIVE, ARCHIVED
    private Date createdAt;
    private Date updatedAt;
    private String createdBy;
    private String type;
    private String systemPrompt;
    private String userPromptTemplate;
    private String category;  // 新增：分类
    private String tags;      // 新增：标签

    private BigDecimal temperature;    // 温度参数
    private Integer maxTokens;         // 最大输出token数
    private BigDecimal topP;           // Top P参数
    private BigDecimal repeatPenalty;  // 重复惩罚
    private Integer useCount;          // 使用次数
    private Integer rating;            // 评分
    private String vectorStoreName;
    
    @Data
    public static class ModelParamsDTO {
        private Float temperature;
        private Integer maxTokens;
        private Float topP;
        private Float repeatPenalty;
    }
}