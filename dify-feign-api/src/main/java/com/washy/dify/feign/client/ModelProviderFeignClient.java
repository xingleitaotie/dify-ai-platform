// ModelProviderFeignClient.java - 在 dify-llm-service 中添加
package com.washy.dify.feign.client;

import com.washy.dify.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "dify-model-provider")
public interface ModelProviderFeignClient {

    /**
     * 统一同步对话 - 自动使用系统配置的模型
     */
    @PostMapping("/api/provider/unified/chat/sync")
    Result<String> unifiedSyncChat(@RequestBody Map<String, Object> request);

    /**
     * 统一流式对话 - 自动使用系统配置的模型
     */
    @PostMapping(value = "/api/provider/unified/chat/stream", consumes = "application/json")
    String unifiedStreamChat(@RequestBody Map<String, Object> request);

}