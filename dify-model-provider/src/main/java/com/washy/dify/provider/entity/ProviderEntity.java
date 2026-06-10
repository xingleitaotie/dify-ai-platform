package com.washy.dify.provider.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("llm_provider")
public class ProviderEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String providerKey;
    private String providerName;
    private String baseUrl;
    private String apiKey;
    private String secret;
    private String description;
    private Integer sortOrder;
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}