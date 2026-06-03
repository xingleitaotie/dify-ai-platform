package com.washy.dify.prompt.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GenerateResponse generateWithModel(GenerateRequest request, Long modelConfigId) {
        // 方案四：先尝试大模型，失败时降级到模板
        try {
            log.info("尝试使用大模型生成提示词...");
            return generateWithLLM(request, modelConfigId);
        } catch (Exception e) {
            log.warn("大模型生成失败，降级使用模板生成", e);
            return generateWithTemplate(request);
        }
    }

    /**
     * 使用大模型生成提示词
     */
    private GenerateResponse generateWithLLM(GenerateRequest request, Long modelConfigId) {
        GenerateResponse response = new GenerateResponse();
        response.setCreatedAt(new Date());

        // 1. 构建生成提示词的Prompt
        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildGenerationSystemPrompt()));
        messages.add(ChatMessage.user(buildGenerationUserPrompt(request)));

        log.info("生成系统提示词的Prompt长度: {}", buildGenerationSystemPrompt().length());
        log.info("生成用户提示词的Prompt长度: {}", buildGenerationUserPrompt(request).length());

        // 2. 调用大模型生成提示词（支持指定模型配置）
        String generatedContent = callLLMWithModel(messages, request);
        log.info("大模型返回内容长度: {}", generatedContent != null ? generatedContent.length() : 0);

        // 3. 解析生成的内容
        parseGeneratedContent(generatedContent, response, request);

        // 4. 生成模板名称和版本
        response.setName(generateTemplateName(request));
        response.setVersion("v1.0.0");

        // 5. 计算置信度
        response.setConfidenceScore(calculateConfidence(response, request));

        // 6. 添加优化建议
        response.setSuggestions(generateSuggestions(request, response));

        return response;
    }

    // ========== 新增：调用大模型的方法，支持指定模型配置 ==========
    private String callLLMWithModel(List<ChatMessage> messages, GenerateRequest request) {
        try {

            ChatRequestDTO dto =new ChatRequestDTO();
            dto.setMessages(messages);
            Map<String,Object> params = new HashMap<>();
            params.put("temperature", 0.7);
            params.put("max_tokens", 4096);
            dto.setParams(params);
            dto.setConfigId(request.getModelConfigId());
            Result<String> result = null;
            // 使用指定的模型配置ID
            log.info("使用模型配置ID: {} 生成提示词", request.getModelConfigId());
            result = llmFeignClient.chatWithConfig(dto);

            if (result != null && result.getCode() == 200 && result.getData() != null) {
                return result.getData();
            } else {
                String errorMsg = result != null ? result.getMsg() : "响应为空";
                log.error("LLM调用失败: {}", errorMsg);
                throw new RuntimeException("LLM调用失败: " + errorMsg);
            }

        } catch (Exception e) {
            log.error("调用大模型失败", e);
            throw new RuntimeException("调用大模型失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建生成提示词所需的 System Prompt
     * 角色定义、任务说明、输出格式要求等固定内容
     */
    private String buildGenerationSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的系统提示词工程师。\n");
        sb.append("\n");
        sb.append("【任务】\n");
        sb.append("根据用户的需求描述，生成一个完整的系统提示词。\n");
        sb.append("\n");
        sb.append("【输出要求】\n");
        sb.append("1. 系统提示词需要包含以下要素：\n");
        sb.append("   - 角色定位：定义AI的身份和职责\n");
        sb.append("   - 核心能力：列出AI需要具备的技能\n");
        sb.append("   - 输出格式：规定回复的结构和格式\n");
        sb.append("   - 限制条件：明确不能做什么\n");
        sb.append("2. 使用 Markdown 格式，结构清晰\n");
        sb.append("3. 只输出系统提示词内容，不要添加额外说明\n");
        sb.append("\n");
        sb.append("【输出格式示例】\n");
        sb.append("# 角色\n");
        sb.append("你是一个xxx专家，能够...\n");
        sb.append("\n");
        sb.append("## 技能\n");
        sb.append("### 技能1: xxx\n");
        sb.append("1. 步骤一\n");
        sb.append("2. 步骤二\n");
        sb.append("\n");
        sb.append("## 限制\n");
        sb.append("- 限制一\n");
        sb.append("- 限制二\n");
        return sb.toString();
    }

    /**
     * 构建生成提示词所需的 User Prompt
     * 用户具体需求、参数、示例等动态内容
     */
    private String buildGenerationUserPrompt(GenerateRequest request) {
        StringBuilder sb = new StringBuilder();

        // 1. 用户需求
        sb.append("## 用户需求\n");
        sb.append(request.getRequirement()).append("\n\n");

        // 2. 模板类型
        sb.append("## 模板类型\n");
        sb.append(getTypeDescription(request.getType())).append("\n\n");

        // 3. 输出语言
        sb.append("## 输出语言\n");
        sb.append("zh-CN".equals(request.getLanguage()) ? "中文" : "English").append("\n\n");

        // 4. 输出格式要求
        sb.append("## 输出格式\n");
        if (request.getOutputFormat() != null) {
            switch (request.getOutputFormat()) {
                case JSON:
                    sb.append("请严格按照以下JSON格式输出：\n\n");
                    sb.append(buildJsonFormatExample()).append("\n\n");
                    break;
                case YAML:
                    sb.append("请严格按照YAML格式输出\n\n");
                    break;
                case MARKDOWN:
                    sb.append("请使用Markdown格式输出\n\n");
                    break;
                default:
                    sb.append("请使用文本格式输出\n\n");
                    break;
            }
        } else {
            sb.append("请输出包含【系统提示词】和【用户提示词】两个部分\n\n");
        }

        // 5. 参考示例
        if (request.getExamples() != null && !request.getExamples().isEmpty()) {
            sb.append("## 参考示例\n");
            for (GenerateRequest.Example example : request.getExamples()) {
                sb.append("输入：").append(example.getInput()).append("\n");
                sb.append("输出：").append(example.getOutput()).append("\n\n");
            }
        }

        // 6. 额外参数
        if (request.getParameters() != null && !request.getParameters().isEmpty()) {
            sb.append("## 额外要求\n");
            for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        // 7. 注意事项
        sb.append("## 注意事项\n");
        sb.append("1. 如果涉及RAG，提示词中应包含{{context}}变量\n");
        sb.append("2. 用户输入使用{{query}}或{{input}}变量\n");
        sb.append("3. 提示词要清晰、具体、可执行\n");
        sb.append("4. 可以包含角色设定、任务描述、输出格式要求\n\n");

        sb.append("请开始生成：");

        return sb.toString();
    }


    /**
     * 构建生成提示词所需的Prompt
     */
    private String buildGenerationSytemPrompt(GenerateRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个专业的提示词工程师，请根据用户需求生成高质量的提示词。\n\n");

        sb.append("## 用户需求\n");
        sb.append(request.getRequirement()).append("\n\n");

        sb.append("## 模板类型\n");
        String typeDesc = getTypeDescription(request.getType());
        sb.append(typeDesc).append("\n\n");

        sb.append("## 输出语言\n");
        if ("zh-CN".equals(request.getLanguage())) {
            sb.append("中文\n\n");
        } else {
            sb.append("English\n\n");
        }

        sb.append("## 输出格式\n");
        if (request.getOutputFormat() != null) {
            switch (request.getOutputFormat()) {
                case JSON:
                    sb.append("请严格按照以下JSON格式输出：\n\n");
                    sb.append(buildJsonFormatExample());
                    break;
                case YAML:
                    sb.append("请严格按照YAML格式输出\n\n");
                    break;
                case MARKDOWN:
                    sb.append("请使用Markdown格式输出\n\n");
                    break;
                default:
                    sb.append("请使用文本格式输出\n\n");
                    break;
            }
        } else {
            sb.append("请使用文本格式输出，包含系统提示词和用户提示词两部分\n\n");
        }

        // 添加参考示例
        if (request.getExamples() != null && !request.getExamples().isEmpty()) {
            sb.append("## 参考示例\n");
            for (GenerateRequest.Example example : request.getExamples()) {
                sb.append("输入：").append(example.getInput()).append("\n");
                sb.append("输出：").append(example.getOutput()).append("\n\n");
            }
        }

        // 添加额外参数
        if (request.getParameters() != null && !request.getParameters().isEmpty()) {
            sb.append("## 额外要求\n");
            for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 注意事项\n");
        sb.append("1. 如果涉及RAG，提示词中应包含{{context}}变量\n");
        sb.append("2. 用户输入使用{{query}}或{{input}}变量\n");
        sb.append("3. 提示词要清晰、具体、可执行\n");
        sb.append("4. 可以包含角色设定、任务描述、输出格式要求\n\n");

        sb.append("请开始生成：");

        return sb.toString();
    }


    private String buildJsonFormatExample() {
        return "```json\n" +
                "{\n" +
                "  \"systemPrompt\": \"系统提示词内容\",\n" +
                "  \"userPrompt\": \"用户提示词模板，使用{{变量名}}表示变量\",\n" +
                "  \"modelParams\": {\n" +
                "    \"temperature\": 0.7,\n" +
                "    \"maxTokens\": 2048\n" +
                "  },\n" +
                "  \"variables\": [\n" +
                "    {\"name\": \"query\", \"description\": \"用户输入\", \"required\": true}\n" +
                "  ]\n" +
                "}\n" +
                "```\n\n";
    }

    private String getTypeDescription(GenerateRequest.TemplateType type) {
        if (type == null) return "自定义类型";

        switch (type) {
            case RAG:
                return "RAG问答类型：需要基于知识库内容回答问题，提示词中应包含{{context}}变量";
            case FUNCTION_CALLING:
                return "函数调用类型：需要调用特定函数完成任务，提示词中应说明何时调用什么函数";
            case AGENT_DECISION:
                return "Agent决策类型：需要Agent分析情况并做出决策";
            case AGENT_ANSWER:
                return "Agent回答类型：需要Agent根据信息生成回答";
            case SUMMARY:
                return "总结类型：需要对输入内容进行总结归纳";
            default:
                return "自定义类型：根据用户需求灵活设计";
        }
    }

    private void parseGeneratedContent(String content, GenerateResponse response, GenerateRequest request) {
        if (content == null || content.isEmpty()) {
            response.setPrompt("生成的提示词内容为空");
            return;
        }

        // 尝试解析JSON格式
        if (request.getOutputFormat() == GenerateRequest.OutputFormat.JSON) {
            try {
                String jsonContent = extractJson(content);
                if (jsonContent != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = objectMapper.readValue(jsonContent, Map.class);

                    String systemPrompt = (String) parsed.getOrDefault("systemPrompt", "");
                    String userPrompt = (String) parsed.getOrDefault("userPrompt", "");

                    response.setSystemPrompt(systemPrompt);

                    // 构建最终提示词
                    StringBuilder finalPrompt = new StringBuilder();
                    if (systemPrompt != null && !systemPrompt.isEmpty()) {
                        finalPrompt.append(systemPrompt).append("\n\n");
                    }
                    finalPrompt.append(userPrompt);
                    response.setPrompt(finalPrompt.toString());

                    // 解析模型参数
                    if (parsed.containsKey("modelParams")) {
                        Object modelParamsObj = parsed.get("modelParams");
                        if (modelParamsObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> modelParamsMap = (Map<String, Object>) modelParamsObj;
                            GenerateResponse.ModelParamsDTO modelParams = new GenerateResponse.ModelParamsDTO();

                            if (modelParamsMap.containsKey("temperature")) {
                                Object temp = modelParamsMap.get("temperature");
                                if (temp instanceof Number) {
                                    modelParams.setTemperature(((Number) temp).floatValue());
                                }
                            }
                            if (modelParamsMap.containsKey("maxTokens")) {
                                Object tokens = modelParamsMap.get("maxTokens");
                                if (tokens instanceof Number) {
                                    modelParams.setMaxTokens(((Number) tokens).intValue());
                                }
                            }
                            if (modelParamsMap.containsKey("topP")) {
                                Object topP = modelParamsMap.get("topP");
                                if (topP instanceof Number) {
                                    modelParams.setTopP(((Number) topP).floatValue());
                                }
                            }
                            if (modelParamsMap.containsKey("repeatPenalty")) {
                                Object penalty = modelParamsMap.get("repeatPenalty");
                                if (penalty instanceof Number) {
                                    modelParams.setRepeatPenalty(((Number) penalty).floatValue());
                                }
                            }

                            response.setModelParams(modelParams);
                        }
                    }
                } else {
                    response.setPrompt(content);
                }
            } catch (Exception e) {
                log.warn("解析JSON失败", e);
                response.setPrompt(content);
            }
        } else {
            // 文本格式，直接使用
            response.setPrompt(content);
        }
    }

    private String extractJson(String content) {
        if (content == null) return null;
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return content.substring(start, end + 1);
        }
        return null;
    }

    private int calculateConfidence(GenerateResponse response, GenerateRequest request) {
        int score = 80;

        if (response.getPrompt() == null || response.getPrompt().isEmpty()) {
            score -= 30;
        }

        if (request.getRequirement() == null || request.getRequirement().length() < 10) {
            score -= 20;
        }

        if (response.getPrompt() != null && response.getPrompt().contains("{{query}}")) {
            score += 5;
        }

        if ("RAG".equals(request.getType()) && response.getPrompt() != null && response.getPrompt().contains("{{context}}")) {
            score += 5;
        }

        return Math.min(100, Math.max(0, score));
    }

    private List<String> generateSuggestions(GenerateRequest request, GenerateResponse response) {
        List<String> suggestions = new ArrayList<>();

        if (response.getPrompt() == null || response.getPrompt().isEmpty()) {
            suggestions.add("生成的提示词为空，请检查需求描述是否清晰");
        }

        if (response.getConfidenceScore() < 70) {
            suggestions.add("生成质量较低，建议提供更详细的需求描述或参考示例");
        }

        if (request.getType() == GenerateRequest.TemplateType.RAG &&
                response.getPrompt() != null && !response.getPrompt().contains("{{context}}")) {
            suggestions.add("RAG类型提示词建议包含{{context}}变量来引用知识库内容");
        }

        if (response.getPrompt() != null &&
                !response.getPrompt().contains("{{query}}") &&
                !response.getPrompt().contains("{{input}}")) {
            suggestions.add("建议在提示词中包含{{query}}变量来接收用户输入");
        }

        if (request.getModelConfigId() == null && request.getModelType() == null && request.getModelName() == null) {
            suggestions.add("可以在生成时选择特定的大模型以获得更好的效果");
        }

        suggestions.add("生成后请根据实际效果微调提示词内容");

        return suggestions;
    }

    private String generateTemplateName(GenerateRequest request) {
        String requirement = request.getRequirement();
        if (requirement.length() > 20) {
            requirement = requirement.substring(0, 20);
        }
        return requirement + "_提示词模板";
    }

    // ==================== 模板生成方式（降级方案） ====================

    /**
     * 使用模板生成提示词（降级方案）
     */
    private GenerateResponse generateWithTemplate(GenerateRequest request) {
        log.info("使用模板生成提示词，需求: {}", request.getRequirement());

        String requirement = request.getRequirement();
        String lowerReq = requirement.toLowerCase();

        GenerateResponse response = new GenerateResponse();
        response.setTemplateId(UUID.randomUUID().toString());
        response.setCreatedAt(new Date());
        response.setName(generateName(requirement));
        response.setVersion("v1.0.0");
        response.setConfidenceScore(85);

        // 根据需求类型生成对应的提示词
        String prompt = generatePromptByType(requirement, lowerReq);
        response.setPrompt(prompt);

        // 设置模型参数
        GenerateResponse.ModelParamsDTO params = new GenerateResponse.ModelParamsDTO();
        params.setTemperature(0.3f);
        params.setMaxTokens(2048);
        params.setTopP(0.9f);
        params.setRepeatPenalty(1.1f);
        response.setModelParams(params);

        // 生成优化建议
        List<String> suggestions = new ArrayList<String>();
        suggestions.add("当前使用模板生成，如需更精准的提示词，请检查大模型服务是否正常运行");
        suggestions.add("可以根据实际测试效果调整 Temperature 参数");
        suggestions.add("建议在「限制」部分添加更详细的边界条件");
        response.setSuggestions(suggestions);

        return response;
    }

    /**
     * 根据需求类型生成提示词
     */
    private String generatePromptByType(String requirement, String lowerReq) {
        // 知识库问答类型
        if (lowerReq.contains("知识库") || lowerReq.contains("rag") ||
                lowerReq.contains("问答") || lowerReq.contains("查询") ||
                lowerReq.contains("专家")) {
            return generateKnowledgeBasePrompt(requirement);
        }

        // 摘要类型
        if (lowerReq.contains("摘要") || lowerReq.contains("总结") || lowerReq.contains("提炼")) {
            return generateSummaryPrompt(requirement);
        }

        // 代码类型
        if (lowerReq.contains("代码") || lowerReq.contains("编程") || lowerReq.contains("开发")) {
            return generateCodePrompt(requirement);
        }

        // 翻译类型
        if (lowerReq.contains("翻译") || lowerReq.contains("语言")) {
            return generateTranslatePrompt(requirement);
        }

        // 写作类型
        if (lowerReq.contains("写作") || lowerReq.contains("创作") || lowerReq.contains("文案")) {
            return generateWritingPrompt(requirement);
        }

        // 默认通用类型
        return generateGeneralPrompt(requirement);
    }

    /**
     * 生成知识库问答提示词（高质量版本）
     */
    private String generateKnowledgeBasePrompt(String requirement) {
        return "# 角色\n" +
                "你是一个专业的本地知识库问答助手，擅长基于本地知识库中的内容为用户提供准确、专业的回答。\n\n" +
                "## 技能\n" +
                "### 技能1：理解用户问题\n" +
                "1. 仔细分析用户提问的意图和核心关键词\n" +
                "2. 判断问题类型（事实查询、概念解释、操作指导等）\n" +
                "3. 识别是否需要检索知识库\n\n" +
                "### 技能2：检索知识库\n" +
                "1. 从用户问题中提取3-5个检索关键词\n" +
                "2. 在知识库中进行相似度检索\n" +
                "3. 筛选最相关的3-5条内容\n" +
                "4. 按相关度排序，优先使用高相关度内容\n\n" +
                "### 技能3：生成回答\n" +
                "1. 基于检索结果整合答案\n" +
                "2. 如果检索结果充分，给出准确回答\n" +
                "3. 如果检索结果不足，明确告知用户：\"根据现有知识库，我无法完整回答这个问题，建议补充以下信息：...\"\n" +
                "4. 回答要简洁、准确、有条理\n\n" +
                "## 回复格式\n" +
                "【回答】\n" +
                "（基于知识库的准确答案）\n\n" +
                "【参考来源】\n" +
                "- 来源1：具体引用内容\n" +
                "- 来源2：具体引用内容\n\n" +
                "【补充说明】（如有必要）\n" +
                "- 相关建议或注意事项\n\n" +
                "## 示例\n" +
                "用户问：\"什么是Java？\"\n" +
                "知识库中有：\"Java是一种面向对象的编程语言，由Sun Microsystems于1995年推出\"\n" +
                "回答：\n" +
                "【回答】\n" +
                "Java是一种面向对象的编程语言，由Sun Microsystems于1995年推出。\n\n" +
                "【参考来源】\n" +
                "- 技术文档：Java语言介绍\n\n" +
                "## 限制\n" +
                "1. 只基于知识库内容回答，绝对不使用自己的知识编造\n" +
                "2. 如果知识库中没有相关信息，必须如实告知\n" +
                "3. 不回答与知识库内容无关的问题\n" +
                "4. 回答语言使用中文，语气专业但不生硬\n" +
                "5. 引用来源时要标注清楚，便于用户核对";
    }

    /**
     * 生成摘要提示词
     */
    private String generateSummaryPrompt(String requirement) {
        return "# 角色\n" +
                "你是一个专业的文档摘要助手，擅长快速提取文档核心内容，生成简洁准确的摘要。\n\n" +
                "## 技能\n" +
                "### 技能1：分析文档内容\n" +
                "1. 识别文档类型（技术文档、新闻、论文、邮件等）\n" +
                "2. 提取文档的核心主题和关键信息\n" +
                "3. 识别文档的重要结论和观点\n\n" +
                "### 技能2：生成摘要\n" +
                "1. 提取3-5个核心要点\n" +
                "2. 整合成连贯的摘要内容\n" +
                "3. 控制摘要长度（普通摘要30-50字，详细摘要100-150字）\n" +
                "4. 保持原文档的语气和风格\n\n" +
                "### 技能3：质量检查\n" +
                "1. 确保摘要准确反映原文内容\n" +
                "2. 检查是否有遗漏重要信息\n" +
                "3. 确认字数符合要求\n\n" +
                "## 回复格式\n" +
                "**核心摘要：**\n" +
                "（30-50字的核心摘要）\n\n" +
                "**详细摘要：**\n" +
                "（100-150字的详细摘要）\n\n" +
                "**关键要点：**\n" +
                "- 要点1：具体内容\n" +
                "- 要点2：具体内容\n" +
                "- 要点3：具体内容\n\n" +
                "## 限制\n" +
                "1. 摘要必须忠实于原文，不添加个人观点\n" +
                "2. 不遗漏重要信息\n" +
                "3. 使用中文，语言流畅简洁";
    }

    /**
     * 生成代码助手提示词
     */
    private String generateCodePrompt(String requirement) {
        return "# 角色\n" +
                "你是一个专业的代码助手，擅长编写、解释和优化代码。\n\n" +
                "## 技能\n" +
                "### 技能1：理解代码需求\n" +
                "1. 分析用户的技术需求和场景\n" +
                "2. 识别编程语言和技术栈\n" +
                "3. 评估问题的复杂程度\n\n" +
                "### 技能2：编写代码\n" +
                "1. 提供完整、可运行的代码示例\n" +
                "2. 添加必要的注释说明\n" +
                "3. 包含错误处理和边界情况\n" +
                "4. 遵循语言的最佳实践\n\n" +
                "### 技能3：解释代码\n" +
                "1. 解释代码的核心逻辑\n" +
                "2. 说明关键函数和算法\n" +
                "3. 指出可能的性能优化点\n\n" +
                "## 回复格式\n" +
                "**代码实现：**\n" +
                "```java\n" +
                "// 代码内容\n" +
                "```\n\n" +
                "**代码说明：**\n" +
                "1. 核心逻辑说明\n" +
                "2. 关键函数解释\n" +
                "3. 使用注意事项\n\n" +
                "## 限制\n" +
                "1. 代码必须正确可运行\n" +
                "2. 遵循安全编码规范\n" +
                "3. 对于不明确的需求，先询问确认";
    }

    /**
     * 生成翻译助手提示词
     */
    private String generateTranslatePrompt(String requirement) {
        return "# 角色\n" +
                "你是一个专业的语言翻译助手，精通多语言翻译和文化适配。\n\n" +
                "## 技能\n" +
                "### 技能1：理解翻译需求\n" +
                "1. 识别源语言和目标语言\n" +
                "2. 判断文本类型（技术、文学、商务、日常等）\n" +
                "3. 识别专业术语\n\n" +
                "### 技能2：执行翻译\n" +
                "1. 保持原文的核心意思和风格\n" +
                "2. 确保专业术语翻译准确\n" +
                "3. 进行文化适配，避免生硬直译\n" +
                "4. 保持语言的流畅和自然\n\n" +
                "### 技能3：质量检查\n" +
                "1. 检查翻译的准确性\n" +
                "2. 确保语言表达地道\n" +
                "3. 核对专业术语的一致性\n\n" +
                "## 回复格式\n" +
                "**原文：**\n" +
                "（原文内容）\n\n" +
                "**译文：**\n" +
                "（翻译后的内容）\n\n" +
                "## 限制\n" +
                "1. 保持原文风格和语气\n" +
                "2. 专业术语翻译要准确\n" +
                "3. 不添加原文没有的内容";
    }

    /**
     * 生成写作助手提示词
     */
    private String generateWritingPrompt(String requirement) {
        return "# 角色\n" +
                "你是一个专业的写作助手，擅长各类文本创作。\n\n" +
                "## 技能\n" +
                "### 技能1：理解写作需求\n" +
                "1. 分析文本类型（文章、报告、邮件、故事等）\n" +
                "2. 识别目标受众和写作目的\n" +
                "3. 确定语气风格（正式、轻松、幽默等）\n\n" +
                "### 技能2：内容创作\n" +
                "1. 设计清晰的结构（开头、正文、结尾）\n" +
                "2. 提供有说服力的论据和例子\n" +
                "3. 保持语言的流畅和吸引力\n" +
                "4. 控制篇幅和深度\n\n" +
                "### 技能3：优化润色\n" +
                "1. 检查语法和拼写错误\n" +
                "2. 优化句式让表达更流畅\n" +
                "3. 调整语气使其更贴合需求\n\n" +
                "## 回复格式\n" +
                "**标题：**\n" +
                "（建议的标题）\n\n" +
                "**正文：**\n" +
                "（完整的文章内容）\n\n" +
                "## 限制\n" +
                "1. 内容要原创，不抄袭\n" +
                "2. 符合目标受众的需求\n" +
                "3. 遵守相关法律法规";
    }

    /**
     * 生成通用提示词
     */
    private String generateGeneralPrompt(String requirement) {
        return "# 角色\n" +
                "你是一个专业的智能助手，擅长处理各类问题和任务。\n\n" +
                "## 技能\n" +
                "### 技能1：理解需求\n" +
                "1. 仔细分析用户的问题和需求\n" +
                "2. 识别核心意图和关键信息\n" +
                "3. 判断问题的复杂程度\n\n" +
                "### 技能2：处理任务\n" +
                "1. 按照任务要求执行\n" +
                "2. 提供准确、专业的回答\n" +
                "3. 必要时分步骤解释\n" +
                "4. 确保回答完整覆盖需求\n\n" +
                "### 技能3：结果呈现\n" +
                "1. 使用清晰的结构化方式呈现\n" +
                "2. 突出关键信息\n" +
                "3. 提供必要的补充说明\n\n" +
                "## 回复格式\n" +
                "**回答：**\n" +
                "（主要答案内容）\n\n" +
                "**详细说明：**\n" +
                "（分点详细说明）\n\n" +
                "**总结：**\n" +
                "（关键结论或建议）\n\n" +
                "## 限制\n" +
                "1. 回答准确、专业\n" +
                "2. 不编造信息\n" +
                "3. 使用中文，语气友好专业";
    }

    /**
     * 生成名称
     */
    private String generateName(String requirement) {
        if (requirement == null || requirement.isEmpty()) {
            return "智能提示词";
        }
        String name = requirement.length() > 30 ? requirement.substring(0, 30) : requirement;
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_");
    }
}