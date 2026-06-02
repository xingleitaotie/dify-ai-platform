package com.washy.dify.llm.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

// dify-llm-service/src/main/java/com/washy/dify/llm/entity/LlmConfig.java
@Data
@TableName("llm_config")
public class LlmConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String configKey;      // 配置键
    private String configValue;    // 配置值
    private String description;    // 描述
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}