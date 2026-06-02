package com.washy.dify.common.entity.rag;

import lombok.Data;

import java.util.Map;

/**
 * RAG检索返回结果
 */
@Data
public class RagRetrieveVO {
    private String document;  // 文档内容
    private Double distance;  // 距离分数
    private Map<String, Object> metadata; //元数据
}