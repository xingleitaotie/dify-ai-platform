package com.washy.dify.rag.util;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求转换工具
 */
@Component
public class ChatRequestConverter {
    
    /**
     * 将 prompt 字符串转换为 ChatRequestDTO
     */
    public ChatRequestDTO toRequest(String prompt, Double temperature, Long configId) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(prompt));
        
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessages(messages);
        Map<String,Object> params = new HashMap<>();
        params.put("temperature", temperature);
        request.setParams(params);
        request.setConfigId(configId);
        
        return request;
    }
    
    /**
     * 一次 LLM 调用完成摘要、关键词、分类带 system prompt 的转换
     */
    public ChatRequestDTO toRequest(String systemPrompt, String userPrompt, Double temperature, Long configId) {
        List<ChatMessage> messages = new ArrayList<>();
        
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        messages.add(ChatMessage.user(userPrompt));
        
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessages(messages);
        Map<String,Object> params = new HashMap<>();
        params.put("temperature", temperature);
        request.setParams(params);
        request.setConfigId(configId);
        
        return request;
    }
    
    /**
     * 从完整的 prompt 构建（工作流模式）
     */
    public ChatRequestDTO toWorkflowRequest(String completePrompt, Double temperature, Long configId) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(completePrompt));

        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessages(messages);
        Map<String,Object> params = new HashMap<>();
        params.put("temperature", temperature);
        request.setParams(params);
        request.setConfigId(configId);
        return request;
    }
}