package com.washy.dify.provider.client.chat;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequest;
import com.washy.dify.common.entity.llm.ChatResponse;

import java.util.List;
import java.util.function.Consumer;

/**
 * 大语言模型客户端接口
 */
public interface ChatClient {

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
     * @param messages 消息列表（包含 system/user/assistant 角色）
     * @param temperature 温度参数，可为空
     * @param maxTokens 最大 token 数，可为空
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
     * temperature 可为空
     * maxTokens 可为空
     */
    void chatStream(List<ChatMessage> messages, Double temperature, Integer maxTokens, Consumer<String> consumer);



    /**
     * 测试连接
     */
    boolean testConnection();

}