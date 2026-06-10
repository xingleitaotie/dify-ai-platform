package com.washy.dify.provider.controller;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.result.Result;
import com.washy.dify.provider.service.UnifiedChatService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 统一对话接口 - 对外屏蔽模型配置细节
 */
@RestController
@RequestMapping("/api/provider/unified/chat")
@Api(tags = "统一对话接口")
@Slf4j
@RequiredArgsConstructor
public class UnifiedChatController {

    private final UnifiedChatService unifiedChatService;

    /**
     * 同步对话 - 自动使用系统配置的Chat模型
     */
    @PostMapping("/sync")
    @ApiOperation("同步对话（自动使用系统配置的模型）")
    public Result<String> syncChat(@RequestBody Map<String, Object> request) {
        List<ChatMessage> messages = (List<ChatMessage>) request.get("messages");
        String result = unifiedChatService.chat(messages);
        return Result.success(result);
    }

    /**
     * 流式对话 - 自动使用系统配置的Chat模型
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("流式对话（自动使用系统配置的模型）")
    public SseEmitter streamChat(@RequestBody Map<String, Object> request) {
        List<ChatMessage> messages = (List<ChatMessage>) request.get("messages");
        return unifiedChatService.chatStream(messages);
    }
}