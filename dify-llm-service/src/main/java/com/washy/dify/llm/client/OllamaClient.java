package com.washy.dify.llm.client;

import com.alibaba.fastjson2.JSON;
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
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ollama 模型客户端
 */
@Component("ollamaClient")
@Slf4j
public class OllamaClient implements LlmClient {
    @Resource
    private RestTemplate restTemplate;

    @Resource
    private LlmProperties llmProperties;

    private static final String OLLAMA_CHAT_API = "/api/chat";

    // 构造器
    public OllamaClient() {
        this.restTemplate = new RestTemplate();
        this.llmProperties = new LlmProperties();
    }

    public OllamaClient(LlmProperties properties) {
        this.restTemplate = new RestTemplate();
        this.llmProperties = properties;
    }

    @Override
    public void setLlmProperties(LlmProperties properties) {
        this.llmProperties = properties;
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", llmProperties.getModelName());
            request.put("messages", messages);  // /api/chat 接口支持 messages 数组
            request.put("stream", false);

            Map<String, Object> options = new HashMap<>();
            options.put("temperature", llmProperties.getTemperature());
            options.put("num_predict", llmProperties.getMaxTokens());  // 注意：Ollama 使用 num_predict
            request.put("options", options);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            // 使用 /api/chat 接口
            Map<String, Object> response = restTemplate.postForObject(
                    llmProperties.getBaseUrl() + OLLAMA_CHAT_API,  // 改用 /api/chat
                    entity,
                    Map.class
            );

            if (response == null || !response.containsKey("message")) {
                throw new LlmException("Ollama 模型返回异常");
            }

            Map<String, String> message = (Map<String, String>) response.get("message");
            return message.get("content");
        } catch (Exception e) {
            log.error("Ollama 调用失败", e);
            throw new LlmException("Ollama 模型调用失败：" + e.getMessage());
        }
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chat(List<ChatMessage> messages, Double temperature, Integer maxTokens) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", llmProperties.getModelName());
            request.put("messages", messages);
            request.put("stream", false);

            Map<String, Object> options = new HashMap<>();
            options.put("temperature", temperature != null ? temperature : llmProperties.getTemperature());
            options.put("num_predict", maxTokens != null ? maxTokens : llmProperties.getMaxTokens());
            request.put("options", options);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            Map<String, Object> response = restTemplate.postForObject(
                    llmProperties.getBaseUrl() + OLLAMA_CHAT_API,
                    entity,
                    Map.class
            );

            if (response == null || !response.containsKey("message")) {
                throw new LlmException("Ollama 模型返回异常");
            }

