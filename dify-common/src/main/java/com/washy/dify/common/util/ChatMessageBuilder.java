package com.washy.dify.common.util;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息构建器 - 将 ChatRequestDTO 转换为标准的 ChatMessage 列表
 */
@Slf4j
@Component
public class ChatMessageBuilder {
    
    /**
     * 从 DTO 构建消息列表
     * 
     * @param requestDTO 请求DTO
     * @param systemPrompt 系统提示词（可选，如果为null则只使用DTO中的内容）
     * @return 标准消息列表
     */
    public List<ChatMessage> buildMessages(ChatRequestDTO requestDTO, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 1. 添加系统提示词（如果提供）
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            messages.add(ChatMessage.system(systemPrompt));
            log.debug("已添加系统提示词，长度: {}", systemPrompt.length());
        }
        
        // 2. 添加历史消息（如果有）
        if (requestDTO.getHistory() != null && !requestDTO.getHistory().isEmpty()) {
            // 过滤掉可能重复的 system 消息
            for (ChatMessage historyMsg : requestDTO.getHistory()) {
                if (!"system".equals(historyMsg.getRole()) || systemPrompt == null) {
                    messages.add(historyMsg);
                }
            }
            log.debug("已添加历史消息，数量: {}", requestDTO.getHistory().size());
        }
        
        // 3. 添加当前用户消息
        if (requestDTO.getMessage() != null && !requestDTO.getMessage().trim().isEmpty()) {
            messages.add(ChatMessage.user(requestDTO.getMessage()));
            log.debug("已添加用户消息: {}", requestDTO.getMessage());
        }
        
        // 4. 验证消息列表不为空
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("消息列表不能为空，请提供至少一条消息");
        }
        
        // 5. 确保最后一条消息是 user 角色（标准格式要求）
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        if (!"user".equals(lastMessage.getRole())) {
            log.warn("最后一条消息不是 user 角色，当前角色: {}", lastMessage.getRole());
            // 可以选择添加一个空的 user 消息或者抛出警告
        }
        
        return messages;
    }
    
    /**
     * 简化版本：只构建当前对话（无历史）
     */
    public List<ChatMessage> buildSimpleMessages(String userMessage, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        
        messages.add(ChatMessage.user(userMessage));
        
        return messages;
    }
    
    /**
     * 从完整的 prompt 文本构建消息（兼容旧的工作流）
     * 当调用方已经封装好完整的 system + user 提示词时使用
     */
    public List<ChatMessage> buildFromCompletePrompt(String completePrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 直接作为 user 消息发送（避免重复封装）
        messages.add(ChatMessage.user(completePrompt));
        
        log.debug("从完整提示词构建消息，长度: {}", completePrompt.length());
        
        return messages;
    }
}