package com.washy.dify.function.controller;

import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.common.entity.function.FunctionInfo;
import com.washy.dify.common.result.Result;
import com.washy.dify.function.service.FunctionCallService;
import com.washy.dify.function.service.FunctionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/function")
@RequiredArgsConstructor
public class FunctionController {

    private final FunctionRegistry functionRegistry;
    private final FunctionCallService functionCallService;

    /**
     * 获取所有AI函数列表
     */
    @GetMapping("/list")
    public Result<List<FunctionInfo>> list() {
        log.info("接收请求：获取AI函数列表");
        return Result.success(functionRegistry.getFunctionList());
    }

    /**
     * 执行AI函数调用
     */
    @PostMapping("/call")
    public Result<FunctionExecuteResult> callFunction(@RequestBody FunctionCallRequest callDTO) {
        log.info("接收请求：执行AI函数调用，函数名：{}", callDTO.getFunctionName());
        FunctionExecuteResult result = functionCallService.callFunction(callDTO);
        return Result.success(result);
    }

    /**
     * 获取大模型 Function Calling 格式的函数列表
     * 这是给大模型使用的标准格式
     */
    @GetMapping("/tools")
    public Result<Map<String, Object>> getTools() {
        log.info("接收请求：获取大模型工具列表");

        Map<String, Object> response = new HashMap<>();
        response.put("tools", functionRegistry.getFunctionListForLLM());

        // 可选：返回工具调用说明
        response.put("tool_choice", "auto");  // auto: 自动选择, none: 不调用, required: 必须调用

        return Result.success(response);
    }
}