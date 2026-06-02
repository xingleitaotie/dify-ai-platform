package com.washy.dify.rag.service;

import com.washy.dify.rag.domain.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChunkQualityFilter {

    // 最小有效内容长度
    private static final int MIN_CONTENT_LENGTH = 50;
    
    // 纯标题模式（只有编号+标题，没有实质内容）
    private static final String[] TITLE_ONLY_PATTERNS = {
        "^第[一二三四五六七八九十\\d]+[章节条款].{0,30}$",
        "^[\\d.]+\\s+.{0,30}$",
        "^[一二三四五六七八九十]+[、，。].{0,30}$",
        "^[（(][一二三四五六七八九十\\d]+[）)].{0,30}$",
    };

    /**
     * 过滤低质量块
     */
    public List<DocumentChunk> filterChunks(List<DocumentChunk> chunks) {
        int beforeCount = chunks.size();
        
        List<DocumentChunk> filtered = chunks.stream()
            .filter(this::isValidChunk)
            .collect(Collectors.toList());
        
        int removedCount = beforeCount - filtered.size();
        if (removedCount > 0) {
            log.warn("过滤低质量块: 移除 {} 个，保留 {} 个", removedCount, filtered.size());
        }
        
        return filtered;
    }

    /**
     * 判断块是否有效
     */
    private boolean isValidChunk(DocumentChunk chunk) {
        String content = chunk.getContent();
        
        // 1. 内容为空或过短
        if (content == null || content.trim().length() < MIN_CONTENT_LENGTH) {
            log.debug("移除空块: {}", chunk.getChunkId());
            return false;
        }
        
        // 2. 只有标题没有内容
        if (isTitleOnly(content)) {
            log.debug("移除纯标题块: {} -> {}", chunk.getChunkId(), 
                content.substring(0, Math.min(30, content.length())));
            return false;
        }
        
        // 3. 全是数字或符号
        String textOnly = content.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "");

        // 判断有效文本长度
        if (textOnly.length() < 20) {
            log.debug("移除无意义块: {}", chunk.getChunkId());
            return false;
        }
        
        return true;
    }

    /**
     * 判断是否只有标题
     */
    private boolean isTitleOnly(String content) {
        String trimmed = content.trim();
        for (String pattern : TITLE_ONLY_PATTERNS) {
            if (trimmed.matches(pattern)) {
                return true;
            }
        }
        return false;
    }
}