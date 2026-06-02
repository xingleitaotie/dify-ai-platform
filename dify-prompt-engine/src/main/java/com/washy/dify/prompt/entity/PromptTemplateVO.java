package com.washy.dify.prompt.entity;

import lombok.Data;
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
    
    @Data
    public static class ModelParamsDTO {
        private Float temperature;
        private Integer maxTokens;
        private Float topP;
        private Float repeatPenalty;
    }
}