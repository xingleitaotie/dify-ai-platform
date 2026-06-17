package com.washy.dify.provider.client.chat.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequest;
import com.washy.dify.common.entity.llm.ChatResponse;
import com.washy.dify.provider.client.chat.ChatClient;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.exception.ModelProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
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
 * 配置从数据库读取，不使用配置文件
 */
@Slf4j
public class ModelScopeChatClient implements ChatClient {

    private static final String CHAT_API_PATH = "/chat/completions";

    private final RestTemplate restTemplate;
    private final ProviderEntity provider;
    private final ModelConfigEntity modelConfig;

    /**
     * 构造函数 - 从数据库配置创建客户端
     * @param provider 供应商配置（包含baseUrl、apiKey等）
     * @param modelConfig 模型配置（包含modelKey、参数等）
     */
    public ModelScopeChatClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
        this.restTemplate = new RestTemplate();
        this.provider = provider;
        this.modelConfig = modelConfig;
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String chat(List<ChatMessage> messages) {
        return chat(messages, null, null);
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String chat(List<ChatMessage> messages, Double temperature, Integer maxTokens) {
        try {
            // 验证参数
            if (messages == null || messages.isEmpty()) {
                throw new ModelProviderException("消息列表不能为空");
            }
            if (provider == null || provider.getBaseUrl() == null) {
                throw new ModelProviderException("供应商配置不完整");
            }
            if (modelConfig == null || modelConfig.getModelKey() == null) {
                throw new ModelProviderException("模型配置不完整");
            }

            log.info("ModelScope 调用 - provider: {}, model: {}, 消息数量: {}",
                    provider.getProviderKey(), modelConfig.getModelKey(), messages.size());

            // 构建请求头
            HttpHeaders headers = buildHeaders();

            // 转换消息格式
            List<Map<String, String>> apiMessages = convertToApiMessages(messages);

            // 构建请求体
            Map<String, Object> request = buildRequest(apiMessages, temperature, maxTokens);

            // 发送请求
            String url = provider.getBaseUrl() + CHAT_API_PATH;
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            JSONObject response = restTemplate.postForObject(url, entity, JSONObject.class);
            return parseSyncResponse(response);

        } catch (Exception e) {
            log.error("ModelScope 调用失败", e);
            throw new ModelProviderException("ModelScope 模型调用失败：" + e.getMessage());
        }
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            HttpHeaders headers = buildHeaders();

            // 确保请求中有模型名称
            if (request.getModel() == null || request.getModel().isEmpty()) {
                request.setModel(modelConfig.getModelKey());
            }

            String url = provider.getBaseUrl() + CHAT_API_PATH;
            HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

            return restTemplate.postForObject(url, entity, ChatResponse.class);

        } catch (Exception e) {
            log.error("ModelScope 调用失败", e);
            throw new ModelProviderException("ModelScope 模型调用失败：" + e.getMessage());
        }
    }

    @Override
    public void chatStream(List<ChatMessage> messages, Consumer<String> consumer) {
        chatStream(messages, null, null, consumer);
    }

