package com.washy.dify.prompt.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型参数配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelParams {

    /**
     * 温度参数 (0.0-1.0)，越低越稳定
     */
    @Builder.Default
    private Float temperature = 0.3f;

    /**
     * 最大输出 tokens
     */
    @Builder.Default
    private Integer maxTokens = 2048;

    /**
     * Top P 采样
     */
    @Builder.Default
    private Float topP = 0.9f;

    /**
     * Top K 采样
     */
    @Builder.Default
    private Integer topK = 40;

    /**
     * 重复惩罚
     */
    @Builder.Default
    private Float repeatPenalty = 1.1f;

    /**
     * 停止词
     */
    @Builder.Default
    private String[] stop = {"<|im_end|>", "\n\n"};

    /**
     * 是否流式输出
     */
    @Builder.Default
    private Boolean stream = false;

    /**
     * 预设场景配置
     */
    public static ModelParams forFunctionCalling() {
        return ModelParams.builder()
            .temperature(0.1f)      // FC 必须低温度
            .maxTokens(512)
            .topP(0.9f)
            .repeatPenalty(1.15f)
            .build();
    }

    public static ModelParams forRAG() {
        return ModelParams.builder()
            .temperature(0.3f)
            .maxTokens(1024)
            .topP(0.9f)
            .repeatPenalty(1.1f)
            .build();
    }

    public static ModelParams forSummary() {
        return ModelParams.builder()
            .temperature(0.3f)
            .maxTokens(150)         // 50字 ≈ 80 tokens
            .topP(0.9f)
            .repeatPenalty(1.1f)
            .build();
    }

    public static ModelParams forChat() {
        return ModelParams.builder()
            .temperature(0.7f)
            .maxTokens(2048)
            .topP(0.9f)
            .repeatPenalty(1.05f)
            .build();
    }

    public static ModelParams forAgentDecision() {
        return ModelParams.builder()
            .temperature(0.2f)      // 决策需要稳定
            .maxTokens(512)
            .topP(0.9f)
            .repeatPenalty(1.1f)
            .build();
    }
}