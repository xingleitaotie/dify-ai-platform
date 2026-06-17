package com.washy.dify.workflow.node;

import com.washy.dify.workflow.config.WorkflowContext;
import com.washy.dify.workflow.util.WorkflowVariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartNodeExecutor implements NodeExecutor {

    private final WorkflowVariableResolver resolver;

    @Override
    public String getNodeType() {
        return "START";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        Map<String, Object> config = (Map<String, Object>) nodeInput.get("config");
        if (config == null) {
            config = new HashMap<>();
        }

        Map<String, Object> workflowInputs = context.getInputs();

        // 1. 将用户输入存入上下文（作为变量）
        for (Map.Entry<String, Object> entry : workflowInputs.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }

        // 2. 处理默认值（前端配置的默认值）
        //    ✅ 使用 resolver.resolveObject() 解析默认值（支持变量引用）
        List<Map<String, Object>> inputVariables = (List<Map<String, Object>>) config.get("inputVariables");
        if (inputVariables != null) {
            for (Map<String, Object> varDef : inputVariables) {
                String varName = (String) varDef.get("name");
                Object defaultValue = varDef.get("defaultValue");

                if (!workflowInputs.containsKey(varName) && defaultValue != null) {
                    // ✅ 关键修改：使用解析器解析默认值（支持变量替换）
                    Object resolvedDefault = resolver.resolveObject(defaultValue, context);
                    context.setVariable(varName, resolvedDefault);
                    workflowInputs.put(varName, resolvedDefault);
                    log.debug("开始节点设置默认值: {} = {}", varName, resolvedDefault);
                }
            }
        }

        log.info("开始节点执行完成，输入变量: {}", workflowInputs);

        // 3. 返回所有输入变量
        return new HashMap<>(workflowInputs);
    }
}