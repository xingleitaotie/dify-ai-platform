package com.washy.dify.agent.service;

import com.washy.dify.agent.domain.AgentToolBind;
import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.common.exception.GlobalExceptionHandler;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.FunctionFeignClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class AgentExecuteService {

    @Resource
    private AgentToolBindService agentToolBindService;

    @Resource
    private FunctionFeignClient functionFeignClient;

    // 执行Agent的所有工具
    public Object executeAgent(Long agentId, Object params) {
        // 1. 获取该Agent绑定的工具
        List<AgentToolBind> tools = agentToolBindService.lambdaQuery()
                .eq(AgentToolBind::getAgentId, agentId)
                .list();

        // 2. 调用 function-service 执行工具
        for (AgentToolBind tool : tools) {
            // 解析函数调用参数
            FunctionCallRequest functionRequest = parseFunctionCall(tool,params);
            // 执行函数
            Result<FunctionExecuteResult> result = functionFeignClient.invokeFunction(functionRequest);

            return result.getData();
        }
        return "无可用工具";
    }

    /**
     * 解析大模型返回的 JSON，转为 FunctionRequest
     */
    private FunctionCallRequest parseFunctionCall(AgentToolBind tool,Object params) {
        try {
            FunctionCallRequest request = new FunctionCallRequest();
            request.setFunctionName(tool.getToolName());
            request.setParameters(params);
            return request;
        } catch (Exception e) {
            throw new GlobalExceptionHandler("解析函数调用失败：" + e.getMessage());
        }
    }
}