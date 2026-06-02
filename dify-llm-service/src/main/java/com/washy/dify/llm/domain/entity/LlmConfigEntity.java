package com.washy.dify.llm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("llm_config")
public class LlmConfigEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String configName;
    
    private String type;
    
    private String modelName;
    
    private String baseUrl;
    
    private String apiKey;
    
    private String secret;
    
    private Integer maxTokens;
    
    private BigDecimal temperature;
    
    private Integer timeout;
    
    private Integer isDefault;
    
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}