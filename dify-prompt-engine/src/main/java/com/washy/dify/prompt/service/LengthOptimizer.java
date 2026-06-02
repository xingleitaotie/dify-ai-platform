package com.washy.dify.prompt.service;

import lombok.extern.slf4j.Slf4j;

/**
 * 长度优化器 - 压缩过长的提示词
 */
@Slf4j
public class LengthOptimizer implements PromptOptimizer {
    
    private static final int MAX_PROMPT_LENGTH = 4000;
    private static final int TARGET_LENGTH = 3000;
    
    @Override
    public String optimize(String prompt) {
        if (prompt == null || prompt.length() <= MAX_PROMPT_LENGTH) {
            return prompt;
        }
        
        log.warn("提示词过长 ({} 字符), 开始压缩", prompt.length());
        
        // 策略1: 移除多余空白行
        String optimized = prompt.replaceAll("\\n\\s*\\n\\s*\\n", "\n\n");
        
        // 策略2: 压缩示例（只保留1个示例）
        optimized = compressExamples(optimized);
        
        // 策略3: 简化系统提示
        optimized = simplifySystemPrompt(optimized);
        
        // 策略4: 如果仍然过长，智能截断
        if (optimized.length() > MAX_PROMPT_LENGTH) {
            optimized = optimized.substring(0, TARGET_LENGTH) + "\n...[内容已截断]";
        }
        
        log.info("提示词压缩完成: {} -> {} 字符", prompt.length(), optimized.length());
        return optimized;
    }
    
    private String compressExamples(String prompt) {
        // 简单实现：移除多余示例，只保留第一个
        String[] parts = prompt.split("【示例】");
        if (parts.length <= 2) {
            return prompt;
        }
        return parts[0] + "【示例】" + parts[1];
    }
    
    private String simplifySystemPrompt(String prompt) {
        // 移除冗余的说明文字
        return prompt.replaceAll("请严格遵守以下规则：", "规则：")
                     .replaceAll("非常重要|务必|一定", "");
    }
}