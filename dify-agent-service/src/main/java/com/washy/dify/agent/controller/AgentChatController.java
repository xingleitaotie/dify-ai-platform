package com.washy.dify.agent.controller;

import com.washy.dify.agent.domain.dto.AgentExecuteRequest;
import com.washy.dify.agent.domain.dto.AgentStreamChatRequest;
import com.washy.dify.agent.service.AgentChatService;
import com.washy.dify.agent.service.AgentStreamChatService;
import com.washy.dify.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentChatService agentChatService;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody AgentExecuteRequest request) {
        return Result.success(agentChatService.chat(request));
    }

    private final AgentStreamChatService agentStreamChatService;

    /**
     * 生产版 Agent 流式对话（记忆 + RAG + 工具调用）
     */
    @PostMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AgentStreamChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);
        agentStreamChatService.streamChat(request, emitter);
        return emitter;
    }
}