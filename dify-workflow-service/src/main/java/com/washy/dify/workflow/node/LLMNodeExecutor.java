package com.washy.dify.workflow.node;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.LlmFeignClient;
import com.washy.dify.workflow.config.WorkflowContext;
import com.washy.dify.workflow.util.WorkflowVariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LLMNodeExecutor implements NodeExecutor {

    private final LlmFeignClient llmFeignClient;
    private final WorkflowVariableResolver resolver;
    private final ObjectMapper objectMapper;

    @Override
    public String getNodeType() {
        return "LLM";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        // 统一获取 config
        Map<String, Object> config = (Map<String, Object>) nodeInput.get("config");
        if (config == null) config = new HashMap<>();

        // ========== 1. 解析提示词（变量替换） ==========
        Object systemPromptResolved = resolver.resolve((String) config.get("systemPrompt"), context);
        String systemPrompt = systemPromptResolved != null ? systemPromptResolved.toString() : "";

        Object userPromptResolved = resolver.resolve((String) config.get("userPrompt"), context);
        String userPrompt = userPromptResolved != null ? userPromptResolved.toString() : "";

        if (userPrompt.trim().isEmpty()) {
            throw new RuntimeException("LLM 节点用户提示词不能为空");
        }

        // ========== 2. 模型参数 ==========
        Integer modelConfigIdInt = resolver.resolveInteger(config.get("modelConfigId"), context, 0);
        Long modelConfigId = modelConfigIdInt.longValue();

        Double temperature = resolver.resolveDouble(config.get("temperature"), context, 0.7);
        Integer maxTokens = resolver.resolveInteger(config.get("maxTokens"), context, 4096);
        Map<String,Object> params = new HashMap<>();
        params.put("temperature",temperature);
        params.put("maxTokens",maxTokens);

        // ========== 3. 调用 LLM ==========
        ChatRequestDTO dto = new ChatRequestDTO();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userPrompt));
        dto.setMessages(messages);
        dto.setParams(params);
        dto.setConfigId(modelConfigId);

        Result<String> result = llmFeignClient.chatWithConfig(dto);

        if (result.getCode() != 200) {
            throw new RuntimeException("LLM 调用失败：" + result.getMsg());
        }

        String rawOutput = result.getData();
        log.info("LLM 原始输出：{}", rawOutput);

        // ========== 4. 动态解析输出类型（严格按前端配置） ==========
        Object outputTypeObj = resolver.resolve((String) config.get("outputType"), context);
        String outputType = outputTypeObj != null ? outputTypeObj.toString() : "string";

        Object outputVarObj = resolver.resolve((String) config.get("outputVar"), context);
        String outputVar = outputVarObj != null ? outputVarObj.toString() : "llm_response";

        Object arrayItemTypeObj = resolver.resolve(config.get("arrayItemType") == null ? "" : config.get("arrayItemType").toString(), context);
        String arrayItemType = arrayItemTypeObj != null ? arrayItemTypeObj.toString() : "string";

        Object finalOutput;
        String jsonStr = rawOutput.trim();

        try {
            if ("json".equalsIgnoreCase(outputType)) {
                // 前端配置：JSON
                if (jsonStr.startsWith("{")) {
                    finalOutput = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
                } else if (jsonStr.startsWith("[")) {
                    finalOutput = objectMapper.readValue(jsonStr, new TypeReference<List<Object>>() {});
                } else {
                    finalOutput = rawOutput;
                }
            } else if ("array".equalsIgnoreCase(outputType)) {
                // 前端配置：数组，严格按数组项类型解析
                if ("object".equalsIgnoreCase(arrayItemType)) {
                    // 对象数组：尝试解析 JSON 数组
                    finalOutput = objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
                } else {
                    // 字符串数组：先判断是否为 JSON 数组
                    if (jsonStr.startsWith("[")) {
                        try {
                            // 如果是 JSON 字符串数组，直接解析
                            finalOutput = objectMapper.readValue(jsonStr, new TypeReference<List<String>>() {});
                        } catch (Exception e) {
                            // 解析失败，按行分割
                            finalOutput = Arrays.stream(rawOutput.split("\n"))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.toList());
                        }
                    } else {
                        // 普通文本，按行分割为字符串数组
                        finalOutput = Arrays.stream(rawOutput.split("\n"))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
                    }
                }
            } else {
                // 前端配置：字符串
                finalOutput = rawOutput;
            }
        } catch (Exception e) {
            log.error("LLM 输出解析失败，使用原始文本", e);
            finalOutput = rawOutput;
        }

        // ========== 5. 按前端配置的变量名输出 ==========
        // 把结果存入上下文变量（关键！保证下游能通过 {{var.xxx}} 获取）
        context.setVariable(outputVar, finalOutput);

        // 直接输出纯净值，不再额外包一层 Map
        log.info("LLM 节点动态输出：{} = {}", outputVar, finalOutput);
        return Collections.singletonMap(outputVar, finalOutput);

    }
}