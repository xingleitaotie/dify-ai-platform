package com.washy.dify.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.washy.dify.user.entity.ChatMessageEntity;
import com.washy.dify.user.entity.ChatSessionEntity;
import com.washy.dify.user.mapper.ChatMessageMapper;
import com.washy.dify.user.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {
    
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    
    /**
     * 创建或获取会话
     */
    @Transactional
    public ChatSessionEntity getOrCreateSession(String sessionId, Long userId, String title) {
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSessionEntity::getSessionId, sessionId)
               .eq(ChatSessionEntity::getUserId, userId);
        
        ChatSessionEntity session = chatSessionMapper.selectOne(wrapper);
        
        if (session == null) {
            session = new ChatSessionEntity();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setTitle(title != null ? title : "新对话");
            session.setMessageCount(0);
            session.setStatus(1);
            session.setCreateTime(LocalDateTime.now());
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else if (title != null && (session.getTitle() == null || "新对话".equals(session.getTitle()))) {
            session.setTitle(title);
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
        
        return session;
    }
    
    /**
     * 保存消息
     */
    @Transactional
    public void saveMessage(String sessionId, Long userId, String role, String content) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        
        // 更新会话消息数量
        chatSessionMapper.incrementMessageCount(sessionId);
        
        // 更新会话更新时间
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSessionEntity::getSessionId, sessionId);
        ChatSessionEntity session = chatSessionMapper.selectOne(wrapper);
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
    }
    
    /**
     * 获取用户会话列表
     */
    public List<ChatSessionEntity> getUserSessions(Long userId) {
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSessionEntity::getUserId, userId)
               .eq(ChatSessionEntity::getStatus, 1)
               .orderByDesc(ChatSessionEntity::getUpdateTime);
        return chatSessionMapper.selectList(wrapper);
    }
    
    /**
     * 获取会话消息
     */
    public List<ChatMessageEntity> getSessionMessages(String sessionId, Long userId) {
        // 验证会话归属
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSessionEntity::getSessionId, sessionId)
               .eq(ChatSessionEntity::getUserId, userId);
        ChatSessionEntity session = chatSessionMapper.selectOne(wrapper);
        
        if (session == null) {
            throw new RuntimeException("会话不存在或无权限访问");
        }
        
        return chatMessageMapper.getMessagesBySessionId(sessionId);
    }
    
    /**
     * 获取用户对话总数
     */
    public Integer getConversationCount(Long userId) {
        return chatSessionMapper.countByUserId(userId);
    }
    
    /**
     * 删除会话（逻辑删除）
     */
    @Transactional
    public void deleteSession(String sessionId, Long userId) {
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSessionEntity::getSessionId, sessionId)
               .eq(ChatSessionEntity::getUserId, userId);
        
        ChatSessionEntity session = chatSessionMapper.selectOne(wrapper);
        if (session != null) {
            session.setStatus(0);
            chatSessionMapper.updateById(session);
        }
    }
}