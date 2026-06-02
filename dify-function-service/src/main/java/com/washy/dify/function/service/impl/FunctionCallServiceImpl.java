package com.washy.dify.function.service.impl;

import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.function.executor.FunctionExecutor;
import com.washy.dify.function.service.FunctionCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 函数调用服务实现
 * @author Day7
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FunctionCallServiceImpl implements FunctionCallService {

    private final FunctionExecutor functionExecutor;


    @Override
    public FunctionExecuteResult callFunction(FunctionCallRequest callDTO) {
        // 直接调用执行器执行
        return functionExecutor.execute(callDTO);
    }
}