package com.washy.dify.user.controller;

import com.washy.dify.common.result.Result;
import com.washy.dify.user.config.JwtUtil;
import com.washy.dify.user.entity.ChatMessageEntity;
import com.washy.dify.user.entity.ChatSessionEntity;
import com.washy.dify.user.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;

    /**
     * 获取用户会话列表
     */
    @GetMapping("/sessions")
    public Result<List<ChatSessionEntity>> getSessions(@RequestHeader("token") String token) {
        Long userId = JwtUtil.getUserId(token);
        List<ChatSessionEntity> sessions = chatSessionService.getUserSessions(userId);
        return Result.success(sessions);
    }

    /**
     * 获取会话消息
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageEntity>> getMessages(
            @PathVariable String sessionId,
            @RequestHeader("token") String token) {
        Long userId = JwtUtil.getUserId(token);
        List<ChatMessageEntity> messages = chatSessionService.getSessionMessages(sessionId, userId);
        return Result.success(messages);
    }

    /**
     * 创建或更新会话
     */
    @PostMapping("/sessions")
    public Result<ChatSessionEntity> createOrUpdateSession(
            @RequestBody Map<String, String> params,
            @RequestHeader("token") String token) {
        Long userId = JwtUtil.getUserId(token);
        String sessionId = params.get("sessionId");
        String title = params.get("title");

        ChatSessionEntity session = chatSessionService.getOrCreateSession(sessionId, userId, title);
        return Result.success(session);
    }

    /**
     * 保存消息
     */
    @PostMapping("/messages")
    public Result<Void> saveMessage(
            @RequestBody Map<String, Object> params,
            @RequestHeader("token") String token) {
        Long userId = JwtUtil.getUserId(token);
        String sessionId = (String) params.get("sessionId");
        String role = (String) params.get("role");
        String content = (String) params.get("content");

        chatSessionService.saveMessage(sessionId, userId, role, content);
        return Result.success();
    }

    /**
     * 获取对话统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(@RequestHeader("token") String token) {
        Long userId = JwtUtil.getUserId(token);
        Integer conversationCount = chatSessionService.getConversationCount(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("conversationCount", conversationCount);
        return Result.success(stats);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(
            @PathVariable String sessionId,
            @RequestHeader("token") String token) {
        Long userId = JwtUtil.getUserId(token);
        chatSessionService.deleteSession(sessionId, userId);
        return Result.success();
    }
}