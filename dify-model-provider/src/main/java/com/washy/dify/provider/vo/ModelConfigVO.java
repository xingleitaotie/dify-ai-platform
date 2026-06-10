package com.washy.dify.provider.vo;

import lombok.Data;

@Data
public class ModelConfigVO {
    private Long id;
    private Long providerId;
    private String modelKey;
    private String modelName;
    private String capabilityType;
    private String modelSchema;
    private Integer contextLength;
    private Integer dimension;
    private Integer status;
    private Boolean isUsed;  // 是否被系统使用
    private String usedBy;   // 被哪个能力使用
}