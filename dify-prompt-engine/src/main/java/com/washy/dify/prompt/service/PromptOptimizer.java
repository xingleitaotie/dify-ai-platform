package com.washy.dify.prompt.service;

/**
 * 提示词优化器接口
 */
public interface PromptOptimizer {
    
    /**
     * 优化提示词
     * @param prompt 原始提示词
     * @return 优化后的提示词
     */
    String optimize(String prompt);
}