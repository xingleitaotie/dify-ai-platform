package com.washy.dify.rag.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    private String chunkId;              // 块ID
    private String documentId;           // 文档ID
    private String sectionNumber = "0";        // 所属章节编号
    private String sectionTitle;         // 所属章节标题
    private Integer sectionLevel;        // 所属章节级别
    private String content;              // 文本内容(含图片路径标记)
    private List<ImageInfo> images;      // 包含的图片
    private List<TableInfo> tables;      // 包含的表格
    private Map<String, Object> metadata; // 元数据
    private float[] vector;              // 向量(预留)
}