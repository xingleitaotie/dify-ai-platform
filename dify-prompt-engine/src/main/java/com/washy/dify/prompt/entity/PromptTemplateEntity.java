package com.washy.dify.prompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("prompt_template")
public class PromptTemplateEntity {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String name;
    
    private String version;
    
    private String description;
    
    @TableField("template_content")
    private String templateContent;
    
    @TableField("system_prompt")
    private String systemPrompt;
    
    @TableField("user_prompt_template")
    private String userPromptTemplate;
    
    private BigDecimal temperature;
    
    @TableField("max_tokens")
    private Integer maxTokens;
    
    @TableField("top_p")
    private BigDecimal topP;
    
    @TableField("repeat_penalty")
    private BigDecimal repeatPenalty;
    
    private Boolean streaming;
    
    private String status;
    
    private String type;
    
    private String category;
    
    private String tags;
    
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;
    
    @TableField(value = "updated_by", fill = FieldFill.UPDATE)
    private String updatedBy;
    
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
    
    @TableField("use_count")
    private Integer useCount;
    
    private Integer rating;

    @TableField("vector_store_name")
    private String vectorStoreName;
}