package com.washy.dify.provider.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("llm_system_capability")
public class SystemCapabilityEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String capabilityType;
    private Long modelConfigId;
    private Long fallbackConfigId;
    private String configParams;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    // 关联字段
    @TableField(exist = false)
    private ModelConfigEntity modelConfig;
    
    @TableField(exist = false)
    private ModelConfigEntity fallbackConfig;
}