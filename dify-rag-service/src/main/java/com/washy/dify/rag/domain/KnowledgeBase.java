package com.washy.dify.rag.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgeBase {
    private String id;           // 集合ID
    private String name;         // 知识库名称
    private String description;  // 描述
    private Integer chunkCount;  // 分块数量
    private Integer docCount;    // 文档数量
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}