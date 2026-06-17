package com.washy.dify.common.entity.function;

import com.washy.dify.common.entity.llm.ChatMessage;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FunctionChatRequest {
    private List<ChatMessage> messages;  // 这样 Jackson 就能正确反序列化
    private List<Map<String, Object>> tools;
    private String toolChoice;
}