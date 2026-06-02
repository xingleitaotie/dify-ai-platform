package com.washy.dify.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.common.entity.llm.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentRedisMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;

    public static final String PREFIX = "agent:memory:";
    public static final long EXPIRE_MINUTES = 30;


    // 保存消息
    public void saveMessage(String sessionId, ChatMessage message) {
        try {
             ObjectMapper objectMapper = new ObjectMapper();
            // 关键：转成 JSON 字符串再存！！！
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush("chat:memory:" + sessionId, json);
        } catch (Exception e) {
            throw new RuntimeException("Redis 消息序列化失败", e);
        }
    }

    // 获取最近 N 条（修复：带 limit）
    public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
        List<Object> list = redisTemplate.opsForList()
                .range(PREFIX + sessionId, -limit, -1);

        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        return list.stream()
                .map(obj -> (ChatMessage) obj)
                .collect(Collectors.toList());
    }

    // 获取一个会话的全部消息
    public List<ChatMessage> getAllMessages(String sessionId) {
        List<Object> list = redisTemplate.opsForList()
                .range(PREFIX + sessionId, 0, -1);

        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        return list.stream()
                .map(obj -> (ChatMessage) obj)
                .collect(Collectors.toList());
    }

    // 获取所有会话 key（用于定时同步）
    public Set<String> getAllSessionKeys() {
        return redisTemplate.keys(PREFIX + "*");
    }

    // 删除会话
    public void deleteSession(String sessionId) {
        redisTemplate.delete(PREFIX + sessionId);
    }
}