package com.washy.dify.llm.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequest;
import com.washy.dify.common.entity.llm.ChatResponse;
import com.washy.dify.llm.config.LlmProperties;
import com.washy.dify.llm.exception.LlmException;
import com.washy.dify.llm.service.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 魔塔社区 ModelScope 大模型客户端
 * 对齐 OllamaClient 结构、接口、重试、异常体系
 */
@Component("modelScopeClient")
@Slf4j
public class ModelScopeLlmClient implements LlmClient {

    private final RestTemplate restTemplate;

    private LlmProperties llmProperties;

    // 构造器
    public ModelScopeLlmClient() {
        this.restTemplate = new RestTemplate();
        this.llmProperties = new LlmProperties();
    }

    public ModelScopeLlmClient(LlmProperties properties) {
        this.restTemplate = new RestTemplate();
        this.llmProperties = properties;
    }

    @Override
    public void setLlmProperties(LlmProperties properties) {
        this.llmProperties = properties;
    }

    /**
     * 魔塔标准对话接口
     */
    private static final String MODELSCOPE_CHAT_API = "/chat/completions";

    // ==================== 新增：标准消息列表接口 ====================

    /**
     * 核心方法：直接接受标准的 ChatMessage 列表
     * 调用方完全控制消息内容，不会发生提示词污染
     */
    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String chat(List<ChatMessage> messages) {
        return chat(messages, null, null);
    }

    /**
     * 带参数的对话接口
     */
    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String chat(List<ChatMessage> messages, Double temperature, Integer maxTokens) {
        try {
            // 验证消息
            if (messages == null || messages.isEmpty()) {
                throw new LlmException("消息列表不能为空");
            }

            // 记录消息信息（便于调试）
            log.info("ModelScope 调用 - 消息数量: {}, 最后角色: {}",
                    messages.size(),
                    messages.get(messages.size() - 1).getRole());

            if (log.isDebugEnabled()) {
                log.debug("消息内容: {}", JSON.toJSONString(messages));
            }

            // 1. 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmProperties.getApiKey());

            // 2. 转换消息格式
            List<Map<String, String>> apiMessages = convertToApiMessages(messages);

            // 3. 构建请求体
            Map<String, Object> request = new HashMap<>();
            request.put("model", llmProperties.getModelName());
            request.put("messages", apiMessages);
            request.put("temperature", temperature != null ? temperature : llmProperties.getTemperature());
            request.put("max_tokens", maxTokens != null ? maxTokens : llmProperties.getMaxTokens());
            request.put("stream", false);

            // 4. 发送请求
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            String url = llmProperties.getBaseUrl() + MODELSCOPE_CHAT_API;

            JSONObject response = restTemplate.postForObject(url, entity, JSONObject.class);
            return parseSyncResponse(response);

        } catch (Exception e) {
            log.error("ModelScope 调用失败", e);
            throw new LlmException("ModelScope 模型调用失败：" + e.getMessage());
        }
    }

