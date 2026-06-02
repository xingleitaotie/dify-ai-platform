package com.washy.dify.llm.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * RAG问答请求体
 */
@Data
public class RagQaQueryDTO {
    // 用户问题
    @NotBlank(message = "用户提示词不能为空")
    private String query;
    // 检索返回条数
    private Integer topK = 3;
}