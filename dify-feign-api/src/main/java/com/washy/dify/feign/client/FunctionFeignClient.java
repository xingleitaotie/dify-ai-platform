package com.washy.dify.feign.client;

import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.common.entity.function.FunctionInfo;
import com.washy.dify.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "dify-function-service")
public interface FunctionFeignClient {

    @GetMapping("/api/function/list")
    Result<List<FunctionInfo>> getFunctionList();

    @PostMapping("/api/function/call")
    Result<FunctionExecuteResult> invokeFunction(@RequestBody FunctionCallRequest dto);
}