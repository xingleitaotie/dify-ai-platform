package com.washy.dify.prompt.generator;

import com.washy.dify.prompt.entity.GenerateRequest;
import com.washy.dify.prompt.entity.GenerateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于模板的快速生成器（不依赖 LLM）
 * 用于大模型服务不可用时的降级方案
 */
@Slf4j
@Component
public class TemplateBasedGenerator implements PromptGenerator {

    private static final Map<String, String> TEMPLATE_MAP = new HashMap<>();
    private static final Map<String, String> TYPE_NAME_MAP = new HashMap<>();

    static {
        // 中文模板
        TEMPLATE_MAP.put("GENERAL_ZH", buildGeneralPromptZH());
        TEMPLATE_MAP.put("CUSTOM_ZH", buildCustomPromptZH());
        TEMPLATE_MAP.put("RAG_ZH", buildRAGPromptZH());
        TEMPLATE_MAP.put("FUNCTION_CALLING_ZH", buildFCPromptZH());
        TEMPLATE_MAP.put("SUMMARY_ZH", buildSummaryPromptZH());
        TEMPLATE_MAP.put("AGENT_DECISION_ZH", buildAgentDecisionPromptZH());
        TEMPLATE_MAP.put("AGENT_ANSWER_ZH", buildAgentAnswerPromptZH());
        TEMPLATE_MAP.put("CODE_ZH", buildCodePromptZH());
        TEMPLATE_MAP.put("CREATIVE_ZH", buildCreativePromptZH());
        TEMPLATE_MAP.put("DATA_ZH", buildDataPromptZH());
        TEMPLATE_MAP.put("STREAMING_ZH", buildStreamingPromptZH());

        // 英文模板（可扩展）
        TEMPLATE_MAP.put("RAG_EN", buildRAGPromptEN());

        // 类型名称映射
        TYPE_NAME_MAP.put("GENERAL", "通用助手");
        TYPE_NAME_MAP.put("CUSTOM", "自定义助手");
        TYPE_NAME_MAP.put("RAG", "知识库问答助手");
        TYPE_NAME_MAP.put("FUNCTION_CALLING", "函数调用助手");
        TYPE_NAME_MAP.put("SUMMARY", "文档摘要助手");
        TYPE_NAME_MAP.put("AGENT_DECISION", "Agent决策引擎");
        TYPE_NAME_MAP.put("AGENT_ANSWER", "Agent回答助手");
        TYPE_NAME_MAP.put("CODE", "代码助手");
        TYPE_NAME_MAP.put("CREATIVE", "创意写作助手");
        TYPE_NAME_MAP.put("DATA", "数据分析助手");
        TYPE_NAME_MAP.put("STREAMING", "流式对话助手");
    }

    @Override
    public GenerateResponse generate(GenerateRequest request) {
        GenerateRequest.TemplateType type = request.getType();
        String language = "zh".equalsIgnoreCase(request.getLanguage()) ? "ZH" : "EN";

        // 获取类型名称
        String typeName = TYPE_NAME_MAP.getOrDefault(type.name(), "智能助手");

        // 获取模板
        String key = type.name() + "_" + language;
        String template = TEMPLATE_MAP.getOrDefault(key, buildDefaultPrompt(typeName));

        // 根据需求定制模板
        String prompt = customizeTemplate(template, request, typeName);

        // 构建响应
        GenerateResponse response = new GenerateResponse();
        response.setTemplateId(UUID.randomUUID().toString());
        response.setCreatedAt(new Date());
        response.setPrompt(prompt);
        response.setName(type.name().toLowerCase() + "_prompt_template");
        response.setVersion("v1.0.0");

        // 设置模型参数
        GenerateResponse.ModelParamsDTO params = new GenerateResponse.ModelParamsDTO();
        params.setTemperature(getRecommendedTemperature(type));
        params.setMaxTokens(getRecommendedMaxTokens(type));
        params.setTopP(0.9f);
        params.setRepeatPenalty(1.1f);
        response.setModelParams(params);

        // 置信度评分
        response.setConfidenceScore(75);

        // 生成建议
        response.setSuggestions(generateSuggestions(request));

        log.info("模板生成器使用类型: {}, 语言: {}", type, language);

        return response;
    }

