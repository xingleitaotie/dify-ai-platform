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

        List<Map<String, Object>> branches = (List<Map<String, Object>>) config.get("branches");
        if (branches == null || branches.isEmpty()) {
            throw new RuntimeException("条件分支(branches)不能为空");
        }

        // 顺序匹配分支
        for (int i = 0; i < branches.size(); i++) {
            Map<String, Object> branch = branches.get(i);
            String type = (String) branch.get("type");
            if ("ELSE".equals(type)) {
                return buildOutput("ELSE", true);
            }

            // 解析变量和运算符
            String variable = (String) branch.get("variable");
            String operator = (String) branch.get("operator");
            Object rawValue = branch.get("value"); // 可能是变量占位符或静态值

            // 解析变量值（变量占位符会被 WorkflowVariableResolver 处理）
            Object variableValue = resolver.resolve(variable, context);
            // 解析比较值（同样支持变量）
            Object compareValue = resolver.resolve(rawValue != null ? rawValue.toString() : null, context);

            boolean matched = evaluateCondition(variableValue, operator, compareValue);
            if (matched) {
                String branchId = type + "_" + i;  // 例如 IF_0, ELSE_IF_2
                return buildOutput(branchId, true);
            }
        }

        // 无匹配（理论上不会发生，因为至少应有 ELSE 分支）
        return buildOutput("NO_MATCH", false);
    }

    /**
     * 条件评估
     */
    private boolean evaluateCondition(Object variable, String operator, Object compareValue) {
        // 处理空值场景
        if ("empty".equals(operator)) {
            return variable == null || variable.toString().isEmpty();
        }
        if ("not_empty".equals(operator)) {
            return variable != null && !variable.toString().isEmpty();
        }
        if (variable == null) {
            return false;
        }

        String strVar = variable.toString();
        String strCompare = compareValue != null ? compareValue.toString() : "";

        switch (operator) {
            case "==":
                return strVar.equals(strCompare);
            case "!=":
                return !strVar.equals(strCompare);
            case ">":
                return compareNumeric(strVar, strCompare) > 0;
            case ">=":
                return compareNumeric(strVar, strCompare) >= 0;
            case "<":
                return compareNumeric(strVar, strCompare) < 0;
            case "<=":
                return compareNumeric(strVar, strCompare) <= 0;
            case "contains":
                return strVar.contains(strCompare);
            case "not_contains":
                return !strVar.contains(strCompare);
            default:
                log.warn("未知运算符: {}", operator);
                return false;
        }
    }

    private int compareNumeric(String a, String b) {
        try {
            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    private Map<String, Object> buildOutput(String branch, boolean matched) {
        Map<String, Object> output = new HashMap<>();
        output.put("branch", branch);
        output.put("matched", matched);
        log.info("条件节点输出分支: {}", branch);
        return output;
    }
}