package com.washy.dify.llm.service;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.common.entity.function.FunctionInfo;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.entity.llm.StreamChatRequestDTO;
import com.washy.dify.common.exception.GlobalExceptionHandler;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.FunctionFeignClient;
import com.washy.dify.llm.factory.LlmClientFactory;
import com.washy.dify.llm.util.ChatContextHolder;
import com.washy.dify.llm.util.ChatMessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LlmService {
    @Resource
    private FunctionFeignClient functionFeignClient;

    @Resource
    private LlmClientFactory llmClientFactory;

    @Resource
    private ChatContextHolder chatContextHolder;

    @Resource
    private ChatMessageBuilder messageBuilder;

    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L;

    /**
     * 根据请求获取对应的LLM客户端
     */
    public LlmClient getClient(ChatRequestDTO request) {
        // 优先使用配置ID
        if (request.getConfigId() != null) {
            return llmClientFactory.getLlmClientByConfigId(request.getConfigId());
        }
        // 其次使用模型类型
        if (request.getModelType() != null && !request.getModelType().isEmpty()) {
            return llmClientFactory.getLlmClientByType(request.getModelType());
        }
        // 默认客户端
        return llmClientFactory.getLlmClient();
    }

    private LlmClient getClient(StreamChatRequestDTO request) {
        if (request.getConfigId() != null) {
            return llmClientFactory.getLlmClientByConfigId(request.getConfigId());
        }
        if (request.getModelType() != null && !request.getModelType().isEmpty()) {
            return llmClientFactory.getLlmClientByType(request.getModelType());
        }
        return llmClientFactory.getLlmClient();
    }

    /**
     * 普通对话（支持指定模型）
     */
    public String chat(ChatRequestDTO request) {

        // 1. 获取 LLM 客户端
        LlmClient client = getClient(request);

        List<ChatMessage> messages = null;

        // 如果前端已经封装好系统提示词和用户提示词，则直接查询
        if(request.getMessages() != null){
            messages = request.getMessages();
            log.info("LLM对话请求 - 模型类型: {}, 消息数: {}", client.toString(), messages.size());
        }else {

            // 2. 获取系统提示词（如果需要）
            String systemPrompt = getSystemPrompt(request);

            // 3. 构建消息列表
            messages = messageBuilder.buildMessages(request, systemPrompt);
            log.info("LLM对话请求 - 模型类型: {}, 系统提示词为: {}, 用户提示词为: {}", client.toString(), systemPrompt, request.getMessage());
        }
        // 4. 获取参数
        Double temperature = extractTemperature(request.getParams());
        Integer maxTokens = extractMaxTokens(request.getParams());

        // 5. 调用 LLM
        log.info("调用 LLM，sessionId: {}, 消息数: {}",
                request.getSessionId(), messages.size());

        String result;
        if (temperature != null || maxTokens != null) {
            result = client.chat(messages, temperature, maxTokens);
        } else {
            result = client.chat(messages);
        }

        return result;
    }

    /**
     * 兼容旧版
     */
    public String chat(String prompt) {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage(prompt);
        return chat(request);
    }

    /**
     * 条件对话
     */
    public Object conditionChat(ChatRequestDTO request) {
        log.info("条件对话请求 - 模型类型: {}", request.getModelType());

        LlmClient client = getClient(request);

        Map<String, Object> params = request.getParams();
        if (params == null) {
            params = new HashMap<>();
        }
        params.put("prompt", request.getMessage());

        return client.chat(params);
    }

    /**
     * Function Calling对话
     */
    public String chatWithFunction(ChatRequestDTO request) {
        try {
            LlmClient client = getClient(request);

            // 1. 获取所有注册的函数定义
            Result<List<FunctionInfo>> functionListResult = functionFeignClient.getFunctionList();
            List<FunctionInfo> functionList = functionListResult.getData();

            // 2. 构建给大模型的 Prompt
            List<ChatMessage> messages = new ArrayList<>();
            if(request.getMessages() != null && !request.getMessages().isEmpty()){
                messages = request.getMessages();
            }else{
                messages.add(ChatMessage.system(buildFunctionSystemPrompt(functionList)));
                messages.add(ChatMessage.user(buildFunctionUserPrompt(request.getMessage())));
            }

            // 3. 调用大模型
            String llmRawResponse = client.chat(messages);
             log.info("大模型原始返回：{}", llmRawResponse);

            // 4. 解析模型返回，判断是否需要调用函数
            if (isFunctionCallResponse(llmRawResponse)) {
                List<FunctionCallRequest> functions = parseFunctionCall(llmRawResponse);
                List<FunctionExecuteResult> allResults = new ArrayList<>();

                for (FunctionCallRequest functionCallRequest : functions) {
                    Result<FunctionExecuteResult> result = functionFeignClient.invokeFunction(functionCallRequest);
                    if (result != null && result.getCode() == 200) {
                        allResults.add(result.getData());
                    } else {
                        // 调用失败时也记录，便于调试
                        FunctionExecuteResult errorResult = new FunctionExecuteResult();
                        errorResult.setSuccess(false);
                        errorResult.setFunctionName(functionCallRequest.getFunctionName());
                        errorResult.setErrorMsg(result != null ? result.getMsg() : "调用失败");
                        allResults.add(errorResult);
                    }
                }

                // 根据结果数量格式化输出
                if (allResults.size() == 1) {
                    // 单个函数：直接输出数据
                    FunctionExecuteResult singleResult = allResults.get(0);
                    llmRawResponse = singleResult.getData() != null ? singleResult.getData().toString() : "{}";
                } else {
                    // 多个函数：输出JSON数组，包含完整信息
                    llmRawResponse = JSON.toJSONString(allResults);
                }
            }

            return llmRawResponse;

        } catch (Exception e) {
            throw new GlobalExceptionHandler("AI 对话异常：" + e.getMessage());
        }
    }

    /**
     * 流式对话
     */
    public SseEmitter streamChat(StreamChatRequestDTO request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String sessionId = request.getSessionId();

        LlmClient client = getClient(request);

        llmClientFactory.getThreadPoolExecutor().execute(() -> {
            try {
                List<ChatMessage> messages = null;
                if(null != request.getMessages() && !request.getMessages().isEmpty()){
                    messages = request.getMessages();
                }
                else {
                    messages = buildChatMessages(sessionId, request.getMessage());
                }
                // 使用数组包装以突破 final 限制
                StringBuilder buffer = new StringBuilder();
                int[] lastSendLength = {0};
                long[] lastSendTime = {System.currentTimeMillis()};

                client.chatStream(messages, chunk -> {
                    buffer.append(chunk);

                    int currentLength = buffer.length();
                    long now = System.currentTimeMillis();
                    boolean shouldSend = (currentLength - lastSendLength[0] >= 50)
                            || (now - lastSendTime[0] >= 100);

                    if (shouldSend) {
                        String toSend = buffer.substring(lastSendLength[0]);
                        lastSendLength[0] = currentLength;
                        lastSendTime[0] = now;
                        try {
                            emitter.send(toSend);
                        } catch (IOException e) {
                            log.error("发送失败", e);
                            emitter.completeWithError(e);
                        }
                    }
                });

                // 发送剩余内容
                if (buffer.length() > lastSendLength[0]) {
                    emitter.send(buffer.substring(lastSendLength[0]));
                }
                emitter.send("[DONE]");
                emitter.complete();

            } catch (Exception e) {
                log.error("流式对话异常", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 测试模型连接
     */
    public boolean testModel(ChatRequestDTO request) {
        try {
            LlmClient client = getClient(request);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是一个专业测试大模型是否连接成功的助手，请根据客户要求，直接输出"));
            messages.add(ChatMessage.user("你好，请回复'连接成功'"));
            String result = client.chat(messages);
            return result != null && !result.isEmpty();
        } catch (Exception e) {
            log.error("测试模型连接失败", e);
            return false;
        }
    }

    /**
     * 构建最终答案
     */
    private String buildFinalAnswer(String userQuestion, FunctionExecuteResult functionResult, LlmClient client) {
        // 检查工具结果是否有效
        String toolData = "";
        if (functionResult != null && functionResult.getSuccess() != null && functionResult.getSuccess()) {
            if (functionResult.getData() != null) {
                toolData = functionResult.getData().toString();
            } else  {
                toolData = "";
            }
        }

        // 如果没有有效结果
        if (toolData == null || toolData.trim().isEmpty()) {
            toolData = "工具未返回有效结果";
        }

        String system = "请根据以下工具查询结果回答用户问题。\n\n" +
                "工具查询结果：" + toolData + "\n\n" +
                "要求：\n" +
                "1. 直接使用工具查询结果中的信息回答用户\n" +
                "2. 回答要简洁、自然\n" +
                "3. 不要输出 JSON 格式\n" +
                "4. 不要输出 <think> 标签\n" +
                "5. 直接给出答案，例如：北京今天天气晴朗，温度25℃，空气质量优";
        String user = "用户问题：" + userQuestion + "\n\n";

        log.info("LLM 最终回答请求：{}", system + user);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(system));
        messages.add(ChatMessage.user(user));
        String result = client.chat(messages);
        log.info("LLM 最终回答结果：{}", result);
        return result;
    }

    /**
     * 构建带 Function 定义的 SystemPrompt
     */
    private String buildFunctionSystemPrompt(List<FunctionInfo> functions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能助手，具备函数调用能力。\n");
        prompt.append("可用函数列表：\n");

        for (FunctionInfo func : functions) {
            prompt.append("函数名：").append(func.getName()).append("\n");
            prompt.append("描述：").append(func.getDesc()).append("\n");
            prompt.append("参数：").append(JSON.toJSONString(func.getParams())).append("\n\n");
        }

        prompt.append("规则：\n");
        prompt.append("1. 如果用户问题需要调用函数，必须严格返回 JSON 格式：{\"functionName\":\"xxx\",\"params\":{...}}\n");
        prompt.append("2. 如果不需要调用函数，直接正常回答\n");
        return prompt.toString();
    }
    /**
     * 构建带 Function 定义的 UserPrompt
     */
    private String buildFunctionUserPrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户问题：").append(userMessage);
        return prompt.toString();
    }

    private boolean isFunctionCallResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }

        String trimmed = response.trim();

        // 1. JSON 格式判断
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }

        // 2. 包含函数调用关键字
        boolean hasFunctionKey = trimmed.contains("\"functionName\"") ||
                trimmed.contains("\"function\"") ||
                trimmed.contains("\"name\"");

        // 3. 不包含代码特征（排除 Java 代码）
        boolean hasCodeFeatures = trimmed.contains("private") ||
                trimmed.contains("public") ||
                trimmed.contains("return") ||
                trimmed.contains("class ") ||
                trimmed.contains("void ") ||
                trimmed.contains("();");

        return hasFunctionKey && !hasCodeFeatures;
    }

    private List<FunctionCallRequest> parseFunctionCall(String response) {
        try {
            String[] lines = response.trim().split("\n");
            List<FunctionCallRequest> requests = new ArrayList<>();

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                JSONObject json = JSON.parseObject(line.trim());
                FunctionCallRequest request = new FunctionCallRequest();
                request.setFunctionName(json.getString("functionName"));
                request.setParameters(json.getObject("params", Object.class));
                requests.add(request);
            }
            return requests;
        } catch (Exception e) {
            throw new GlobalExceptionHandler("解析函数调用失败：" + e.getMessage());
        }
    }

    /**
     * 构建完整对话上下文
     */
    public List<ChatMessage> buildChatMessages(String sessionId, String userMessage) {
        List<ChatMessage> allMessages = new ArrayList<>();

        String systemPrompt = getDefaultSystemPrompt();
        if (!systemPrompt.isEmpty()) {
            allMessages.add(ChatMessage.system(systemPrompt));
        }

        List<ChatMessage> contextMessages = chatContextHolder.getContext(sessionId);
        if (contextMessages != null && !contextMessages.isEmpty()) {
            allMessages.addAll(contextMessages);
        }

        ChatMessage userChatMessage = ChatMessage.user(userMessage);
        chatContextHolder.saveMessage(sessionId, userChatMessage);
        allMessages.add(userChatMessage);

        // 限制消息数量
        int maxHistorySize = 20;
        if (allMessages.size() > maxHistorySize) {
            boolean hasSystem = allMessages.get(0).getRole().equals("system");
            if (hasSystem) {
                List<ChatMessage> recentMessages = allMessages.subList(
                        allMessages.size() - (maxHistorySize - 1),
                        allMessages.size()
                );
                allMessages = new ArrayList<>();
                allMessages.add(ChatMessage.system(systemPrompt));
                allMessages.addAll(recentMessages);
            } else {
                allMessages = allMessages.subList(allMessages.size() - maxHistorySize, allMessages.size());
            }
        }

        log.info("构建消息列表完成，共 {} 条消息", allMessages.size());
        return allMessages;
    }

    /**
     * 提取 temperature 参数
     */
    private Double extractTemperature(Map<String, Object> params) {
        if (params != null && params.containsKey("temperature")) {
            Object value = params.get("temperature");
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return null;  // 使用客户端默认值
    }

    /**
     * 提取 maxTokens 参数
     */
    private Integer extractMaxTokens(Map<String, Object> params) {
        if (params != null && params.containsKey("maxTokens")) {
            Object value = params.get("maxTokens");
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }

    /**
     * 获取系统提示词
     */
    private String getSystemPrompt(ChatRequestDTO requestDTO) {
        // 优先使用请求中的 system prompt（如果有）
        if (requestDTO.getHistory() != null && !requestDTO.getHistory().isEmpty()) {
            for (ChatMessage msg : requestDTO.getHistory()) {
                if ("system".equals(msg.getRole())) {
                    log.debug("使用请求中的 system prompt");
                    return msg.getContent();
                }
            }
        }

        // 使用默认系统提示词
        return getDefaultSystemPrompt();
    }

    /**
     * 获取系统提示词（可根据 sessionId 或 Agent 配置获取）
     */
    private String getDefaultSystemPrompt() {


        return "你是一个智能AI助手，名叫 Dify AI。\n\n" +

                "## 核心规则\n" +
                "1. 回答要准确、简洁、有帮助\n" +
                "2. 如果不知道答案，直接说\"我不知道\"，不要编造\n" +
                "3. 禁止使用开场白（如\"当然\"、\"好的\"、\"没问题\"）\n" +
                "4. 复杂问题先用 <think> 标签展示思考过程，然后给出答案\n" +
                "5. 简单问题直接回答，不需要 <think> 标签\n\n" +

                "## 思考标签使用规则\n" +
                "仅在以下情况使用 <think> 标签：\n" +
                "- 数学计算、逻辑推理\n" +
                "- 代码分析、算法设计\n" +
                "- 多步骤问题拆解\n" +
                "- 对比分析类问题\n\n" +

                "## 输出格式示例\n\n" +

                "### 示例1：复杂问题（需要思考）\n" +
                "<think>\n" +
                "用户问的是快速排序算法的实现原理。\n" +
                "快速排序的核心是分治思想：选择基准值，分区，递归排序。\n" +
                "时间复杂度：平均 O(n log n)，最差 O(n²)。\n" +
                "需要给出Python代码示例。\n" +
                "</think>\n\n" +
                "快速排序是一种分治排序算法，核心思想是：\n" +
                "1. 选择一个基准值(pivot)\n" +
                "2. 将小于基准值的放左边，大于的放右边\n" +
                "3. 递归排序左右子数组\n\n" +
                "```python\n" +
                "def quick_sort(arr):\n" +
                "    if len(arr) <= 1:\n" +
                "        return arr\n" +
                "    pivot = arr[0]\n" +
                "    left = [x for x in arr[1:] if x <= pivot]\n" +
                "    right = [x for x in arr[1:] if x > pivot]\n" +
                "    return quick_sort(left) + [pivot] + quick_sort(right)\n" +
                "```\n\n" +

                "### 示例2：简单问题（直接回答）\n" +
                "用户问：今天天气怎么样？\n" +
                "回答：抱歉，我无法获取实时天气信息。建议您查看天气预报应用。\n\n" +

                "现在请回答用户的问题。";
    }
}