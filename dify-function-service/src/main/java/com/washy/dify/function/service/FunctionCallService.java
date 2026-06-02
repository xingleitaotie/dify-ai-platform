package com.washy.dify.function.service;


import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;

/**
 * 函数调用服务
 * @author Day7
 */
public interface FunctionCallService {

    /**
     * 执行函数调用
     */
    FunctionExecuteResult callFunction(FunctionCallRequest callDTO);
}