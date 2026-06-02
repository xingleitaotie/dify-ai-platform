package com.washy.dify.feign.client;

import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "dify-llm-service")
public interface LlmFeignClient {

    /**
     * 普通对话
     */
    @PostMapping("/api/llm/chat")
    Result<String> chat(@RequestBody ChatRequestDTO request);

    /**
     * 函数决策对话
     */
    @PostMapping("/api/llm/chat/function")
    Result<String> functionChat(@RequestBody ChatRequestDTO params);
    /**
     * 带配置ID的AI对话接口
     */
    @PostMapping("/api/llm/chat/with-config")
    Result<String> chatWithConfig(@RequestBody ChatRequestDTO request);
}