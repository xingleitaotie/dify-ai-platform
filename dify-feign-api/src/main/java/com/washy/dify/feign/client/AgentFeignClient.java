package com.washy.dify.feign.client;


import com.washy.dify.common.entity.agent.AgentExecuteRequest;
import com.washy.dify.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "dify-agent-service")
public interface AgentFeignClient {

    @PostMapping("/api/agent/chat")
    Result<String> chat(@RequestBody AgentExecuteRequest request);

}
