package com.washy.dify.workflow.node;

import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.FunctionFeignClient;
import com.washy.dify.workflow.config.WorkflowContext;
import com.washy.dify.workflow.util.WorkflowVariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionNodeExecutor implements NodeExecutor {

    private final WorkflowVariableResolver resolver;
    private final FunctionFeignClient functionFeignClient;

    @Override
    public String getNodeType() {
        return "FUNCTION";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        Map<String, Object> config = (Map<String, Object>) nodeInput.get("config");
        if (config == null) {
            config = new HashMap<>();
        }

        // 1. 获取函数名（字段名：functionName）
        String functionName = (String) config.get("functionName");
        if (functionName == null || functionName.isEmpty()) {
            throw new RuntimeException("函数节点缺少 functionName 配置");
        }

        // 2. 获取参数（字段名：parameters），并解析其中的变量
        Object paramsObj = resolver.resolveObject(config.get("parameters"), context);
        log.info("函数节点执行: functionName={}, params={}", functionName, paramsObj);

        // 3. 构造 Feign 调用请求
        FunctionCallRequest request = new FunctionCallRequest();
        request.setFunctionName(functionName);
        request.setParameters(paramsObj);

        // 4. 调用函数服务
        Result<FunctionExecuteResult> result = functionFeignClient.invokeFunction(request);
        if (result == null || result.getCode() != 200) {
            throw new RuntimeException("函数调用失败: " + (result != null ? result.getMsg() : "服务不可用"));
        }

        FunctionExecuteResult executeResult = result.getData();
        if (executeResult == null) {
            throw new RuntimeException("函数执行返回为空");
        }

        // 5. 构建输出（与前端约定的输出结构一致）
        Map<String, Object> output = new HashMap<>();
        output.put("functionName", functionName);
        output.put("params", paramsObj);
        // 从执行结果中提取主要返回值
        output.put("result", executeResult.getData());

        log.info("函数节点执行完成: {}", output);
        return output;
    }
}