    @Override
    public void chatStream(List<ChatMessage> messages, Double temperature, Integer maxTokens, Consumer<String> consumer) {
        try {
            if (messages == null || messages.isEmpty()) {
                throw new ModelProviderException("messages 不能为空");
            }
            if (provider == null || provider.getBaseUrl() == null) {
                throw new ModelProviderException("供应商配置不完整");
            }

            List<Map<String, String>> validMessages = convertToApiMessages(messages);
            HttpHeaders headers = buildHeaders();

            Map<String, Object> requestMap = buildStreamRequest(validMessages, temperature, maxTokens);

            String url = provider.getBaseUrl() + CHAT_API_PATH;
            log.info("ModelScope 流式请求 URL: {}", url);

            restTemplate.execute(
                    url,
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
                                    processStreamResponse(jsonObject, consumer);
                                } catch (Exception e) {
                                    log.warn("解析 JSON 失败: {}", e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            log.error("读取响应流失败", e);
                            throw new ModelProviderException("读取响应失败: " + e.getMessage());
                        }
                        return null;
                    }
            );

        } catch (Exception e) {
            log.error("ModelScope 流式调用失败", e);
            throw new ModelProviderException("ModelScope 流式调用异常：" + e.getMessage());
        }
    }

    @Override
    public boolean testConnection() {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.user("你好，请回复'连接成功'"));
            String result = chat(messages);
            return result != null && !result.isEmpty();
        } catch (Exception e) {
            log.error("ModelScope 连接测试失败", e);
            return false;
        }
    }

    @Override
    public String chatWithTools(List<ChatMessage> messages, List<Map<String, Object>> tools, String toolChoice) {
        try {
            // 1. 构建请求头
            HttpHeaders headers = buildHeaders();

            // 2. 转换消息格式
            List<Map<String, String>> apiMessages = convertToApiMessages(messages);

            // 3. 构建请求体（OpenAI 标准格式）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelConfig.getModelKey());
            requestBody.put("messages", apiMessages);

            // 4. 如果有工具，添加 tools 参数
            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", tools);
                requestBody.put("tool_choice", toolChoice != null ? toolChoice : "auto");
                log.info("ModelScope 使用工具调用模式，tools: {}", tools.size());
            }

            // 6. 发送请求（ModelScope 通常使用 OpenAI 兼容接口）
            String url = provider.getBaseUrl() + "/v1/chat/completions";
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("ModelScope API 请求 URL: {}", url);
            log.debug("ModelScope API 请求体: {}", JSON.toJSONString(requestBody));

            // 7. 接收响应
            JSONObject response = restTemplate.postForObject(url, entity, JSONObject.class);

            // 8. 解析响应（OpenAI 格式）
            return parseModelScopeResponse(response);

        } catch (Exception e) {
            log.error("ModelScope 调用失败", e);
            throw new ModelProviderException("ModelScope 模型调用失败：" + e.getMessage());
        }
    }

    /**
     * 解析 ModelScope 响应（OpenAI 标准格式）
     */
    private String parseModelScopeResponse(JSONObject response) {
        if (response == null) {
            throw new ModelProviderException("API返回结果为空");
        }

        // ModelScope 使用 OpenAI 标准格式，直接返回原始 JSON（包含 tool_calls）
        return response.toJSONString();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建请求头 - 从数据库配置读取
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 从 provider 获取 API Key
        if (provider.getApiKey() != null && !provider.getApiKey().isEmpty()) {
            headers.setBearerAuth(provider.getApiKey());
        }

        return headers;
    }

    /**
     * 构建同步请求体 - 从数据库配置读取模型名称和参数
     */
    private Map<String, Object> buildRequest(List<Map<String, String>> messages, Double temperature, Integer maxTokens) {
        Map<String, Object> request = new HashMap<>();

        // 模型名称从 modelConfig 获取
        request.put("model", modelConfig.getModelKey());
        request.put("messages", messages);

        // 参数优先级：方法参数 > 模型配置 > 默认值
        request.put("temperature", temperature != null ? temperature :
                (modelConfig.getTemperature() != null ? modelConfig.getTemperature() : 0.7));
        request.put("max_tokens", maxTokens != null ? maxTokens :
                (modelConfig.getMaxTokens() != null ? modelConfig.getMaxTokens() : 2048));
        request.put("stream", false);

        return request;
    }

    /**
     * 构建流式请求体
     */
    private Map<String, Object> buildStreamRequest(List<Map<String, String>> messages, Double temperature, Integer maxTokens) {
        Map<String, Object> request = new HashMap<>();

        request.put("model", modelConfig.getModelKey());
        request.put("messages", messages);
        request.put("temperature", temperature != null ? temperature :
                (modelConfig.getTemperature() != null ? modelConfig.getTemperature() : 0.7));
        request.put("max_tokens", maxTokens != null ? maxTokens :
                (modelConfig.getMaxTokens() != null ? modelConfig.getMaxTokens() : 2048));
        request.put("stream", true);

        return request;
    }

    /**
     * 转换消息格式 - 支持 ChatMessage 和 Map 两种类型
     */
    private List<Map<String, String>> convertToApiMessages(List<?> messages) {
        List<Map<String, String>> apiMessages = new ArrayList<>();

        for (Object msg : messages) {
            Map<String, String> apiMsg = new HashMap<>();

            if (msg instanceof ChatMessage) {
                // 如果是 ChatMessage 类型
                ChatMessage chatMsg = (ChatMessage) msg;
                apiMsg.put("role", chatMsg.getRole());
                apiMsg.put("content", chatMsg.getContent() != null ? chatMsg.getContent() : "");
            } else if (msg instanceof Map) {
                // 如果是 LinkedHashMap（Feign反序列化结果）
                Map<?, ?> mapMsg = (Map<?, ?>) msg;
                Object role = mapMsg.get("role");
                Object content = mapMsg.get("content");
                apiMsg.put("role", role != null ? role.toString() : "user");
                apiMsg.put("content", content != null ? content.toString() : "");
            }

            apiMessages.add(apiMsg);
        }

        return apiMessages;
    }

    /**
     * 解析同步响应
     */
    private String parseSyncResponse(JSONObject response) {
        if (response == null || !response.containsKey("choices")) {
            throw new ModelProviderException("ModelScope 返回异常");
        }

        String content = response.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        if (content != null) {
            content = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        }
        log.info("大模型输出为：{}", content);
        return content;
    }

    /**
     * 处理流式响应
     */
    private void processStreamResponse(JSONObject jsonObject, Consumer<String> consumer) {
        if (jsonObject.containsKey("error")) {
            JSONObject error = jsonObject.getJSONObject("error");
            String errorMsg = error.getString("message");
            log.error("ModelScope API 错误: {}", errorMsg);
            throw new ModelProviderException("ModelScope API 错误: " + errorMsg);
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
    }

    // ==================== 重试降级 ====================

    @Recover
    public String chatListRecover(Exception e, List<ChatMessage> messages) {
        log.error("ModelScope 调用重试失败：{}", e.getMessage());
        return "大模型服务繁忙，请稍后再试~";
    }
}