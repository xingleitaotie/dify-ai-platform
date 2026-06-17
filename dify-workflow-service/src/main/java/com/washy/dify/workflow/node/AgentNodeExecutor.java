package com.washy.dify.workflow.node;

import com.washy.dify.common.entity.agent.AgentExecuteRequest;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.AgentFeignClient;
import com.washy.dify.workflow.config.WorkflowContext;
import com.washy.dify.workflow.util.WorkflowVariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 节点执行器
 * 调用 AgentChatService 完成智能编排（RAG + Function Calling + LLM）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentNodeExecutor implements NodeExecutor {

    private final AgentFeignClient agentFeignClient;
    private final WorkflowVariableResolver resolver;

    @Override
    public String getNodeType() {
        return "AGENT";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        log.info("===== Agent 节点执行 =====");
        log.info("节点输入: {}", nodeInput);

        // 1. 提取配置
        Map<String, Object> config = (Map<String, Object>) nodeInput.get("config");
        if (config == null) config = new HashMap<>();

        // 2. 解析 agentId（支持数字或字符串）
        Long agentId = null;
        Object agentIdObj = config.get("agentId");
        if (agentIdObj instanceof Number) {
            agentId = ((Number) agentIdObj).longValue();
        } else if (agentIdObj instanceof String) {
            try {
                agentId = Long.parseLong((String) agentIdObj);
            } catch (NumberFormatException e) {
                throw new RuntimeException("agentId 格式错误: " + agentIdObj);
            }
        }
        if (agentId == null) {
            throw new RuntimeException("Agent 节点必须配置 agentId");
        }

        // 3. 解析 query（支持变量替换，如 {{input.query}}、{{var.xxx}}）
        String queryTemplate = (String) config.get("query");
        if (queryTemplate == null || queryTemplate.isEmpty()) {
            throw new RuntimeException("Agent 节点 query 不能为空");
        }
        Object queryValue = resolver.resolve(queryTemplate, context);
        log.info("原始查询模板: {}", queryTemplate);
        log.info("解析后查询值: {} 类型: {}", queryValue, queryValue != null ? queryValue.getClass().getSimpleName() : "null");

        // 4. 将解析结果转为字符串（如果是 List，取第一个非空元素）
        String query;
        if (queryValue instanceof List) {
            List<?> list = (List<?>) queryValue;
            query = list.stream()
                    .filter(item -> item != null && !item.toString().trim().isEmpty())
                    .map(Object::toString)
                    .findFirst()
                    .orElse("");
        } else if (queryValue != null) {
            query = queryValue.toString();
        } else {
            query = "";
        }

        if (query.isEmpty()) {
            throw new RuntimeException("解析后的 query 为空，请检查变量是否有效");
        }
        log.info("最终使用的 query: {}", query);

        // 5. 构建 AgentExecuteRequest
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setAgentId(agentId);
        request.setQuery(query);
        // params 暂时传空，如需扩展可从 config 读取
        request.setParams(new HashMap<>());

        // 6. 调用 Agent 服务
        Result<String> result;

        try {
            result = agentFeignClient.chat(request);
        } catch (Exception e) {
            log.error("Agent 执行异常", e);
            throw new RuntimeException("Agent 执行失败: " + e.getMessage(), e);
        }
        if (result.getCode() != 200) {
            throw new RuntimeException("LLM 调用失败：" + result.getMsg());
        }

        String answer = result.getData();

        // 7. 构建输出
        Map<String, Object> output = new HashMap<>();
        output.put("agentId", agentId);
        output.put("query", query);
        output.put("answer", answer != null ? answer : "");

        log.info("Agent 执行完成，回答长度: {}", answer != null ? answer.length() : 0);
        return output;
    }
}