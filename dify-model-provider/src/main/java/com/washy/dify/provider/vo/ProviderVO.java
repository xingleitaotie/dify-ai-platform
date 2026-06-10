package com.washy.dify.provider.vo;

import lombok.Data;
import java.util.List;

@Data
public class ProviderVO {
    private Long id;
    private String providerKey;
    private String providerName;
    private String baseUrl;
    private String apiKey;
    private String secret;
    private Integer status;
    private List<ModelConfigVO> models;
}