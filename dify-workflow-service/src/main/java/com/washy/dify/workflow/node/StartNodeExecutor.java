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
    public Map<String, Object> execute(Map<String, Object> config, WorkflowContext context) {
        Map<String, Object> workflowInputs = context.getInputs();

        // 将输入存入上下文
        for (Map.Entry<String, Object> entry : workflowInputs.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }

        // 处理默认值（前端配置的默认值）
        List<Map<String, Object>> inputVariables = (List<Map<String, Object>>) config.get("inputVariables");
        if (inputVariables != null) {
            for (Map<String, Object> varDef : inputVariables) {
                String varName = (String) varDef.get("name");
                Object defaultValue = varDef.get("defaultValue");

                if (!workflowInputs.containsKey(varName) && defaultValue != null) {
                    Object resolvedDefault = resolver.resolveObject(defaultValue, context);
                    context.setVariable(varName, resolvedDefault);
                    workflowInputs.put(varName, resolvedDefault);
                }
            }
        }

        log.info("开始节点执行完成，输入变量: {}", workflowInputs);

        // ====================== 正确逻辑：直接返回【所有输入变量】 ======================
        // 完全动态：前端配置什么字段，就返回什么，类型完全保留
        // 1:1 匹配你的前端 START 节点配置
        return new HashMap<>(workflowInputs);
    }
}