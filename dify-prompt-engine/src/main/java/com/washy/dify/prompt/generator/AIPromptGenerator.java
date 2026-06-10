package com.washy.dify.prompt.generator;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.LlmFeignClient;
import com.washy.dify.prompt.entity.GenerateRequest;
import com.washy.dify.prompt.entity.GenerateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AIPromptGenerator implements PromptGenerator {

    @Autowired
    private LlmFeignClient llmFeignClient;

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 4096;

    @Override
    public GenerateResponse generate(GenerateRequest request) {
        log.info("开始生成提示词, 需求: {}, 类型: {}", request.getRequirement(), request.getType());

        GenerateResponse response = new GenerateResponse();
        response.setCreatedAt(new Date());

        // 1. 构建消息
        List<ChatMessage> messages = buildMessages(request);

        // 2. 调用大模型
        String generatedContent = callLLM(messages, request);
        log.info("大模型返回内容长度: {}", generatedContent != null ? generatedContent.length() : 0);

        // 3. 解析结果
        parseResponse(generatedContent, response);

        // 4. 设置元数据
        response.setName(generateName(request));
        response.setVersion("v1.0.0");
        response.setConfidenceScore(calculateConfidence(response, request));
        response.setSuggestions(generateSuggestions(request, response));

        return response;
    }

    /**
     * 构建消息
     */
    private List<ChatMessage> buildMessages(GenerateRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt()));
        messages.add(ChatMessage.user(buildUserPrompt(request)));
        return messages;
    }

    /**
     * 系统提示词
     */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的系统提示词工程师。\n\n");
        sb.append("【任务】\n");
        sb.append("根据用户的需求描述，生成一个完整的系统提示词。\n\n");
        sb.append("【输出要求】\n");
        sb.append("1. 系统提示词需要包含：\n");
        sb.append("   - 角色定位：定义AI的身份和职责\n");
        sb.append("   - 核心能力：列出AI需要具备的技能\n");
        sb.append("   - 输出格式：规定回复的结构和格式\n");
        sb.append("   - 限制条件：明确不能做什么\n");
        sb.append("2. 使用 Markdown 格式，结构清晰\n");
        sb.append("3. 只输出系统提示词内容，不要添加额外说明\n\n");
        sb.append("【输出格式示例】\n");
        sb.append("# 角色\n");
        sb.append("你是一个xxx专家...\n\n");
        sb.append("## 技能\n");
        sb.append("### 技能1\n");
        sb.append("1. 步骤一\n\n");
        sb.append("## 限制\n");
        sb.append("- 限制一\n");
        return sb.toString();
    }

    /**
     * 用户提示词
     */
    private String buildUserPrompt(GenerateRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 用户需求\n");
        sb.append(request.getRequirement()).append("\n\n");

        sb.append("## 模板类型\n");
        sb.append(getTypeDescription(request.getType())).append("\n\n");

        sb.append("## 输出语言\n");
        sb.append("zh-CN".equals(request.getLanguage()) ? "中文" : "English").append("\n\n");

        sb.append("## 注意事项\n");
        sb.append("1. RAG类型需包含 {{context}} 变量\n");
        sb.append("2. 用户输入使用 {{query}} 变量\n");
        sb.append("3. 提示词要清晰、具体、可执行\n\n");

        sb.append("请开始生成：");

        return sb.toString();
    }

    /**
     * 获取类型描述
     */
    private String getTypeDescription(GenerateRequest.TemplateType type) {
        if (type == null) return "通用类型";

        switch (type) {
            case GENERAL:
                return "通用聊天类型：适用于日常对话、问答、信息查询等场景";
            case CUSTOM:
                return "自定义类型：根据用户需求灵活设计";
            case SUMMARY:
                return "总结类型：对输入内容进行总结归纳";
            case AGENT_ANSWER:
                return "Agent回答类型：根据信息生成回答";
            case FUNCTION_CALLING:
                return "函数调用类型：说明何时调用什么函数";
            case AGENT_DECISION:
                return "Agent决策类型：分析情况并做出决策";
            case RAG:
                return "RAG问答类型：需包含{{context}}变量";
            case CODE:
                return "代码生成类型：生成代码相关的提示词";
            case CREATIVE:
                return "创意写作类型：适用于创意写作、文案生成等";
            case DATA:
                return "数据分析类型：适用于数据分析、报表生成等";
            case STREAMING:
                return "流式对话类型：适用于流式输出场景";
            default:
                return "通用类型：根据需求灵活设计";
        }
    }

    /**
     * 调用大模型
     */
    private String callLLM(List<ChatMessage> messages, GenerateRequest request) {
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessages(messages);

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", extractTemperature(request));
        params.put("max_tokens", extractMaxTokens(request));
        dto.setParams(params);

        // 传递模型配置（如果有）
        if (request.getModelConfigId() != null) {
            dto.setConfigId(request.getModelConfigId());
            log.info("使用指定模型配置ID: {}", request.getModelConfigId());
        } else if (request.getModelType() != null) {
            dto.setModelType(request.getModelType());
            log.info("使用指定模型类型: {}", request.getModelType());
        }

        Result<String> result = llmFeignClient.chat(dto);

        if (result != null && result.getCode() == 200 && result.getData() != null) {
            return result.getData();
        }

        String errorMsg = result != null ? result.getMsg() : "响应为空";
        log.error("LLM调用失败: {}", errorMsg);
        throw new RuntimeException("LLM调用失败: " + errorMsg);
    }

    /**
     * 提取温度参数
     */
    private double extractTemperature(GenerateRequest request) {
        if (request.getParameters() != null && request.getParameters().containsKey("temperature")) {
            Object temp = request.getParameters().get("temperature");
            if (temp instanceof Number) {
                return ((Number) temp).doubleValue();
            }
        }
        return DEFAULT_TEMPERATURE;
    }

    /**
     * 提取 maxTokens 参数
     */
    private int extractMaxTokens(GenerateRequest request) {
        if (request.getParameters() != null && request.getParameters().containsKey("maxTokens")) {
            Object tokens = request.getParameters().get("maxTokens");
            if (tokens instanceof Number) {
                return ((Number) tokens).intValue();
            }
        }
        return DEFAULT_MAX_TOKENS;
    }

    /**
     * 解析响应
     */
    private void parseResponse(String content, GenerateResponse response) {
        if (content == null || content.isEmpty()) {
            response.setPrompt("生成的提示词内容为空");
            return;
        }

        response.setPrompt(content);

        // 尝试提取系统提示词
        if (content.contains("# 角色") || content.contains("## 角色") || content.startsWith("#")) {
            response.setSystemPrompt(content);
        }

        // 设置默认模型参数
        GenerateResponse.ModelParamsDTO modelParams = new GenerateResponse.ModelParamsDTO();
        modelParams.setTemperature((float) DEFAULT_TEMPERATURE);
        modelParams.setMaxTokens(DEFAULT_MAX_TOKENS);
        modelParams.setTopP(0.9f);
        modelParams.setRepeatPenalty(1.1f);
        response.setModelParams(modelParams);
    }

    /**
     * 计算置信度
     */
    private int calculateConfidence(GenerateResponse response, GenerateRequest request) {
        int score = 80;

        if (response.getPrompt() == null || response.getPrompt().isEmpty()) {
            score -= 40;
        }

        if (request.getRequirement() == null || request.getRequirement().length() < 10) {
            score -= 20;
        }

        if (response.getPrompt() != null && response.getPrompt().contains("{{query}}")) {
            score += 5;
        }

        if (request.getType() == GenerateRequest.TemplateType.RAG &&
                response.getPrompt() != null && response.getPrompt().contains("{{context}}")) {
            score += 5;
        }

        return Math.min(100, Math.max(0, score));
    }

    /**
     * 生成优化建议
     */
    private List<String> generateSuggestions(GenerateRequest request, GenerateResponse response) {
        List<String> suggestions = new ArrayList<>();

        if (response.getPrompt() == null || response.getPrompt().isEmpty()) {
            suggestions.add("生成的提示词为空，请检查需求描述是否清晰");
            return suggestions;
        }

        if (response.getConfidenceScore() < 70) {
            suggestions.add("生成质量较低，建议提供更详细的需求描述或参考示例");
        }

        if (request.getType() == GenerateRequest.TemplateType.RAG &&
                !response.getPrompt().contains("{{context}}")) {
            suggestions.add("RAG类型提示词建议包含 {{context}} 变量来引用知识库内容");
        }

        if (!response.getPrompt().contains("{{query}}") && !response.getPrompt().contains("{{input}}")) {
            suggestions.add("建议在提示词中包含 {{query}} 变量来接收用户输入");
        }

        suggestions.add("生成后请根据实际效果微调提示词内容");

        return suggestions;
    }

    /**
     * 生成模板名称
     */
    private String generateName(GenerateRequest request) {
        String requirement = request.getRequirement();
        if (requirement == null || requirement.isEmpty()) {
            return "提示词模板";
        }
        if (requirement.length() > 30) {
            requirement = requirement.substring(0, 30);
        }
        return requirement;
    }
}