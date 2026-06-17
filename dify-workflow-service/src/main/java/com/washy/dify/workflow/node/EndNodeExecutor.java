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
public class EndNodeExecutor implements NodeExecutor {

    private final WorkflowVariableResolver resolver;

    @Override
    public String getNodeType() {
        return "END";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        Map<String, Object> output = new HashMap<>();

        Map<String, Object> config = (Map<String, Object>) nodeInput.get("config");
        List<Map<String, Object>> outputVariables = null;
        if (config != null && config.containsKey("outputVariables")) {
            outputVariables = (List<Map<String, Object>>) config.get("outputVariables");
        }

        // 如果有 outputVariables 配置，按映射关系构建输出
        if (outputVariables != null && !outputVariables.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            for (Map<String, Object> varDef : outputVariables) {
                String name = (String) varDef.get("name");
                String source = (String) varDef.get("source");
                if (name == null || name.isEmpty()) {
                    continue;
                }

                // ========== 关键修复 ==========
                // 将 "input.query" 转换为 "{{input.query}}" 格式，再交给解析器
                String sourceWithBrackets = wrapVariableIfNeeded(source);
                Object value = resolver.resolve(sourceWithBrackets, context);
                // =============================

                if (value != null) {
                    result.put(name, value);
                } else {
                    log.warn("变量 {} 解析为空，source: {}", name, source);
                }
            }
            output.put("code", 200);
            output.put("data", result);
            output.put("msg", "执行成功");
            log.info("结束节点按 outputVariables 输出: {}", result);
            return output;
        }

        // 无 outputVariables 配置时，使用智能提取逻辑
        Object lastOutput = context.getLastNodeOutput();
        Object finalResult = extractFinalResult(lastOutput);

        if (finalResult == null) {
            finalResult = "执行完成，但无返回结果（请检查工作流连线或 outputVariables 配置）";
        }

        output.put("code", 200);
        output.put("data", finalResult);
        output.put("msg", "执行成功");

        log.info("结束节点最终输出（无 outputVariables 配置）: {}", output);
        return output;
    }

    /**
     * 如果字符串是变量格式（如 input.xxx 或 var.xxx），自动包装为 {{input.xxx}} 或 {{var.xxx}}
     */
    private String wrapVariableIfNeeded(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        // 如果已经包含 {{ 和 }}，说明已经是完整变量格式，直接返回
        if (source.contains("{{") && source.contains("}}")) {
            return source;
        }

        // 判断是否为变量格式：input.xxx 或 var.xxx
        if (source.startsWith("input.") || source.startsWith("var.")) {
            return "{{" + source + "}}";
        }

        // 纯字符串（不是变量），直接返回
        return source;
    }

    /**
     * 智能提取最终结果
     */
    private Object extractFinalResult(Object lastOutput) {
        if (lastOutput == null) {
            return null;
        }

        if (lastOutput instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) lastOutput;
            if (resultMap.size() == 1) {
                return resultMap.values().iterator().next();
            }
            if (resultMap.containsKey("content")) {
                return resultMap.get("content");
            }
            if (resultMap.containsKey("answer")) {
                return resultMap.get("answer");
            }
            if (resultMap.containsKey("result")) {
                return resultMap.get("result");
            }
            Map<String, Object> cleaned = new HashMap<>(resultMap);
            cleaned.remove("_raw");
            cleaned.remove("nodeId");
            cleaned.remove("nodeType");
            cleaned.remove("costTime");
            return cleaned.isEmpty() ? resultMap : cleaned;
        }

        return lastOutput;
    }
}