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

/**
 * OpenAI 协议模型客户端
 * 支持：OpenAI、DeepSeek、Moonshot、零一、通义千问、智谱AI、Llama3 等
 * @author washy
 */
@Component("openaiClient")
@Slf4j
public class OpenAiClient implements LlmClient {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private LlmProperties llmProperties;

    // 构造器
    public OpenAiClient() {
        this.restTemplate = new RestTemplate();
        this.llmProperties = new LlmProperties();
    }

    public OpenAiClient(LlmProperties properties) {
        this.restTemplate = new RestTemplate();
        this.llmProperties = properties;
    }

    @Override
    public void setLlmProperties(LlmProperties properties) {
        this.llmProperties = properties;
    }

    private static final String OPENAI_CHAT_API = "/v1/chat/completions";

    @Override
    public String chat(List<ChatMessage> messages) {
        return "";
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
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String chat(String prompt, String history) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();

            if (history != null && !history.isEmpty()) {
                Map<String, String> hisMap = new HashMap<>();
                hisMap.put("role", "user");
                hisMap.put("content", history);
                messages.add(hisMap);
            }

            Map<String, String> userMap = new HashMap<>();
            userMap.put("role", "user");
            userMap.put("content", prompt);
            messages.add(userMap);

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
                    llmProperties.getBaseUrl() + OPENAI_CHAT_API,
                    entity,
                    Map.class
            );

            if (response == null || !response.containsKey("choices")) {
                throw new LlmException("OpenAI 模型返回格式异常");
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices.isEmpty()) {
                throw new LlmException("OpenAI 返回内容为空");
            }

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            return message.get("content").toString();
        } catch (Exception e) {
            log.error("OpenAI 调用失败", e);
            throw new LlmException("OpenAI 模型调用失败：" + e.getMessage());
        }
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public String ragChat(String prompt, String context) {
        String ragPrompt = "请根据上下文回答，不要编造。上下文：" + context + "\n问题：" + prompt;
        return chat(ragPrompt,"");
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