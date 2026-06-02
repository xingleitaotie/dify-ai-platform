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
public class EndNodeExecutor implements NodeExecutor {

    private final WorkflowVariableResolver resolver;

    @Override
    public String getNodeType() {
        return "END";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        Map<String, Object> output = new HashMap<>();
        Object lastOutput = context.getLastNodeOutput();

        // 智能获取最终结果（兼容所有节点：START / LLM / RAG / FUNCTION）
        Object finalResult = lastOutput;

        // 如果最后节点输出是 Map，自动提取真实值（支持动态变量名）
        if (lastOutput instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) lastOutput;
            // 排除辅助字段，只取业务结果
            if (resultMap.size() == 1) {
                finalResult = resultMap.values().iterator().next();
            } else if (resultMap.containsKey("raw")) {
                // 优先取原始内容
                finalResult = resultMap.get("raw");
            } else {
                finalResult = lastOutput;
            }
        }

        output.put("code", 200);
        output.put("data", finalResult != null ? finalResult : "执行完成，无返回结果");
        output.put("msg", "执行成功");

        log.info("结束节点最终输出: {}", output);
        return output;
    }
}