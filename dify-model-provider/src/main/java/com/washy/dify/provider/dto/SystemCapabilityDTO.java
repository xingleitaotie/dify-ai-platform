package com.washy.dify.provider.dto;

import lombok.Data;

@Data
public class SystemCapabilityDTO {
    private String capabilityType;
    private Long modelConfigId;
    private Long fallbackConfigId;
    
    // 扩展信息
    private String capabilityName;
    private String modelName;
    private String providerName;
}