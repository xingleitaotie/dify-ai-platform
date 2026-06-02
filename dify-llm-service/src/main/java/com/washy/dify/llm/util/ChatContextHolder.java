package com.washy.dify.llm.util;

import com.washy.dify.common.entity.llm.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话上下文持有者（内存缓存上下文）
 * @author washby
 * @date 2025-12-19
 */
@Component
public class ChatContextHolder {

    /**
     * 会话上下文缓存 key:sessionId value:消息列表
     */
    private static final Map<String, List<ChatMessage>> CONTEXT_CACHE = new ConcurrentHashMap<>();

    /**
     * 单轮对话最大保留消息数
     */
    private static final int MAX_MESSAGE_SIZE = 20;

    /**
     * 获取会话上下文
     */
    public List<ChatMessage> getContext(String sessionId) {
        return CONTEXT_CACHE.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * 保存消息到上下文
     */
    public void saveMessage(String sessionId, ChatMessage message) {
        List<ChatMessage> messages = CONTEXT_CACHE.computeIfAbsent(sessionId, k -> new ArrayList<>());
        messages.add(message);
        
        // 超过最大长度，移除最早的消息
        if (messages.size() > MAX_MESSAGE_SIZE) {
            messages.remove(0);
        }
    }

    /**
     * 清空会话上下文
     */
    public void clearContext(String sessionId) {
        CONTEXT_CACHE.remove(sessionId);
    }
}