    /**
     * 根据类型推荐 Temperature
     */
    private float getRecommendedTemperature(GenerateRequest.TemplateType type) {
        switch (type) {
            case CREATIVE:
                return 0.9f;      // 创意写作需要更高温度
            case CODE:
                return 0.3f;      // 代码生成需要较低温度
            case SUMMARY:
                return 0.4f;      // 摘要需要稳定输出
            case FUNCTION_CALLING:
                return 0.2f;      // 函数调用需要精确
            default:
                return 0.7f;      // 默认
        }
    }

    /**
     * 根据类型推荐 MaxTokens
     */
    private int getRecommendedMaxTokens(GenerateRequest.TemplateType type) {
        switch (type) {
            case RAG:
                return 4096;
            case CODE:
                return 4096;
            case CREATIVE:
                return 8192;
            default:
                return 2048;
        }
    }

    /**
     * 定制模板
     */
    private String customizeTemplate(String template, GenerateRequest request, String typeName) {
        String requirement = request.getRequirement();

        // 替换角色占位符
        template = template.replace("${role}", typeName);

        // 替换需求描述
        if (requirement != null && !requirement.isEmpty()) {
            template = template.replace("${requirement}", requirement);
        }

        // 替换额外参数
        if (request.getParameters() != null) {
            for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                template = template.replace(placeholder, value);
            }
        }

