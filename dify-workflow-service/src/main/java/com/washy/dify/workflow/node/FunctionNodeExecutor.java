package com.washy.dify.workflow.node;

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

        String funcName = (String) config.get("funcName");
        Object paramsObj = resolver.resolveObject(config.get("params"), context);

        log.info("函数节点执行: {}, 参数: {}", funcName, paramsObj);

        Map<String, Object> output = new HashMap<>();
        output.put("funcName", funcName);
        output.put("params", paramsObj);
        output.put("result", "success");

        return output;
    }
}