package com.washy.dify.rag.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageInfo {
    private String imageId;              // UUID
    private String localPath;            // 本地存储路径
    private String objectStoragePath;    // 对象存储路径(预留)
    private String originalName;         // 原始文件名
    private String format;               // 格式(png/jpeg)
    private long size;                   // 文件大小(字节)
    private int width;                   // 宽度
    private int height;                  // 高度
    private String placeholder;          // 文档中的占位符
    private LocalDateTime createdAt;     // 创建时间

}