            Map<String, String> message = (Map<String, String>) response.get("message");
            return message.get("content");
        } catch (Exception e) {
            log.error("Ollama 调用失败", e);
            throw new LlmException("Ollama 模型调用失败：" + e.getMessage());
        }
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            Map<String, Object> ollamaRequest = new HashMap<>();
            ollamaRequest.put("model", llmProperties.getModelName());
            ollamaRequest.put("messages", request.getMessages());
            ollamaRequest.put("stream", false);

            Map<String, Object> options = new HashMap<>();
            if (request.getTemperature() != null) {
                options.put("temperature", request.getTemperature());
            } else {
                options.put("temperature", llmProperties.getTemperature());
            }
            if (request.getMaxTokens() != null) {
                options.put("num_predict", request.getMaxTokens());
            } else {
                options.put("num_predict", llmProperties.getMaxTokens());
            }
            if (request.getTopP() != null) {
                options.put("top_p", request.getTopP());
            }
            ollamaRequest.put("options", options);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(ollamaRequest, headers);

            Map<String, Object> response = restTemplate.postForObject(
                    llmProperties.getBaseUrl() + OLLAMA_CHAT_API,
                    entity,
                    Map.class
            );

            if (response == null || !response.containsKey("message")) {
                throw new LlmException("Ollama 模型返回异常");
            }

            Map<String, String> message = (Map<String, String>) response.get("message");

            // 构建 ChatResponse
            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setContent(message.get("content"));
            chatResponse.setModel((String) response.get("model"));
            chatResponse.setCreatedAt((String) response.get("created_at"));

            // 获取 token 使用情况
            if (response.containsKey("eval_count")) {
                Map<String, Integer> usage = new HashMap<>();
                usage.put("prompt_tokens", (Integer) response.getOrDefault("prompt_eval_count", 0));
                usage.put("completion_tokens", (Integer) response.getOrDefault("eval_count", 0));
                usage.put("total_tokens", (Integer) response.getOrDefault("prompt_eval_count", 0)
                        + (Integer) response.getOrDefault("eval_count", 0));
                chatResponse.setUsage(usage);
            }

            return chatResponse;
        } catch (Exception e) {
            log.error("Ollama 调用失败", e);
            throw new LlmException("Ollama 模型调用失败：" + e.getMessage());
        }
    }

    @Override
    public void chatStream(List<ChatMessage> messages, Consumer<String> consumer) {
        chatStream(messages, null, null, consumer);
    }

    @Override
    public void chatStream(List<ChatMessage> messages, Double temperature, Integer maxTokens, Consumer<String> consumer) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "text/event-stream");

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("model", llmProperties.getModelName());
            requestMap.put("messages", messages);
            requestMap.put("stream", true);

            // 添加可选参数
            Map<String, Object> options = new HashMap<>();
            if (temperature != null) {
                options.put("temperature", temperature);
            } else {
                options.put("temperature", llmProperties.getTemperature());
            }
            if (maxTokens != null) {
                options.put("num_predict", maxTokens);
            } else {
                options.put("num_predict", llmProperties.getMaxTokens());
            }
            if (!options.isEmpty()) {
                requestMap.put("options", options);
            }

            RequestCallback requestCallback = request -> {
                request.getBody().write(JSON.toJSONString(requestMap).getBytes("UTF-8"));
            };

            ResponseExtractor<Void> responseExtractor = response -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;

                        // 处理可能的 data: 前缀
                        if (line.startsWith("data: ")) {
                            line = line.substring(6);
                        }

                        try {
                            JSONObject jsonObject = JSONObject.parseObject(line);
                            if (jsonObject.containsKey("message")) {
                                JSONObject msg = jsonObject.getJSONObject("message");
                                String content = msg.getString("content");
                                if (content != null && consumer != null) {
                                    consumer.accept(content);
                                }
                            }
                            // 检查是否完成
                            if (jsonObject.getBoolean("done") != null && jsonObject.getBoolean("done")) {
                                break;
                            }
                        } catch (Exception e) {
                            log.warn("解析流式响应失败: {}", line, e);
                        }
                    }
                }
                return null;
            };

            restTemplate.execute(
                    llmProperties.getBaseUrl() + OLLAMA_CHAT_API,
                    HttpMethod.POST,
                    requestCallback,
                    responseExtractor
            );

        } catch (Exception e) {
            log.error("Ollama 流式调用失败", e);
            throw new LlmException("Ollama 流式调用异常：" + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> chat(Map<String, Object> request) {
        try {
            if (!request.containsKey("model")) {
                request.put("model", llmProperties.getModelName());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            Map<String, Object> response = restTemplate.postForObject(
                    llmProperties.getBaseUrl() + OLLAMA_CHAT_API,
                    entity,
                    Map.class
            );

            if (response == null) {
                throw new LlmException("Ollama 模型返回异常");
            }

            return response;
        } catch (Exception e) {
            log.error("Ollama 调用失败", e);
            throw new LlmException("Ollama 模型调用失败：" + e.getMessage());
        }
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String chat(String prompt, String history) {
        try {
            // 构建消息列表
            List<ChatMessage> messages = new ArrayList<>();

            if (history != null && !history.isEmpty()) {
                // 假设 history 格式为 "user: xxx\nassistant: xxx"
                String[] lines = history.split("\n");
                for (String line : lines) {
                    if (line.startsWith("user: ")) {
                        messages.add(ChatMessage.user(line.substring(6)));
                    } else if (line.startsWith("assistant: ")) {
                        messages.add(ChatMessage.assistant(line.substring(11)));
                    }
                }
            }

            // 添加当前用户消息
            messages.add(ChatMessage.user(prompt));

            // 调用 chat 方法
            return chat(messages);
        } catch (Exception e) {
            log.error("Ollama 调用失败", e);
            throw new LlmException("Ollama 模型调用失败：" + e.getMessage());
        }
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String ragChat(String prompt, String context) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("你是一个智能助手，请基于以下上下文回答问题。如果无法从上下文中找到答案，请直接说明。\n\n上下文： " + context));
        messages.add(ChatMessage.user(prompt));
        return chat(messages);
    }

    @Override
    public void streamChat(List<ChatMessage> messages, Consumer<String> consumer) {

    }

    // 重试失败降级
    @Recover
    public String chatRecover(Exception e, String prompt, String history) {
        log.error("模型调用重试失败：{}", e.getMessage());
        return "大模型服务繁忙，请稍后再试~";
    }

    // RAG 重试降级
    @Recover
    public String ragChatRecover(Exception e, String prompt, String context) {
        log.error("RAG模型调用重试失败：{}", e.getMessage());
        return "RAG增强问答服务繁忙，请稍后再试~";
    }

    // 为 chatRecover 添加重载方法，避免参数不匹配
    @Recover
    public String chatListRecover(Exception e, List<ChatMessage> messages) {
        log.error("模型调用重试失败：{}", e.getMessage());
        return "大模型服务繁忙，请稍后再试~";
    }

}