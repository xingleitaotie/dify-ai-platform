package com.washy.dify.workflow.node;

import com.washy.dify.workflow.config.WorkflowContext;
import com.washy.dify.workflow.util.WorkflowVariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptNodeExecutor implements NodeExecutor {

    private final WorkflowVariableResolver resolver;

    @Override
    public String getNodeType() {
        return "SCRIPT";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        Map<String, Object> config = (Map<String, Object>) nodeInput.get("config");
        if (config == null) {
            throw new RuntimeException("脚本节点配置不能为空");
        }

        String script = (String) config.get("script");
        if (script == null || script.isEmpty()) {
            throw new RuntimeException("脚本内容不能为空");
        }

        // 变量替换
        String resolvedScript = resolver.resolve(script, context).toString();

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        if (engine == null) {
            throw new RuntimeException("不支持脚本执行");
        }

        try {
            engine.put("context", context);
            engine.put("inputs", context.getInputs());
            engine.put("vars", context.getVariables());

            Object scriptResult = engine.eval(resolvedScript);
            log.info("脚本执行结果: {}", scriptResult);

            Map<String, Object> output = new HashMap<>();
            output.put("scriptResult", scriptResult);
            return output;

        } catch (ScriptException e) {
            log.error("脚本执行失败", e);
            throw new RuntimeException("脚本执行失败: " + e.getMessage());
        }
    }
}