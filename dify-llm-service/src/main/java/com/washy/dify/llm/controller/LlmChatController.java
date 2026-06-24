package com.washy.dify.llm.controller;

import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.entity.llm.StreamChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.llm.service.LlmService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM对话接口
 */
@Slf4j
@RestController
@RequestMapping("/api/llm")
@Api(tags = "LLM对话接口")
public class LlmChatController {

    @Resource
    private LlmService llmService;

    /**
     * AI对话接口（支持传入模型类型）
     */
    @PostMapping("/chat")
    @ApiOperation("AI对话（支持指定模型）")
    public Result<String> chat(@Valid @RequestBody ChatRequestDTO request) {
        String result = llmService.chat(request);
        return Result.success(result);
    }

    /**
     * 条件对话接口（支持指定模型）
     */
    @PostMapping("/condition/chat")
    @ApiOperation("条件对话（支持指定模型）")
    public Result<Object> conditionChat(@RequestBody ChatRequestDTO request) {
        Object result = llmService.chat(request);
        return Result.success(result);
    }

    /**
     * Function Calling对话（支持指定模型）
     */
    @PostMapping("/chat/function")
    @ApiOperation("Function Calling对话（支持指定模型）")
    public Result<String> chatWithFunction(@RequestBody ChatRequestDTO request) {
        String answer = llmService.chatWithFunction(request);
        return Result.success(answer);
    }

    /**
     * 流式对话（支持指定模型）
     */
    @PostMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("流式对话（支持指定模型）")
    public SseEmitter streamChat(@Valid @RequestBody StreamChatRequestDTO request) {
        return llmService.streamChat(request);
    }

    /**
     * 带配置ID的AI对话接口（供工作流调用）
     */
    @PostMapping("/chat/with-config")
    @ApiOperation("带配置ID的AI对话")
    public Result<String> chatWithConfig(@RequestBody ChatRequestDTO request) {
        String result = llmService.conditionChat(request);
        return Result.success(result);
    }

    /**
     * 新增：清除会话模板缓存（开始新会话时调用）
     */
    @DeleteMapping("/session/{sessionId}/template-cache")
    @ApiOperation("清除会话模板缓存")
    public Result<Map<String, String>> clearSessionTemplate(@PathVariable String sessionId) {
        llmService.clearSessionTemplate(sessionId);
        Map<String, String> result = new HashMap<>();
        result.put("message", "清除成功");
        result.put("sessionId", sessionId);
        return Result.success(result);
    }
}