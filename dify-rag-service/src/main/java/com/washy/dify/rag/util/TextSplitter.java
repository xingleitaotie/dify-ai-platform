package com.washy.dify.rag.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块工具（滑动窗口算法）
 * Day2 核心代码
 */
public class TextSplitter {

    /**
     * 文本分块（滑动窗口）
     * @param text 原始文本
     * @param chunkSize 单块最大字符数
     * @param overlapSize 重叠字符数
     * @return 分块后的文本列表
     */
    public static List<String> split(String text, int chunkSize, int overlapSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 清理多余空白、换行，让分块更干净
        text = text.replaceAll("\\s+", " ").trim();

        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String chunk = text.substring(start, end).trim();

            // 过滤空块
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // 滑动步长
            start += (chunkSize - overlapSize);
        }

        //  过滤最后一块过小的碎片（<50字符丢弃，可自己调）
        List<String> result = new ArrayList<>();
        for (String c : chunks) {
            if (c.length() >= 10) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * RAG 推荐默认分块规则
     * 1024 字符 / 128 重叠
     */
    public static List<String> defaultSplit(String text) {
        return split(text, 1024, 128);
    }
}