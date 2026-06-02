package com.washy.dify.prompt.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 提示词工具类
 */
public class PromptUtils {
    
    /**
     * 构建上下文
     */
    public static Map<String, Object> buildContext(Object... pairs) {
        Map<String, Object> context = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            context.put((String) pairs[i], pairs[i + 1]);
        }
        return context;
    }

    /**
     * 清理提示词中的多余空白
     */
    public static String cleanWhitespace(String prompt) {
        if (prompt == null) {
            return null;
        }
        return prompt.replaceAll("\\n\\s*\\n\\s*\\n", "\n\n")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    /**
     * 估算 Token 数量（粗略）
     */
    public static int estimateTokenCount(String text) {
        if (text == null) {
            return 0;
        }
        // 中文: 1字 ≈ 1.5 tokens，英文: 1词 ≈ 1.3 tokens
        int chineseCount = text.replaceAll("[^\\u4e00-\\u9fa5]", "").length();
        int otherCount = text.length() - chineseCount;
        return (int) (chineseCount * 1.5 + otherCount * 0.3);
    }
}
