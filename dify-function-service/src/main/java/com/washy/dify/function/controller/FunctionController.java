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

import java.util.List;
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
}