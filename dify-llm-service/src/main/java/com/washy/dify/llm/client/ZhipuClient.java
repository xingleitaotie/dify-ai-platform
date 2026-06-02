package com.washy.dify.llm.client;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequest;
import com.washy.dify.common.entity.llm.ChatResponse;
import com.washy.dify.llm.config.LlmProperties;
import com.washy.dify.llm.exception.LlmException;
import com.washy.dify.llm.service.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component("zhipuClient")
@Slf4j
public class ZhipuClient implements LlmClient {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private LlmProperties llmProperties;

    // 构造器
    public ZhipuClient() {
        this.restTemplate = new RestTemplate();
        this.llmProperties = new LlmProperties();
    }

    public ZhipuClient(LlmProperties properties) {
        this.restTemplate = new RestTemplate();
        this.llmProperties = properties;
    }

    @Override
    public void setLlmProperties(LlmProperties properties) {
        this.llmProperties = properties;
    }

    private static final String ZHIPU_API = "/api/paas/v4/chat/completions";

    @Override
    public String chat(List<ChatMessage> messages) {

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", llmProperties.getModelName());
            request.put("temperature", llmProperties.getTemperature());
            request.put("max_tokens", llmProperties.getMaxTokens());
            request.put("messages", messages);
            request.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmProperties.getApiKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            Map<String, Object> response = restTemplate.postForObject(
                    llmProperties.getBaseUrl() + ZHIPU_API,
                    entity,
                    Map.class
            );

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return message.get("content").toString();
        } catch (Exception e) {
            log.error("智谱AI调用失败", e);
            throw new LlmException("智谱AI调用失败：" + e.getMessage());
        }

    }

    @Override
    public String chat(List<ChatMessage> messages, Double temperature, Integer maxTokens) {
        return "";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return null;
    }

    @Override
    public void chatStream(List<ChatMessage> messages, Consumer<String> consumer) {

    }

    @Override
    public void chatStream(List<ChatMessage> messages, Double temperature, Integer maxTokens, Consumer<String> consumer) {

    }

    @Override
    public Map<String,Object> chat(Map<String, Object> params) {
        return new HashMap<>();
    }

    @Override
    public String chat(String prompt, String history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(prompt));
        return chat(messages);
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String ragChat(String prompt, String context) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("上下文：" + context + "\n"));
        messages.add(ChatMessage.user(" 问题：" + prompt));
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
}