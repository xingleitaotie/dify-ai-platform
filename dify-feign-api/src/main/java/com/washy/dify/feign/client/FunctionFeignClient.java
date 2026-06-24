package com.washy.dify.feign.client;

import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.common.entity.function.FunctionInfo;
import com.washy.dify.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "dify-function-service")
public interface FunctionFeignClient {

    @GetMapping("/api/function/list")
    Result<List<FunctionInfo>> getToolsList();

    @PostMapping("/api/function/call")
    Result<FunctionExecuteResult> invokeFunction(@RequestBody FunctionCallRequest dto);

    // 调用 /tools 接口（返回包装格式）
    @GetMapping("/api/function/tools")
    Result<Map<String, Object>> getTools();

    // 获取函数详情（含参数 schema）
    @GetMapping("/api/function/info/{functionName}")
    Result<FunctionInfo> getFunctionInfo(@PathVariable String functionName);
}