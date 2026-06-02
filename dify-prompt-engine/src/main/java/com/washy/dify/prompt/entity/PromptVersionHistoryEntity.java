package com.washy.dify.prompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("prompt_version_history")
public class PromptVersionHistoryEntity {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    @TableField("template_id")
    private String templateId;
    
    private String version;
    
    @TableField("template_content")
    private String templateContent;
    
    @TableField("change_log")
    private String changeLog;
    
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;
}