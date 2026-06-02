package com.washy.dify.rag.domain.dto;

import com.washy.dify.rag.domain.DocumentChunk;
import com.washy.dify.rag.domain.ImageInfo;
import com.washy.dify.rag.domain.TableInfo;
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
public class ChunkResult {
    private boolean success;
    private String documentId;
    private String fileName;
    private int sectionCount;
    private int chunkCount;
    private int imageCount;
    private int tableCount;
    private int storedCount;
    private String error;
    private List<DocumentChunk> chunks;
    private Map<String, ImageInfo> images;
    private List<TableInfo> tables;

    public static ChunkResult success(String docId, String fileName, 
                                       List<DocumentChunk> chunks,
                                       Map<String, ImageInfo> images,
                                       List<TableInfo> tables,
                                       int stored) {
        return ChunkResult.builder()
                .success(true)
                .documentId(docId)
                .fileName(fileName)
                .sectionCount(chunks.size())
                .chunkCount(chunks.size())
                .imageCount(images.size())
                .tableCount(tables.size())
                .storedCount(stored)
                .chunks(chunks)
                .images(images)
                .tables(tables)
                .build();
    }

    public static ChunkResult fail(String error) {
        return ChunkResult.builder()
                .success(false)
                .error(error)
                .build();
    }
}