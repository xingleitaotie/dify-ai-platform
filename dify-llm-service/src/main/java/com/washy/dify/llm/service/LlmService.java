package com.washy.dify.llm.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionChatRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.entity.llm.StreamChatRequestDTO;
import com.washy.dify.common.entity.prompt.PromptTemplateVO;
import com.washy.dify.common.exception.GlobalExceptionHandler;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.FunctionFeignClient;
import com.washy.dify.feign.client.ModelProviderFeignClient;
import com.washy.dify.feign.client.PromptFeignClient;
import com.washy.dify.llm.util.ChatContextHolder;
import com.washy.dify.llm.util.SSEContentFixer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LlmService {

    @Resource
    private ChatContextHolder chatContextHolder;

    @Resource
    private ModelProviderFeignClient modelProviderFeignClient;

    @Resource
    private FunctionFeignClient functionFeignClient;

    @Resource
    private PromptFeignClient promptFeignClient;

    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L;

    @Resource
    private RestTemplate restTemplate;

    @Value("${modelProvider.llm.service-url}")
    private String llmServiceUrl;
    @Value("${modelProvider.llm.action}")
    private String action;

    // 缓存动态路由选中的模板（session级别）
    private final Map<String, String> sessionTemplateCache = new ConcurrentHashMap<>();

    // 缓存提示词模板内容（模板ID -> 模板内容）
    private final Map<String, PromptTemplateVO> templateContentCache = new ConcurrentHashMap<>();

    /**
     * 获取会话的模板ID（支持缓存）
     */
    private String getSessionTemplateId(String sessionId, String userMessage, String intent) {
        // 1. 如果会话已经有绑定的模板，直接使用
        if (sessionTemplateCache.containsKey(sessionId)) {
            String templateId = sessionTemplateCache.get(sessionId);
            log.debug("使用会话缓存的模板: sessionId={}, templateId={}", sessionId, templateId);
            return templateId;
        }

        // 2. 如果请求指定了intent，转换为模板ID（兼容旧版本）
        if (intent != null && !intent.isEmpty()) {
            String templateId = getTemplateIdByType(intent);
            if (templateId != null) {
                sessionTemplateCache.put(sessionId, templateId);
                log.info("根据intent绑定模板: sessionId={}, intent={}, templateId={}", sessionId, intent, templateId);
                return templateId;
            }
        }

        // 3. 使用动态路由获取模板
        try {
            Map<String,String> request = new HashMap<>();
            request.put("query",userMessage);
            Result<PromptTemplateVO> routeResult = promptFeignClient.route(request);
            if (routeResult != null && routeResult.getCode() == 200 && routeResult.getData() != null) {
                PromptTemplateVO template = routeResult.getData();
                String templateId = template.getId();
                sessionTemplateCache.put(sessionId, templateId);
                templateContentCache.put(templateId, template);
                log.info("动态路由选中模板: sessionId={}, templateId={}, name={}, type={}",
                        sessionId, templateId, template.getName(), template.getType());
                return templateId;
            }
        } catch (Exception e) {
            log.error("动态路由调用失败，将使用默认模板", e);
        }

        return null;
    }

    /**
     * 根据type获取模板ID（兼容旧版本）
     */
    private String getTemplateIdByType(String type) {
        try {
            Result<List<PromptTemplateVO>> result = promptFeignClient.getTemplatesByType(type);
            if (result != null && result.getCode() == 200 && result.getData() != null && !result.getData().isEmpty()) {
                return result.getData().get(0).getId();
            }
        } catch (Exception e) {
            log.warn("根据type获取模板失败: type={}", type, e);
        }
        return null;
    }

    /**
     * 获取模板内容
     */
    private PromptTemplateVO getTemplateContent(String templateId) {
        // 先从缓存获取
        if (templateContentCache.containsKey(templateId)) {
            return templateContentCache.get(templateId);
        }

        // 缓存未命中，从服务获取
        try {
            Result<PromptTemplateVO> result = promptFeignClient.getTemplate(templateId);
            if (result != null && result.getCode() == 200 && result.getData() != null) {
                templateContentCache.put(templateId, result.getData());
                return result.getData();
            }
        } catch (Exception e) {
            log.error("获取模板内容失败: templateId={}", templateId, e);
        }

        return null;
    }

    /**
     * 清除会话模板缓存（新会话时调用）
     */
    public void clearSessionTemplate(String sessionId) {
        sessionTemplateCache.remove(sessionId);
        log.debug("清除会话模板缓存: sessionId={}", sessionId);
    }

    /**
     * 构建消息列表（支持动态路由获取系统提示词）
     */
    private List<ChatMessage> buildMessagesWithDynamicPrompt(ChatRequestDTO request) {
        List<ChatMessage> messages = new ArrayList<>();
        String sessionId = request.getSessionId();

        // 1. 获取系统提示词
        String systemPrompt = null;

        // 优先使用请求中直接传入的系统提示词
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (ChatMessage msg : request.getMessages()) {
                if ("system".equals(msg.getRole())) {
                    systemPrompt = msg.getContent();
                    break;
                }
            }
        }

        // 如果请求中没有系统提示词，尝试通过动态路由获取
        if (systemPrompt == null) {
            String templateId = getSessionTemplateId(sessionId, request.getMessage(), request.getIntent());
            if (templateId != null) {
                PromptTemplateVO template = getTemplateContent(templateId);
                if (template != null && template.getTemplate() != null) {
                    systemPrompt = template.getTemplate();
                    log.info("使用动态路由模板: templateId={}, name={}", templateId, template.getName());
                }
            }
        }

        // 如果还是没有，使用默认系统提示词
        if (systemPrompt == null) {
            systemPrompt = getDefaultSystemPrompt();
            log.warn("未找到合适的模板，使用默认系统提示词");
        }

        // 添加系统消息
        messages.add(ChatMessage.system(systemPrompt));

        // 2. 添加历史消息
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (ChatMessage msg : request.getMessages()) {
                if (!"system".equals(msg.getRole())) {
                    messages.add(msg);
                }
            }
        } else if (request.getMessage() != null) {
            // 单条消息模式
            messages.add(ChatMessage.user(request.getMessage()));
        }

        return messages;
    }

    /**
     * 获取模型参数（优先从请求参数，其次从模板获取）
     */
    private Map<String, Object> getModelParams(ChatRequestDTO request, String templateId) {
        Map<String, Object> params = new HashMap<>();

        // 1. 优先使用请求中的参数
        if (request.getParams() != null) {
            params.putAll(request.getParams());
        }

        // 2. 如果params中没有temperature，从模板获取
        if (!params.containsKey("temperature") && templateId != null) {
            PromptTemplateVO template = getTemplateContent(templateId);
            if (template != null) {
                Double temperature = null;

                // 方式1：从 ModelParamsDTO 中获取
                if (template.getModelParams() != null && template.getModelParams().getTemperature() != null) {
                    temperature = template.getModelParams().getTemperature().doubleValue();
                }
                // 方式2：从新增的 temperature 字段获取
                else if (template.getTemperature() != null) {
                    temperature = template.getTemperature().doubleValue();
                }

                if (temperature != null) {
                    params.put("temperature", temperature);
                    log.info("使用模板温度参数: temperature={}", temperature);
                }
            }
        }

        // 3. 如果还是没有temperature，使用默认值
        if (!params.containsKey("temperature")) {
            params.put("temperature", 0.7);
        }

        // 4. 其他参数（max_tokens, top_p 同理）
        if (!params.containsKey("max_tokens") && templateId != null) {
            PromptTemplateVO template = getTemplateContent(templateId);
            if (template != null) {
                Integer maxTokens = null;
                if (template.getModelParams() != null && template.getModelParams().getMaxTokens() != null) {
                    maxTokens = template.getModelParams().getMaxTokens();
                } else if (template.getMaxTokens() != null) {
                    maxTokens = template.getMaxTokens();
                }
                if (maxTokens != null) {
                    params.put("max_tokens", maxTokens);
                }
            }
        }
        if (!params.containsKey("max_tokens")) {
            params.put("max_tokens", 2048);
        }

        if (!params.containsKey("top_p") && templateId != null) {
            PromptTemplateVO template = getTemplateContent(templateId);
            if (template != null) {
                Float topP = null;
                if (template.getModelParams() != null && template.getModelParams().getTopP() != null) {
                    topP = template.getModelParams().getTopP();
                } else if (template.getTopP() != null) {
                    topP = template.getTopP().floatValue();
                }
                if (topP != null) {
                    params.put("top_p", topP);
                }
            }
        }
        if (!params.containsKey("top_p")) {
            params.put("top_p", 0.9);
        }

        return params;
    }

    /**
     * 同步对话 - 集成动态路由
     */
    public String chat(ChatRequestDTO request) {
        // 生成sessionId（如果没有传入）
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            request.setSessionId(UUID.randomUUID().toString());
        }

        // 1. 构建消息列表（内部集成动态路由）
        List<ChatMessage> messages = buildMessagesWithDynamicPrompt(request);

        // 2. 判断是否已有完整的系统提示词
        boolean hasSystemPrompt = hasSystemPrompt(messages);

        String templateId = null;
        Map<String, Object> params = new HashMap<>();

        // 3. 只有没有系统提示词时，才通过动态路由获取模板
        if (!hasSystemPrompt) {
            templateId = getSessionTemplateId(request.getSessionId(), request.getMessage(), request.getIntent());
            params = getModelParams(request, templateId);
        } else {
            // 使用请求中的参数或默认参数
            params = getModelParamsFromRequest(request);
        }

        // 4. 调用统一对话接口
        Map<String, Object> invokeRequest = new HashMap<>();
        invokeRequest.put("messages", messages);
        invokeRequest.putAll(params);

        // 如果有其他 params 参数，也传递
        if (request.getParams() != null) {
            for (Map.Entry<String, Object> entry : request.getParams().entrySet()) {
                if (!params.containsKey(entry.getKey())) {
                    invokeRequest.put(entry.getKey(), entry.getValue());
                }
            }
        }

        Result<String> result = modelProviderFeignClient.unifiedSyncChat(invokeRequest);
        if (result.getCode() != 200) {
            log.error("模型调用失败: {}", result.getMsg());
            return "模型调用失败：" + result.getMsg();
        }

        return result.getData();
    }

    /**
     * 判断消息列表中是否已包含系统提示词
     */
    private boolean hasSystemPrompt(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.getRole()) && msg.getContent() != null && !msg.getContent().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 保存最后一条用户消息
     */
    private void saveLastUserMessage(String sessionId, List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if ("user".equals(msg.getRole())) {
                chatContextHolder.saveMessage(sessionId, msg);
                break;
            }
        }
    }

    /**
     * 从请求中获取模型参数（不通过模板）
     */
    private Map<String, Object> getModelParamsFromRequest(ChatRequestDTO request) {
        Map<String, Object> params = new HashMap<>();

        // 从请求参数中获取
        if (request.getParams() != null) {
            if (request.getParams().containsKey("temperature")) {
                params.put("temperature", request.getParams().get("temperature"));
            }
            if (request.getParams().containsKey("maxTokens")) {
                params.put("max_tokens", request.getParams().get("maxTokens"));
            }
            if (request.getParams().containsKey("topP")) {
                params.put("top_p", request.getParams().get("topP"));
            }
        }

        // 设置默认值
        if (!params.containsKey("temperature")) {
            params.put("temperature", 0.7);
        }
        if (!params.containsKey("max_tokens")) {
            params.put("max_tokens", 2048);
        }

        return params;
    }

    /**
     * 构建流式消息列表（支持动态路由）
     */
    private List<ChatMessage> buildStreamMessagesWithDynamicPrompt(String sessionId, StreamChatRequestDTO request) {

        // 如果前端已经传入了完整的消息列表，直接使用
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            log.info("使用前端传入的完整消息列表，sessionId={}, 消息数={}", sessionId, request.getMessages().size());

            // 检查是否已包含系统提示词
            boolean hasSystemPrompt = hasSystemPrompt(request.getMessages());
            if (!hasSystemPrompt) {
                log.warn("前端传入的消息列表中未包含系统提示词，可能影响对话效果");
            }

            // 如果需要保存上下文，提取最后一条 user 消息保存
            if (shouldSaveContext(request)) {
                saveLastUserMessage(sessionId, request.getMessages());
            }

            return request.getMessages();
        }

        // 否则使用传统方式构建消息列表
        return buildStreamMessagesLegacy(sessionId, request);
    }

    /**
     * 传统方式构建消息列表（需要构建 system、历史消息、user）
     */
    private List<ChatMessage> buildStreamMessagesLegacy(String sessionId, StreamChatRequestDTO request) {
        List<ChatMessage> allMessages = new ArrayList<>();

        // 1. 获取系统提示词（只有没有完整消息时才调用）
        String systemPrompt = extractSystemPrompt(sessionId, request);
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = getDefaultSystemPrompt();
        }
        allMessages.add(ChatMessage.system(systemPrompt));

        // 2. 添加历史消息
        List<ChatMessage> contextMessages = chatContextHolder.getContext(sessionId);
        if (contextMessages != null && !contextMessages.isEmpty()) {
            allMessages.addAll(contextMessages);
        }

        // 3. 添加当前用户消息
        ChatMessage userChatMessage = ChatMessage.user(request.getMessage());
        if (shouldSaveContext(request)) {
            chatContextHolder.saveMessage(sessionId, userChatMessage);
        }
        allMessages.add(userChatMessage);

        // 4. 限制消息数量
        final int MAX_HISTORY_SIZE = 20;
        if (allMessages.size() > MAX_HISTORY_SIZE) {
            allMessages = trimMessages(allMessages, systemPrompt, MAX_HISTORY_SIZE);
        }

        return allMessages;
    }

    /**
     * 判断是否应该保存上下文
     */
    private boolean shouldSaveContext(StreamChatRequestDTO request) {
        return request.getSaveContext() == null || request.getSaveContext();
    }

    /**
     * 提取系统提示词（优先请求中的，其次从模板获取）
     * 注意：此方法仅在传统构建模式下调用，不会与完整消息冲突
     */
    private String extractSystemPrompt(String sessionId, StreamChatRequestDTO request) {
        // 注意：这里不再检查 request.getMessages()
        // 因为调用此方法时已经确认没有完整消息

        // 优先使用请求中直接指定的系统提示词（如果有单独字段）
        // 这里假设 request 可能有 getSystemPrompt() 方法
        // 如果没有，可以跳过

        // 通过动态路由获取模板
        String templateId = getSessionTemplateId(sessionId, request.getMessage(), request.getIntent());
        if (templateId != null) {
            PromptTemplateVO template = getTemplateContent(templateId);
            if (template != null && template.getTemplate() != null && !template.getTemplate().isEmpty()) {
                log.info("流式对话使用动态路由模板: sessionId={}, templateId={}, name={}",
                        sessionId, templateId, template.getName());
                return template.getTemplate();
            }
        }

        return null;
    }

    /**
     * 裁剪消息列表，保留系统消息和最近的消息
     */
    private List<ChatMessage> trimMessages(List<ChatMessage> messages, String systemPrompt, int maxSize) {
        // 如果消息数量未超过限制，直接返回
        if (messages.size() <= maxSize) {
            return messages;
        }

        // 保留系统消息 + 最近 (maxSize - 1) 条消息
        int startIndex = messages.size() - (maxSize - 1);

        List<ChatMessage> trimmed = new ArrayList<>();
        trimmed.add(ChatMessage.system(systemPrompt));
        trimmed.addAll(messages.subList(startIndex, messages.size()));

        log.debug("消息列表裁剪: 原{}条, 现{}条", messages.size(), trimmed.size());
        return trimmed;
    }

    /**
     * 流式对话 - 集成动态路由
     */
    public SseEmitter streamChat(StreamChatRequestDTO request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String sessionId = request.getSessionId();

        new Thread(() -> {
            try {
                // 获取模板ID用于获取温度参数
                String templateId = getSessionTemplateId(sessionId, request.getMessage(), request.getIntent());

                // 获取模型参数
                Double temperature = request.getTemperature();
                if (temperature == null && templateId != null) {
                    PromptTemplateVO template = getTemplateContent(templateId);
                    if (template != null && template.getModelParams() != null && template.getModelParams().getTemperature() != null) {
                        temperature = template.getModelParams().getTemperature().doubleValue();
                    }
                }
                if (temperature == null) {
                    temperature = 0.7;
                }

                List<ChatMessage> messages = buildStreamMessagesWithDynamicPrompt(sessionId, request);

                Map<String, Object> invokeRequest = new HashMap<>();
                invokeRequest.put("messages", messages);
                invokeRequest.put("temperature", temperature);
                invokeRequest.put("stream", true);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(invokeRequest, headers);

                String url = llmServiceUrl + action;
                log.info("流式请求URL: {}, sessionId={}, temperature={}", url, sessionId, temperature);

                restTemplate.execute(url, HttpMethod.POST,
                        requestCallback -> {
                            requestCallback.getHeaders().putAll(headers);
                            requestCallback.getBody().write(JSON.toJSONString(invokeRequest).getBytes(StandardCharsets.UTF_8));
                        },
                        responseExtractor -> {
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(responseExtractor.getBody(), StandardCharsets.UTF_8))) {
                                String line;
                                StringBuilder contentBuffer = new StringBuilder();

                                while ((line = reader.readLine()) != null) {
                                    if (line.trim().isEmpty()) continue;
                                    if (line.startsWith("data:")) {
                                        String chunk = line.substring(5);
                                        if ("[DONE]".equals(chunk.trim())) {
                                            // 发送前修复内容
                                            if (contentBuffer.length() > 0) {
                                                String content = contentBuffer.toString();
                                                content = SSEContentFixer.fixFragmentedContent(content);
                                                emitter.send(SseEmitter.event().data(content));
                                            }
                                            break;
                                        }

                                        contentBuffer.append(chunk);

                                        // 在合适的时候发送
                                        if (shouldSend(contentBuffer.toString())) {
                                            String content = contentBuffer.toString();
                                            content = SSEContentFixer.fixFragmentedContent(content);
                                            emitter.send(SseEmitter.event().data(content));
                                            contentBuffer.setLength(0);
                                        }
                                    }
                                }
                            }
                            emitter.complete();
                            return null;
                        }
                );

            } catch (Exception e) {
                log.error("流式对话异常", e);
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /**
     * 判断缓冲区内容是否应该发送
     */
    private boolean shouldSend(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        if (isInCodeBlock(content)) {
            return content.endsWith("\n") && content.length() >= 50;
        }

        if (content.endsWith("。") || content.endsWith("！") ||
                content.endsWith("？") || content.endsWith(".") ||
                content.endsWith("!") || content.endsWith("?")) {
            return true;
        }

        if (content.matches(".*\\n[-\\d].*")) {
            return true;
        }

        if (content.contains("**") && countOccurrences(content, "**") % 2 == 0) {
            return true;
        }

        if (content.length() >= 200) {
            return true;
        }

        if (content.contains("\n") && content.length() >= 80) {
            return true;
        }

        return false;
    }

    /**
     * 判断是否在代码块中
     */
    private boolean isInCodeBlock(String content) {
        int count = countOccurrences(content, "```");
        return count % 2 != 0;
    }

    /**
     * 计算子串出现次数
     */
    private int countOccurrences(String str, String subStr) {
        if (str == null || subStr == null || str.isEmpty() || subStr.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(subStr, index)) != -1) {
            count++;
            index += subStr.length();
        }
        return count;
    }

    /**
     * Function Calling对话（支持多工具调用）
     */
    public String chatWithFunction(ChatRequestDTO request) {
        try {
            // 1. 获取工具列表
            Result<Map<String, Object>> toolsResult = functionFeignClient.getTools();
            if (toolsResult.getCode() != 200 || toolsResult.getData() == null) {
                log.warn("获取工具列表失败，降级为普通对话");
                return simpleChat(request.getMessage());
            }

            Map<String, Object> toolsData = toolsResult.getData();
            List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsData.get("tools");
            if (tools == null || tools.isEmpty()) {
                log.warn("没有可用的工具");
                return simpleChat(request.getMessage());
            }

            // 2. 构建消息列表
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.user(request.getMessage()));

            // 3. 调用大模型（只调用一次）
            FunctionChatRequest functionChatRequest = new FunctionChatRequest();
            functionChatRequest.setMessages(messages);
            functionChatRequest.setTools(tools);
            functionChatRequest.setToolChoice("auto");

            Result<String> llmResult = modelProviderFeignClient.functionChat(functionChatRequest);
            if (llmResult.getCode() != 200) {
                throw new GlobalExceptionHandler("模型调用失败：" + llmResult.getMsg());
            }

            String llmResponse = llmResult.getData();
            log.info("大模型返回：{}", llmResponse);

            // 4. 检查是否有工具调用
            if (!hasToolCalls(llmResponse)) {
                // 没有工具调用，返回空（由 Agent 处理）
                log.info("没有工具调用，返回空");
                return "";
            }

            // 5. 获取所有工具调用
            List<ToolCallInfo> toolCallInfos = extractToolCallInfos(llmResponse);
            log.info("检测到 {} 个工具调用", toolCallInfos.size());

            // 6. 执行所有工具调用，收集结果
            List<FunctionExecuteResult> allResults = new ArrayList<>();

            for (ToolCallInfo toolCall : toolCallInfos) {
                log.info("执行工具: {}", toolCall.getFunctionName());

                FunctionCallRequest functionRequest = new FunctionCallRequest();
                functionRequest.setFunctionName(toolCall.getFunctionName());
                functionRequest.setParameters(toolCall.getArguments());

                Result<FunctionExecuteResult> functionResult = functionFeignClient.invokeFunction(functionRequest);

                if (functionResult.getCode() == 200 && functionResult.getData() != null) {
                    allResults.add(functionResult.getData());
                } else {
                    // 工具执行失败，记录错误
                    FunctionExecuteResult errorResult = new FunctionExecuteResult();
                    errorResult.setSuccess(false);
                    errorResult.setFunctionName(toolCall.getFunctionName());
                    errorResult.setErrorMsg(functionResult.getMsg() != null ? functionResult.getMsg() : "未知错误");
                    allResults.add(errorResult);
                }
            }

            // 7. 返回所有工具执行结果（由 Agent 统一处理）
            return JSON.toJSONString(allResults);

        } catch (Exception e) {
            log.error("Function Calling 失败", e);
            throw new GlobalExceptionHandler("AI 对话异常：" + e.getMessage());
        }
    }

    // 提取 ToolCallInfo 用于执行工具
    private List<ToolCallInfo> extractToolCallInfos(String response) {
        List<ToolCallInfo> toolCalls = new ArrayList<>();
        try {
            JSONObject json = JSON.parseObject(response);
            JSONObject message = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message");

            if (message.containsKey("tool_calls")) {
                JSONArray toolCallsArray = message.getJSONArray("tool_calls");
                for (int i = 0; i < toolCallsArray.size(); i++) {
                    JSONObject toolCall = toolCallsArray.getJSONObject(i);

                    ToolCallInfo info = new ToolCallInfo();
                    info.setId(toolCall.getString("id"));
                    info.setIndex(toolCall.getInteger("index"));

                    JSONObject function = toolCall.getJSONObject("function");
                    info.setFunctionName(function.getString("name"));

                    String argumentsStr = function.getString("arguments");
                    info.setArguments(JSON.parseObject(argumentsStr));

                    toolCalls.add(info);
                }
            }
        } catch (Exception e) {
            log.error("解析工具调用失败", e);
        }
        return toolCalls;
    }


    /**
     * 简单对话（无工具）
     */
    private String simpleChat(String message) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(message));

        Map<String, Object> invokeRequest = new HashMap<>();
        invokeRequest.put("messages", messages);

        Result<String> llmResult = modelProviderFeignClient.unifiedSyncChat(invokeRequest);
        return llmResult.getCode() == 200 ? llmResult.getData() : "对话失败：" + llmResult.getMsg();
    }


    /**
     * 检查响应是否包含函数调用
     * 根据 OpenAI 标准格式检测
     */
    private boolean hasToolCalls(String response) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        try {
            JSONObject json = JSON.parseObject(response);

            // 1. 标准 OpenAI 格式：choices[0].message.tool_calls
            if (json.containsKey("choices")) {
                JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                // 标准字段：tool_calls
                if (message.containsKey("tool_calls") &&
                        message.getJSONArray("tool_calls") != null &&
                        !message.getJSONArray("tool_calls").isEmpty()) {
                    return true;
                }
            }

            // 2. 某些模型直接返回 tool_calls 或 function_call
            return json.containsKey("tool_calls") || json.containsKey("function_call");

        } catch (Exception e) {
            return false;
        }
    }

    // 辅助类
    @Data
    private static class ToolCallInfo {
        private String id;
        private Integer index;
        private String functionName;
        private Map<String, Object> arguments;
    }

