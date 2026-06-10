package com.washy.dify.provider.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("llm_model_config")
public class ModelConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long providerId;
    private String modelKey;
    private String modelName;
    private String capabilityType;  // chat, embedding, rerank, stt, tts, vision
    private String modelSchema;     // openai, dashscope, ernie, spark, custom
    private String modelParams;     // JSON格式
    private Integer contextLength;
    private Integer dimension;
    private Integer isSystem;
    private Integer sortOrder;
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Double temperature;      // 默认温度参数

    @TableField(exist = false)
    private Integer maxTokens;       // 默认最大token数

    @TableField(exist = false)
    private Integer timeout;         // 超时时间
    
    // 关联字段(非数据库)
    @TableField(exist = false)
    private ProviderEntity provider;
}