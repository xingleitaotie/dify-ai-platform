package com.washy.dify.llm.controller;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.entity.llm.StreamChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.llm.factory.LlmClientFactory;
import com.washy.dify.llm.service.LlmClient;
import com.washy.dify.llm.service.LlmService;
import com.washy.dify.llm.service.RagQaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

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

    @Resource
    private RagQaService ragQaService;

    @Resource
    private LlmClientFactory llmClientFactory;

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
        Object result = llmService.conditionChat(request);
        return Result.success(result);
    }

    /**
     * RAG问答接口（支持指定模型）
     */
    @PostMapping("/rag-qa/chat")
    @ApiOperation("RAG问答（支持指定模型）")
    public Result<String> ragChat(@Valid @RequestBody ChatRequestDTO request) {
        String answer = ragQaService.ragQa(request);
        return Result.success(answer);
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
        Long configId = null;

        if (request.getConfigId() != null) {
            Object configIdObj = request.getConfigId();

            if (configIdObj instanceof Integer) {
                configId = ((Integer) configIdObj).longValue();
            } else if (configIdObj instanceof Long) {
                configId = (Long) configIdObj;
            } else if (configIdObj instanceof String) {
                // 处理字符串类型
                try {
                    configId = Long.parseLong((String) configIdObj);
                } catch (NumberFormatException e) {
                    log.warn("解析configId失败: {}", configIdObj);
                }
            } else if (configIdObj instanceof Number) {
                configId = ((Number) configIdObj).longValue();
            }
        }
        List<ChatMessage> messages = null;
        if (request.getMessages() != null) {
            messages = request.getMessages();
            log.info("带配置ID的LLM对话请求 - configId: {}, messages的消息长度: {}", configId, request.getMessages().size());
        }

        if (configId != null) {
            // 使用指定的配置
            LlmClient client = llmClientFactory.getLlmClientByConfigId(configId);
            String result = client.chat(messages);
            return Result.success(result);
        } else {
            // 使用默认配置
            String result = llmService.chat(request);
            return Result.success(result);
        }
    }

    /**
     * 测试模型连接
     */
    @PostMapping("/test")
    @ApiOperation("测试模型连接")
    public Result<Boolean> testModel(@RequestBody ChatRequestDTO request) {
        boolean result = llmService.testModel(request);
        return Result.success(result);
    }
}