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
import java.util.*;
import java.util.function.Consumer;

/**
 * 阿里云通义千问大模型客户端
 * 使用 OpenAI 兼容协议
 * API文档：https://help.aliyun.com/zh/dashscope/developer-reference/compatibility-of-openai-with-dashscope
 */
@Slf4j
public class AliyunChatClient implements ChatClient {

    private static final String CHAT_API_PATH = "/chat/completions";
    
    private final RestTemplate restTemplate;
    private final ProviderEntity provider;
    private final ModelConfigEntity modelConfig;

    /**
     * 构造函数
     * @param provider 供应商配置（baseUrl、apiKey）
     * @param modelConfig 模型配置（modelKey如：qwen-turbo, qwen-plus, qwen-max）
     */
    public AliyunChatClient(ProviderEntity provider, ModelConfigEntity modelConfig) {
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

            log.info("阿里云通义千问调用 - provider: {}, model: {}, 消息数量: {}",
                    provider.getProviderKey(), modelConfig.getModelKey(), messages.size());

            // 构建请求头
            HttpHeaders headers = buildHeaders();
            
            // 转换消息格式
            List<Map<String, String>> apiMessages = convertToApiMessages(messages);
            
            // 构建请求体
            Map<String, Object> request = buildRequest(apiMessages, temperature, maxTokens);
            
            // 发送请求
            String url = buildUrl();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            log.debug("阿里云API请求URL: {}", url);
            log.debug("阿里云API请求体: {}", JSON.toJSONString(request));
            
            JSONObject response = restTemplate.postForObject(url, entity, JSONObject.class);
            return parseSyncResponse(response);

        } catch (Exception e) {
            log.error("阿里云通义千问调用失败", e);
            throw new ModelProviderException("阿里云通义千问调用失败：" + e.getMessage());
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
            
            String url = buildUrl();
            HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);
            