//    // ==================== Function Calling 相关方法 ====================
//
//    private String buildFunctionSystemPrompt(List<FunctionInfo> functions) {
//        StringBuilder prompt = new StringBuilder();
//        prompt.append("你是一个智能助手，具备函数调用能力。\n");
//        prompt.append("可用函数列表：\n");
//
//        for (FunctionInfo func : functions) {
//            prompt.append("函数名：").append(func.getName()).append("\n");
//            prompt.append("描述：").append(func.getDesc()).append("\n");
//            prompt.append("参数：").append(JSON.toJSONString(func.getParams())).append("\n\n");
//        }
//
//        prompt.append("规则：\n");
//        prompt.append("1. 如果用户问题需要调用函数，必须严格返回 JSON 格式：{\"functionName\":\"xxx\",\"params\":{...}}\n");
//        prompt.append("2. 如果不需要调用函数，直接正常回答\n");
//        return prompt.toString();
//    }
//
//    private String buildFunctionUserPrompt(String userMessage) {
//        return "用户问题：" + userMessage;
//    }
//
//    private boolean isFunctionCallResponse(String response) {
//        if (response == null || response.trim().isEmpty()) {
//            return false;
//        }
//        String trimmed = response.trim();
//        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
//            return false;
//        }
//        boolean hasFunctionKey = trimmed.contains("\"functionName\"") ||
//                trimmed.contains("\"function\"") ||
//                trimmed.contains("\"name\"");
//        boolean hasCodeFeatures = trimmed.contains("private") ||
//                trimmed.contains("public") ||
//                trimmed.contains("return") ||
//                trimmed.contains("class ") ||
//                trimmed.contains("void ") ||
//                trimmed.contains("();");
//        return hasFunctionKey && !hasCodeFeatures;
//    }
//
//    private FunctionCallRequest parseFunctionCall(String response) {
//        try {
//            JSONObject json = JSON.parseObject(response.trim());
//            FunctionCallRequest request = new FunctionCallRequest();
//            request.setFunctionName(json.getString("functionName"));
//            request.setParameters(json.getObject("params", Object.class));
//            return request;
//        } catch (Exception e) {
//            throw new GlobalExceptionHandler("解析函数调用失败：" + e.getMessage());
//        }
//    }
//
//    private String buildFinalAnswer(String userQuestion, FunctionExecuteResult functionResult) {
//        String toolData = "";
//        if (functionResult != null && functionResult.getSuccess() != null && functionResult.getSuccess()) {
//            if (functionResult.getData() != null) {
//                toolData = functionResult.getData().toString();
//            }
//        }
//
//        if (toolData == null || toolData.trim().isEmpty()) {
//            toolData = "工具未返回有效结果";
//        }
//
//        String system = "请根据以下工具查询结果回答用户问题。\n\n" +
//                "工具查询结果：" + toolData + "\n\n" +
//                "要求：\n" +
//                "1. 直接使用工具查询结果中的信息回答用户\n" +
//                "2. 回答要简洁、自然\n" +
//                "3. 不要输出 JSON 格式\n" +
//                "4. 不要输出 <think> 标签\n" +
//                "5. 直接给出答案";
//
//        String user = "用户问题：" + userQuestion;
//
//        List<ChatMessage> messages = new ArrayList<>();
//        messages.add(ChatMessage.system(system));
//        messages.add(ChatMessage.user(user));
//
//        Map<String, Object> invokeRequest = new HashMap<>();
//        invokeRequest.put("messages", messages);
//
//        Result<String> result = modelProviderFeignClient.unifiedSyncChat(invokeRequest);
//        if (result.getCode() != 200) {
//            log.error("最终答案生成失败: {}", result.getMsg());
//            return "生成答案失败：" + result.getMsg();
//        }
//
//        log.info("LLM 最终回答结果：{}", result.getData());
//        return result.getData();
//    }

    /**
     * 获取默认系统提示词（降级方案）
     */
    private String getDefaultSystemPrompt() {
        return "你是一个智能AI助手，名叫 Dify AI。\n\n" +
                "## 核心规则\n" +
                "1. 回答要准确、简洁、有帮助\n" +
                "2. 如果不知道答案，直接说\"我不知道\"，不要编造\n" +
                "3. 使用Markdown格式组织内容\n" +
                "4. 代码必须使用标准Markdown代码块，格式为：```语言名称\n" +
                "5. 复杂问题可以分步骤回答\n\n" +
                "现在请回答用户的问题。";
    }
}