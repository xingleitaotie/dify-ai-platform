package com.washy.dify.common.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 向量化请求/响应实体
 */
@Data
@NoArgsConstructor       // 🔥 必须加
@AllArgsConstructor
public class EmbeddingDTO {
    private String text;
    private List<String> textChunks;
    private Integer topN = 3;
}