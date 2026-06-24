package com.washy.dify.provider.controller;

import com.washy.dify.common.entity.function.FunctionChatRequest;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.provider.service.UnifiedChatService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
     * 传入模型配置参数查询
     */
    @PostMapping("/condition")
    @ApiOperation("传入模型配置参数查询（未传入使用系统配置的模型）")
    public Result<String> conditionChat(@RequestBody ChatRequestDTO request) {
        String result = unifiedChatService.conditionChat(request);
        return Result.success(result);
    }

    /**
     * 函数调用（支持 OpenAI 标准格式）
     */
    @PostMapping("/function")
    @ApiOperation("函数调用对话（支持 tools 参数）")
    public Result<String> functionChat(@RequestBody FunctionChatRequest request) {
        // 提取参数
        List<ChatMessage> messages = request.getMessages();
        List<Map<String, Object>> tools = request.getTools();
        String toolChoice = request.getToolChoice();

        // 调用支持工具的服务
        String result = unifiedChatService.chatWithTools(messages, tools, toolChoice);
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