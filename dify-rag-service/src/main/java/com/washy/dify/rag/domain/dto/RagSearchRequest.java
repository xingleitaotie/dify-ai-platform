package com.washy.dify.rag.domain.dto;

import lombok.Data;

@Data
public class RagSearchRequest {
    private String query;
    private String kb;
    private int topK = 0;
    private String configId;
    // getter/setter
}