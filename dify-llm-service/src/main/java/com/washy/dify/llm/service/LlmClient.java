package com.washy.dify.llm.service;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequest;
import com.washy.dify.common.entity.llm.ChatResponse;
import com.washy.dify.llm.config.LlmProperties;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 统一 LLM 客户端接口
 * 所有模型实现必须遵循此规范
 * @author washy
 * @date 2025/12/19
 */
public interface LlmClient {

    /**
     * 核心接口：使用标准消息列表
     * 调用方完全控制消息内容，不会产生提示词污染
     *
     * @param messages 消息列表（包含 system/user/assistant 角色）
     * @return 模型返回的文本
     */
    String chat(List<ChatMessage> messages);

    /**
     * 带参数的对话接口
     *
     * @param messages 消息列表
     * @param temperature 温度参数
     * @param maxTokens 最大 token 数
     * @return 模型返回的文本
     */
    String chat(List<ChatMessage> messages, Double temperature, Integer maxTokens);

    /**
     * 完整请求对象接口
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式对话
     */
    void chatStream(List<ChatMessage> messages, Consumer<String> consumer);

    /**
     * 带参数的流式对话
     */
    void chatStream(List<ChatMessage> messages, Double temperature, Integer maxTokens, Consumer<String> consumer);

    /**
     * 通用对话接口
     * @param prompt 用户输入
     * @return 模型返回结果
     */
//    String chat(String prompt);

    /**
     * 根据温度生成结果接口
     * @param params 用户输入
     * @return 模型返回结果
     */
    Map<String,Object> chat(Map<String,Object> params);

    /**
     * 带上下文的对话
     */
    String chat(String prompt, String history);

    /**
     * RAG 增强问答
     */
    String ragChat(String prompt, String context);

    /**
     * 流式对话
     * @param messages 对话上下文
     * @param consumer 流式响应回调（逐字接收）
     */
    void streamChat(List<ChatMessage> messages, Consumer<String> consumer);

    /**
     * 设置配置（用于动态客户端）
     */
    default void setLlmProperties(LlmProperties properties) {
        // 默认空实现，子类可覆盖
    }

}