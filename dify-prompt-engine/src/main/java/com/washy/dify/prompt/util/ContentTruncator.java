package com.washy.dify.prompt.util;

/**
 * 内容截断工具
 */
public class ContentTruncator {
    
    private static final int DEFAULT_MAX_LENGTH = 800;
    
    /**
     * 截断内容到指定长度
     * @param content 原始内容
     * @param maxLength 最大长度
     * @return 截断后的内容
     */
    public static String truncate(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
    
    /**
     * 智能截断（在句子边界截断）
     */
    public static String smartTruncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        
        String truncated = content.substring(0, maxLength);
        int lastPeriod = Math.max(
            truncated.lastIndexOf('。'),
            Math.max(truncated.lastIndexOf('！'), truncated.lastIndexOf('？'))
        );
        
        if (lastPeriod > maxLength / 2) {
            return truncated.substring(0, lastPeriod + 1);
        }
        
        return truncated + "...";
    }
}