        return template;
    }

    /**
     * 生成建议
     */
    private List<String> generateSuggestions(GenerateRequest request) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("当前使用模板生成，如需更精准的提示词，请检查大模型服务是否正常运行");

        if (request.getRequirement() == null || request.getRequirement().isEmpty()) {
            suggestions.add("建议提供更详细的需求描述以获得更好的效果");
        }

        suggestions.add("可以根据实际测试效果调整 Temperature 和 MaxTokens 参数");

        return suggestions;
    }

    // ==================== 模板构建方法 ====================

    /**
     * 通用聊天模板
     */
    private static String buildGeneralPromptZH() {
        return "# 角色\n" +
                "你是一个通用的智能助手，名叫 ${role}。\n\n" +
                "## 核心能力\n" +
                "1. 理解用户问题，提供准确、有用的回答\n" +
                "2. 对于不确定的问题，明确告知不确定\n" +
                "3. 保持友好、专业的沟通风格\n\n" +
                "## 任务\n" +
                "${requirement}\n\n" +
                "## 限制\n" +
                "1. 不编造信息\n" +
                "2. 回答简洁明了\n" +
                "3. 使用中文回答";
    }

    /**
     * 自定义模板
     */
    private static String buildCustomPromptZH() {
        return "# 角色\n" +
                "你是一个专业的智能助手。\n\n" +
                "## 任务\n" +
                "${requirement}\n\n" +
                "## 输出要求\n" +
                "- 准确、专业\n" +
                "- 结构清晰\n" +
                "- 符合用户期望";
    }

    /**
     * RAG问答模板
     */
    private static String buildRAGPromptZH() {
        return "# 角色\n" +
                "你是一个知识库问答助手。\n\n" +
                "## 核心规则\n" +
                "1. 必须基于【参考知识】回答问题，不要使用自己的知识\n" +
                "2. 如果参考知识中没有相关信息，回答：\"根据现有知识库，我无法回答这个问题\"\n" +
                "3. 回答要简洁准确，不要编造信息\n" +
                "4. 使用中文回答\n\n" +
                "## 回复格式\n" +
                "【回答】\n" +
                "（基于参考知识的答案）\n\n" +
                "【参考来源】\n" +
                "（引用来源）\n\n" +
                "## 参考知识\n" +
                "{{context}}\n\n" +
                "## 用户问题\n" +
                "{{query}}";
    }

    /**
     * 函数调用模板
     */
    private static String buildFCPromptZH() {
        return "# 角色\n" +
                "你是一个函数调用助手。\n\n" +
                "## 可用工具\n" +
                "{{tools}}\n\n" +
                "## 规则\n" +
                "1. 根据用户问题判断是否需要调用函数\n" +
                "2. 需要调用时，输出 JSON 格式：{\"function\": \"函数名\", \"arguments\": {}}\n" +
                "3. 不需要调用时，直接回答问题\n\n" +
                "## 用户问题\n" +
                "{{query}}";
    }

    /**
     * 摘要模板
     */
    private static String buildSummaryPromptZH() {
        return "# 角色\n" +
                "你是一个文档摘要助手。\n\n" +
                "## 任务\n" +
                "对以下内容生成30-50字的摘要\n\n" +
                "## 要求\n" +
                "1. 只输出摘要，不要有任何前缀\n" +
                "2. 字数30-50字\n" +
                "3. 提取核心主题和关键信息\n\n" +
                "## 待摘要内容\n" +
                "{{content}}";
    }

    /**
     * Agent决策模板
     */
    private static String buildAgentDecisionPromptZH() {
        return "# 角色\n" +
                "你是Agent决策引擎。\n\n" +
                "## 输出格式\n" +
                "- 检索知识库：{\"action\": \"rag\", \"query\": \"关键词\"}\n" +
                "- 调用工具：{\"action\": \"function\", \"function\": \"工具名\", \"arguments\": {}}\n" +
                "- 直接回答：{\"action\": \"direct\", \"response\": \"回答内容\"}\n\n" +
                "## 用户问题\n" +
                "{{query}}";
    }

    /**
     * Agent回答模板
     */
    private static String buildAgentAnswerPromptZH() {
        return "# 角色\n" +
                "你是Agent回答助手。\n\n" +
                "## 规则\n" +
                "1. 基于提供的信息生成回答\n" +
                "2. 回答要准确、完整\n" +
                "3. 如果信息不足，说明需要补充什么\n\n" +
                "## 提供的信息\n" +
                "{{context}}\n\n" +
                "## 用户问题\n" +
                "{{query}}";
    }

    /**
     * 代码生成模板
     */
    private static String buildCodePromptZH() {
        return "# 角色\n" +
                "你是一个代码助手。\n\n" +
                "## 任务\n" +
                "根据需求生成代码\n\n" +
                "## 要求\n" +
                "1. 代码要正确可运行\n" +
                "2. 添加必要的注释\n" +
                "3. 考虑边界情况和错误处理\n" +
                "4. 遵循语言最佳实践\n\n" +
                "## 输出格式\n" +
                "**代码实现：**\n" +
                "```编程语言\n" +
                "// 代码\n" +
                "```\n\n" +
                "**代码说明：**\n" +
                "1. 核心逻辑说明\n\n" +
                "## 用户需求\n" +
                "{{requirement}}";
    }

    /**
     * 创意写作模板
     */
    private static String buildCreativePromptZH() {
        return "# 角色\n" +
                "你是一个创意写作助手。\n\n" +
                "## 任务\n" +
                "根据需求进行创意写作\n\n" +
                "## 要求\n" +
                "1. 内容要有创意和吸引力\n" +
                "2. 语言生动、流畅\n" +
                "3. 符合主题和风格要求\n\n" +
                "## 写作要求\n" +
                "{{requirement}}";
    }

    /**
     * 数据分析模板
     */
    private static String buildDataPromptZH() {
        return "# 角色\n" +
                "你是一个数据分析助手。\n\n" +
                "## 任务\n" +
                "分析数据并给出洞察\n\n" +
                "## 要求\n" +
                "1. 提取关键指标和趋势\n" +
                "2. 提供数据洞察和建议\n" +
                "3. 使用表格或图表描述\n\n" +
                "## 数据\n" +
                "{{data}}\n\n" +
                "## 分析需求\n" +
                "{{requirement}}";
    }

    /**
     * 流式对话模板
     */
    private static String buildStreamingPromptZH() {
        return "# 角色\n" +
                "你是一个流式对话助手。\n\n" +
                "## 规则\n" +
                "1. 对话以流式方式输出\n" +
                "2. 保持回答的连贯性\n" +
                "3. 及时响应用户输入\n\n" +
                "## 用户消息\n" +
                "{{query}}";
    }

    /**
     * 默认模板
     */
    private static String buildDefaultPrompt(String typeName) {
        return "# 角色\n" +
                "你是一个${role}。\n\n" +
                "## 任务\n" +
                "${requirement}\n\n" +
                "## 回复要求\n" +
                "1. 回答准确、专业\n" +
                "2. 使用中文\n" +
                "3. 保持友好态度";
    }

    /**
     * 英文RAG模板
     */
    private static String buildRAGPromptEN() {
        return "# Role\n" +
                "You are a knowledge base QA assistant.\n\n" +
                "## Rules\n" +
                "1. Answer based ONLY on the provided knowledge\n" +
                "2. If no relevant info, say: I cannot answer based on the knowledge base\n" +
                "3. Be concise and accurate\n\n" +
                "## Knowledge\n" +
                "{{context}}\n\n" +
                "## Question\n" +
                "{{query}}";
    }
}