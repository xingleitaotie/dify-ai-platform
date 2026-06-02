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
public class ConditionNodeExecutor implements NodeExecutor {

    private final WorkflowVariableResolver resolver;

    @Override
    public String getNodeType() {
        return "CONDITION";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        Map<String, Object> config = (Map<String, Object>) nodeInput.get("config");
        if (config == null) {
            throw new RuntimeException("条件节点配置不能为空");
        }

        String expression = (String) config.get("expression");
        if (expression == null || expression.isEmpty()) {
            throw new RuntimeException("条件表达式不能为空");
        }

        // 变量解析
        Object resolved = resolver.resolve(expression, context);
        boolean result = false;

        if (resolved instanceof Boolean) {
            result = (Boolean) resolved;
        } else if (resolved != null) {
            result = Boolean.parseBoolean(resolved.toString());
        }

        log.info("条件节点执行结果: {} → {}", expression, result);

        Map<String, Object> output = new HashMap<>();
        output.put("conditionResult", result);
        context.setVariable("conditionResult", result);
        return output;
    }
}