    /**
     * 完整请求参数对话接口
     */
    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmProperties.getApiKey());

            HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);
            String url = llmProperties.getBaseUrl() + MODELSCOPE_CHAT_API;

            ChatResponse response = restTemplate.postForObject(url, entity, ChatResponse.class);
            return response;

        } catch (Exception e) {
            log.error("ModelScope 调用失败", e);
            throw new LlmException("ModelScope 模型调用失败：" + e.getMessage());
        }
    }

    /**
     * 流式对话接口（接受消息列表）
     */
    @Override
    public void chatStream(List<ChatMessage> messages, Consumer<String> consumer) {
        chatStream(messages, null, null, consumer);
    }

    /**
     * 带参数的流式对话
     */
    @Override
    public void chatStream(List<ChatMessage> messages, Double temperature, Integer maxTokens, Consumer<String> consumer) {
        try {
            if (messages == null || messages.isEmpty()) {
                log.error("messages 为空，无法调用 ModelScope API");
                throw new LlmException("messages 不能为空");
            }

            // 转换消息格式
            List<Map<String, String>> validMessages = convertToApiMessages(messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmProperties.getApiKey());

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("model", llmProperties.getModelName());
            requestMap.put("messages", validMessages);
            requestMap.put("temperature", temperature != null ? temperature : llmProperties.getTemperature());
            requestMap.put("max_tokens", maxTokens != null ? maxTokens : llmProperties.getMaxTokens());
            requestMap.put("stream", true);

            log.info("ModelScope 流式请求 URL: {}", llmProperties.getBaseUrl() + MODELSCOPE_CHAT_API);

            restTemplate.execute(
                    llmProperties.getBaseUrl() + MODELSCOPE_CHAT_API,
                    HttpMethod.POST,
                    request -> {
                        request.getHeaders().putAll(headers);
                        request.getBody().write(JSON.toJSONString(requestMap).getBytes(StandardCharsets.UTF_8));
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {

                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().isEmpty()) continue;

                                // 处理 SSE 格式
                                String jsonStr = line;
                                if (line.startsWith("data: ")) {
                                    jsonStr = line.substring(6);
                                }

                                if ("[DONE]".equals(jsonStr.trim())) {
                                    log.info("流式响应结束");
                                    break;
                                }

                                try {
                                    JSONObject jsonObject = JSON.parseObject(jsonStr);

                                    if (jsonObject.containsKey("error")) {
                                        JSONObject error = jsonObject.getJSONObject("error");
                                        String errorMsg = error.getString("message");
                                        log.error("ModelScope API 错误: {}", errorMsg);
                                        throw new LlmException("ModelScope API 错误: " + errorMsg);
                                    }

                                    if (jsonObject.containsKey("choices")) {
                                        JSONArray choices = jsonObject.getJSONArray("choices");
                                        if (choices != null && !choices.isEmpty()) {
                                            JSONObject choice = choices.getJSONObject(0);

                                            if (choice.containsKey("delta")) {
                                                JSONObject delta = choice.getJSONObject("delta");
                                                if (delta.containsKey("content")) {
                                                    String content = delta.getString("content");
                                                    if (content != null && !content.isEmpty()) {
                                                        consumer.accept(content);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("解析 JSON 失败: {}", e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            log.error("读取响应流失败", e);
                            throw new LlmException("读取响应失败: " + e.getMessage());
                        }
                        return null;
                    }
            );

        } catch (Exception e) {
            log.error("ModelScope 流式调用失败", e);
            throw new LlmException("ModelScope 流式调用异常：" + e.getMessage());
        }
    }

    // ==================== 保持兼容的旧接口 ====================


    /**
     * @deprecated 建议使用 {@link #chat(List)} 方法，避免提示词污染
     */
    @Override
    @Deprecated
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String chat(String prompt, String history) {
        log.warn("使用已废弃的 chat(String, String) 方法，可能导致提示词污染");

        // 构建消息列表（保持原有行为，但添加警告）
        List<ChatMessage> messages = new ArrayList<>();

        // 添加 system prompt
        messages.add(ChatMessage.system("You are a helpful assistant."));

        // 添加历史
        if (history != null && !history.trim().isEmpty()) {
            messages.add(ChatMessage.user(history));
            // 注意：这里假设历史只有用户消息，实际应该交替存储
        }

        // 添加当前问题
        messages.add(ChatMessage.user(prompt));

        // 调用新接口
        return chat(messages);
    }

    /**
     * @deprecated 建议使用 {@link #chat(List)} 方法，将 context 放入 system 消息
     */
    @Override
    @Deprecated
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String ragChat(String prompt, String context) {
        log.warn("使用已废弃的 ragChat 方法，建议使用 chat(List) 并将 context 放入 system 消息");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("上下文信息：\n" + context));
        messages.add(ChatMessage.user(prompt));

        return chat(messages);
    }

    /**
     * @deprecated 建议使用 {@link #chatStream(List, Consumer)} 方法
     */
    @Override
    @Deprecated
    public void streamChat(List<ChatMessage> messages, Consumer<String> consumer) {
        chatStream(messages, consumer);
    }

    /**
     * 通用请求接口（保持兼容）
     */
    @Override
    public Map<String, Object> chat(Map<String, Object> request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmProperties.getApiKey());

            request.put("model", llmProperties.getModelName());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            String url = llmProperties.getBaseUrl() + MODELSCOPE_CHAT_API;

            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            return response;

        } catch (Exception e) {
            log.error("ModelScope 调用失败", e);
            throw new LlmException("ModelScope 模型调用失败：" + e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将内部 ChatMessage 转换为 ModelScope API 格式
     */
    private List<Map<String, String>> convertToApiMessages(List<ChatMessage> messages) {
        List<Map<String, String>> apiMessages = new ArrayList<>();

        for (ChatMessage msg : messages) {
            Map<String, String> apiMsg = new HashMap<>();
            apiMsg.put("role", msg.getRole());
            apiMsg.put("content", msg.getContent() != null ? msg.getContent() : "");
            apiMessages.add(apiMsg);
        }

        return apiMessages;
    }

    /**
     * 解析同步响应
     */
    private String parseSyncResponse(JSONObject response) {
        if (response == null || !response.containsKey("choices")) {
            throw new LlmException("ModelScope 返回异常");
        }

        String content = response.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        // 删除 think 标签内容
        if (content != null) {
            content = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        }
        log.info("大模型输出为：{}", content);
        return content;
    }

    // ==================== 重试降级 ====================

    @Recover
    public String chatRecover(Exception e, String prompt, String history) {
        log.error("ModelScope 调用重试失败：{}", e.getMessage());
        return "大模型服务繁忙，请稍后再试~";
    }

    @Recover
    public String ragChatRecover(Exception e, String prompt, String context) {
        log.error("ModelScope RAG 调用重试失败：{}", e.getMessage());
        return "RAG增强问答服务繁忙，请稍后再试~";
    }

    @Recover
    public String chatListRecover(Exception e, List<ChatMessage> messages) {
        log.error("ModelScope 调用重试失败（消息列表）：{}", e.getMessage());
        return "大模型服务繁忙，请稍后再试~";
    }
}