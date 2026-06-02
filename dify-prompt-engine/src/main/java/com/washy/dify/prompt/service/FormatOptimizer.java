package com.washy.dify.prompt.service;

import java.util.regex.Pattern;

/**
 * 格式优化器 - 优化提示词格式
 */
public class FormatOptimizer implements PromptOptimizer {
    
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("[ ]{2,}");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");
    
    @Override
    public String optimize(String prompt) {
        if (prompt == null) {
            return null;
        }
        
        String optimized = prompt;
        
        // 1. 移除多余空格
        optimized = MULTIPLE_SPACES.matcher(optimized).replaceAll(" ");
        
        // 2. 规范换行（最多2个连续换行）
        optimized = MULTIPLE_NEWLINES.matcher(optimized).replaceAll("\n\n");
        
        // 3. 确保有合适的结束标记
        if (!optimized.endsWith("\n<|im_end|>") && !optimized.endsWith("\n<|im_start|>assistant")) {
            if (!optimized.endsWith("\n")) {
                optimized += "\n";
            }
            optimized += "<|im_end|>\n<|im_start|>assistant\n";
        }
        
        // 4. 去除首尾空白
        optimized = optimized.trim();
        
        return optimized;
    }
}