package com.washy.dify.prompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("prompt_usage_log")
public class PromptUsageLogEntity {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    @TableField("template_id")
    private String templateId;
    
    @TableField("template_name")
    private String templateName;
    
    @TableField("user_input")
    private String userInput;
    
    @TableField("generated_prompt")
    private String generatedPrompt;
    
    @TableField("generation_method")
    private String generationMethod;
    
    @TableField("response_time_ms")
    private Integer responseTimeMs;
    
    @TableField("confidence_score")
    private Integer confidenceScore;
    
    @TableField("user_ip")
    private String userIp;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;
}