            return restTemplate.postForObject(url, entity, ChatResponse.class);

        } catch (Exception e) {
            log.error("阿里云通义千问调用失败", e);
            throw new ModelProviderException("阿里云通义千问调用失败：" + e.getMessage());
        }
    }

    @Override
    public void chatStream(List<ChatMessage> messages, Consumer<String> consumer) {
        chatStream(messages, null, null, consumer);
    }

    @Override
    public String chatWithTools(List<ChatMessage> messages, List<Map<String, Object>> tools,
                         String toolChoice){
        try {
            HttpHeaders headers = buildHeaders();
            List<Map<String, String>> apiMessages = convertToApiMessages(messages);

            Map<String, Object> request = new HashMap<>();
            request.put("model", modelConfig.getModelKey());
            request.put("messages", apiMessages);

            String url = buildUrl();
            if (tools != null && !tools.isEmpty()) {
                // 有工具调用，使用 OpenAI 兼容模式
                request.put("tools", tools);
                request.put("tool_choice", "auto");
                log.info("使用 OpenAI 兼容模式，tools: {}", tools.size());
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            JSONObject response = restTemplate.postForObject(url, entity, JSONObject.class);

            return parseResponse(response, tools != null);

        } catch (Exception e) {
            log.error("阿里云通义千问调用失败", e);
            throw new ModelProviderException("调用失败：" + e.getMessage());
        }
    }

    private String parseResponse(JSONObject response, boolean isToolCallMode) {
        if (response == null) {
            throw new ModelProviderException("API返回结果为空");
        }

        if (isToolCallMode) {
            // OpenAI 兼容模式，直接返回原始 JSON（包含 tool_calls）
            return response.toJSONString();
        } else {
            // DashScope 原生模式，提取 output.text
            String result = response.getJSONObject("output").getString("text");
            if (result == null) {
                throw new ModelProviderException("API返回格式错误");
            }
            return result;
        }
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
            
            String url = buildUrl();
            log.info("阿里云通义千问流式请求 URL: {}", url);

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
            log.error("阿里云通义千问流式调用失败", e);
            throw new ModelProviderException("阿里云通义千问流式调用异常：" + e.getMessage());
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
            log.error("阿里云通义千问连接测试失败", e);
            return false;
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建请求头
     * 阿里云使用 DashScope API Key，格式为 "sk-xxx"
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 阿里云 API Key 格式：sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
        if (provider.getApiKey() != null && !provider.getApiKey().isEmpty()) {
            headers.setBearerAuth(provider.getApiKey());
        }
        
        return headers;
    }

    /**
     * 构建完整URL
     * baseUrl 示例：https://dashscope.aliyuncs.com/compatible-mode/v1
     */
    private String buildUrl() {
        String baseUrl = provider.getBaseUrl();
        // 去除末尾的斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + CHAT_API_PATH;
    }

    /**
     * 构建同步请求体
     */
    private Map<String, Object> buildRequest(List<Map<String, String>> messages, Double temperature, Integer maxTokens) {
        Map<String, Object> request = new LinkedHashMap<>();
        
        // 模型名称
        request.put("model", modelConfig.getModelKey());
        request.put("messages", messages);
        
        // 参数配置
        request.put("temperature", temperature != null ? temperature : 0.7);
        request.put("max_tokens", maxTokens != null ? maxTokens : 2048);
        
        // 可选参数
        request.put("top_p", 0.95);
        request.put("enable_search", false);  // 是否启用联网搜索
        request.put("stream", false);
        
        return request;
    }

    /**
     * 构建流式请求体
     */
    private Map<String, Object> buildStreamRequest(List<Map<String, String>> messages, Double temperature, Integer maxTokens) {
        Map<String, Object> request = new LinkedHashMap<>();
        
        request.put("model", modelConfig.getModelKey());
        request.put("messages", messages);
        request.put("temperature", temperature != null ? temperature : 0.7);
        request.put("max_tokens", maxTokens != null ? maxTokens : 2048);
        request.put("top_p", 0.95);
        request.put("enable_search", false);
        request.put("stream", true);
        
        return request;
    }

    /**
     * 转换消息格式（兼容 LinkedHashMap）
     */
    private List<Map<String, String>> convertToApiMessages(List<?> messages) {
        List<Map<String, String>> apiMessages = new ArrayList<>();

        for (Object obj : messages) {
            Map<String, String> apiMsg = new HashMap<>();

            if (obj instanceof ChatMessage) {
                // 正常情况：ChatMessage 对象
                ChatMessage msg = (ChatMessage) obj;
                apiMsg.put("role", msg.getRole());
                apiMsg.put("content", msg.getContent() != null ? msg.getContent() : "");
            } else if (obj instanceof Map) {
                // HTTP 反序列化后：LinkedHashMap
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;

                String role = map.get("role") != null ? map.get("role").toString() : "";
                String content = map.get("content") != null ? map.get("content").toString() : "";

                apiMsg.put("role", role);
                apiMsg.put("content", content);
            } else {
                log.warn("未知的消息类型: {}", obj != null ? obj.getClass().getName() : "null");
                continue;
            }

            apiMessages.add(apiMsg);
        }

        return apiMessages;
    }

    /**
     * 解析同步响应
     */
    private String parseSyncResponse(JSONObject response) {
        if (response == null) {
            throw new ModelProviderException("阿里云API返回为空");
        }
        
        // 检查错误
        if (response.containsKey("error")) {
            JSONObject error = response.getJSONObject("error");
            String errorCode = error.getString("code");
            String errorMsg = error.getString("message");
            log.error("阿里云API错误: code={}, message={}", errorCode, errorMsg);
            throw new ModelProviderException("阿里云API错误: " + errorMsg);
        }
        
        if (!response.containsKey("choices")) {
            throw new ModelProviderException("阿里云API返回格式异常");
        }
        
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new ModelProviderException("阿里云API返回choices为空");
        }
        
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        if (message == null) {
            throw new ModelProviderException("阿里云API返回message为空");
        }
        
        String content = message.getString("content");
        if (content == null) {
            content = "";
        }
        
        log.info("阿里云通义千问输出内容长度: {}", content.length());
        return content;
    }

    /**
     * 处理流式响应
     */
    private void processStreamResponse(JSONObject jsonObject, Consumer<String> consumer) {
        // 检查错误
        if (jsonObject.containsKey("error")) {
            JSONObject error = jsonObject.getJSONObject("error");
            String errorMsg = error.getString("message");
            log.error("阿里云API流式错误: {}", errorMsg);
            throw new ModelProviderException("阿里云API错误: " + errorMsg);
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
        log.error("阿里云通义千问调用重试失败：{}", e.getMessage());
        return "阿里云通义千问服务繁忙，请稍后再试~";
    }
}