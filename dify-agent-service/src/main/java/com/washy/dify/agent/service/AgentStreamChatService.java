package com.washy.dify.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.agent.domain.AgentConfig;
import com.washy.dify.agent.domain.AgentKbBind;
import com.washy.dify.agent.domain.AgentToolBind;
import com.washy.dify.agent.domain.dto.AgentStreamChatRequest;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.entity.llm.StreamChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.FunctionFeignClient;
import com.washy.dify.feign.client.LlmFeignClient;
import com.washy.dify.feign.client.RagFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStreamChatService {

    private final AgentConfigService agentConfigService;
    private final AgentToolBindService agentToolBindService;
    private final AgentKbBindService agentKbBindService;
    private final AgentChatMemoryService memoryService;
    private final LlmFeignClient llmFeign;
    private final RagFeignClient ragFeign;
    private final FunctionFeignClient functionFeign;

    @Value("${llm.service-url}")
    private String llmServiceUrl;
    @Value("${llm.action}")
    private String action;

    private final RestTemplate restTemplate;

    // 超时时间（秒）
    private static final long RAG_TIMEOUT_SECONDS = 120;
    private static final long FUNCTION_TIMEOUT_SECONDS = 120;

    public void streamChat(AgentStreamChatRequest req, SseEmitter emitter) {
        Long agentId = req.getAgentId();
        String query = req.getQuery();
        String sessionId = req.getSessionId() == null ? "default" : req.getSessionId();

        try {
            send(emitter, "🤖 Agent 启动中...");
            AgentConfig agent = agentConfigService.getById(agentId);

            send(emitter, "📦 加载工具...");
            List<AgentToolBind> tools = agentToolBindService.lambdaQuery()
                    .eq(AgentToolBind::getAgentId, agentId)
                    .eq(AgentToolBind::getIsEnabled, 1)
                    .list();

            send(emitter, "📚 加载知识库...");
            List<AgentKbBind> kbs = agentKbBindService.lambdaQuery()
                    .eq(AgentKbBind::getAgentId, agentId)
                    .list();

            // 记忆
            String history = memoryService.buildMemoryContext(sessionId);
            send(emitter, "🧠 记忆加载完成");

            // 获取模型配置ID
            Long modelConfigId;
            if (req.getModelConfigId() != null && !req.getModelConfigId().isEmpty()) {
                modelConfigId = Long.parseLong(req.getModelConfigId());
            } else {
                modelConfigId = null;
            }

            // ========== 并行执行 RAG 和 Function Calling ==========
            send(emitter, "🔍 并行检索知识库 & 调用工具...");

            // 用于存储结果的容器
            AtomicReference<String> ragContextRef = new AtomicReference<>("");
            AtomicReference<String> toolResultRef = new AtomicReference<>("");
            AtomicReference<String> ragErrorRef = new AtomicReference<>();
            AtomicReference<String> functionErrorRef = new AtomicReference<>();

            // 1. RAG 检索任务
            CompletableFuture<Void> ragFuture = CompletableFuture.runAsync(() -> {
                if (!kbs.isEmpty()) {
                    try {
                        List<String> kbNames = new ArrayList<>();
                        for (AgentKbBind agentKbBind : kbs) {
                            kbNames.add(agentKbBind.getKbName());
                        }
                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("kbs", kbNames);
                        requestBody.put("query", query);
                        requestBody.put("topN", 5);

                        Result<String> ragResult = ragFeign.searchMuchDocument(requestBody);
                        if (ragResult != null && ragResult.getCode() == 200 && ragResult.getData() != null) {
                            ragContextRef.set(ragResult.getData());
                            send(emitter, "  📚 知识库检索完成");
                        } else {
                            ragContextRef.set("未检索到与【" + query + "】相关的知识库内容");
                            ragErrorRef.set("检索无结果");
                            send(emitter, "  ⚠️ 知识库未检索到相关内容");
                        }
                    } catch (Exception e) {
                        log.error("RAG检索失败", e);
                        ragContextRef.set("知识库检索失败：" + e.getMessage());
                        ragErrorRef.set(e.getMessage());
                        send(emitter, "  ❌ 知识库检索失败");
                    }
                } else {
                    ragContextRef.set("");
                    send(emitter, "  📚 无知识库配置，跳过检索");
                }
            });
            // 2. Function Calling 任务
            CompletableFuture<Void> functionFuture = CompletableFuture.runAsync(() -> {
                try {
                    ChatRequestDTO dto = new ChatRequestDTO();
                    dto.setMessage(buildUserPrompt(query));
                    dto.setConfigId(modelConfigId);

                    Result<String> decRes = llmFeign.functionChat(dto);
                    if (decRes != null && decRes.getCode() == 200 && decRes.getData() != null) {
                        toolResultRef.set(decRes.getData());
                        send(emitter, "  🔧 工具调用完成");
                    } else {
                        toolResultRef.set("");
                        functionErrorRef.set(decRes != null ? decRes.getData() : "返回结果为空");
                        send(emitter, "  ⚠️ 工具调用失败：" + functionErrorRef.get());
                    }
                } catch (Exception e) {
                    log.error("Function调用失败", e);
                    toolResultRef.set("");
                    functionErrorRef.set(e.getMessage());
                    send(emitter, "  ❌ 工具调用异常：" + e.getMessage());
                }
            });

            // 等待两个任务完成（带超时）
            CompletableFuture.allOf(ragFuture, functionFuture)
                    .get(Math.max(RAG_TIMEOUT_SECONDS, FUNCTION_TIMEOUT_SECONDS), TimeUnit.SECONDS);

            String ragContext = ragContextRef.get();
            String toolResult = toolResultRef.get();

            send(emitter, "✅ 检索和工具调用完成，开始生成回答...\n");

            // ========== 最终回答 ==========
            String finalAnswer = generateFinalAnswer(agent, query, ragContext, toolResult, history,
                    modelConfigId, sessionId, emitter);

            // 保存记忆（保存最终回答）
            memoryService.saveMemory(agentId, sessionId, query, finalAnswer);

            emitter.complete();

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("并行任务超时", e);
            send(emitter, "❌ 请求超时，请稍后重试");
            emitter.complete();
        } catch (Exception e) {
            log.error("流式对话异常", e);
            send(emitter, "❌ 异常：" + e.getMessage());
            emitter.completeWithError(e);
        }
    }

    /**
     * 生成最终回答
     * @return 完整的回答内容（用于保存记忆）
     */
    private String generateFinalAnswer(AgentConfig agent, String query, String ragContext,
                                       String toolResult, String history, Long modelConfigId,
                                       String sessionId, SseEmitter emitter) {

        send(emitter, "📝 生成回答...\n");

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildFinalSystemPrompt(agent, ragContext, toolResult, history)));
        messages.add(ChatMessage.user(buildUserPrompt(query)));

        // 调用 LLM 流式接口
        String llmUrl = llmServiceUrl + "/api/llm/stream/chat";

        StreamChatRequestDTO requestDTO = new StreamChatRequestDTO();
        requestDTO.setSessionId(sessionId);
        requestDTO.setMessages(messages);
        requestDTO.setConfigId(modelConfigId);

        // 收集完整回答并流式输出
        StringBuilder fullAnswer = new StringBuilder();
        callLlmStreamWithCallback(llmUrl, requestDTO, emitter, fullAnswer);

        return fullAnswer.toString();
    }

    /**
     * 调用 LLM 流式接口（带回调收集完整回答）
     */
    private void callLlmStreamWithCallback(String url, StreamChatRequestDTO dto,
                                           SseEmitter emitter, StringBuilder fullAnswer) {
        restTemplate.execute(url, HttpMethod.POST, request -> {
            ObjectMapper om = new ObjectMapper();
            byte[] bytes = om.writeValueAsBytes(dto);
            request.getBody().write(bytes);
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        }, response -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    // 解析 SSE 格式
                    String content = line;
                    if (content.startsWith("data:")) {
                        content = content.substring("data:".length()).trim();
                    }
                    if (content.isEmpty() || "[DONE]".equals(content)) {
                        continue;
                    }
                    // 收集完整回答
                    fullAnswer.append(content);
                    // 流式发送
                    send(emitter, content);
                }
            } catch (Exception e) {
                log.error("流读取失败", e);
                send(emitter, "\n❌ 读取响应失败");
            }
            return null;
        });
    }

    /**
     * 调用 LLM 流式接口（兼容原方法）
     */
    private void callLlmStream(String url, StreamChatRequestDTO dto, SseEmitter emitter) {
        callLlmStreamWithCallback(url, dto, emitter, new StringBuilder());
    }

    /**
     * 安全发送消息
     */
    private void send(SseEmitter emitter, String message) {
        try {
            emitter.send(message);
        } catch (IOException e) {
            log.error("发送消息失败", e);
        }
    }

    private String buildUserPrompt(String q) {
        return "用户问题：" + q + "\n";
    }

    private String buildFinalSystemPrompt(AgentConfig agent, String rag, String tool, String hist) {
        StringBuilder sb = new StringBuilder();

        // Agent 的系统提示词
        if (StringUtils.hasText(agent.getSystemPrompt())) {
            sb.append(agent.getSystemPrompt()).append("\n");
        }

        // 添加默认指令
        sb.append("请基于以下信息回答用户问题：\n");
        sb.append("1. 结合使用【知识库内容】提供专业建议\n");
        sb.append("1. 结合【工具结果】中的实时数据\n");
        sb.append("3. 参考【历史对话】保持上下文连贯\n");
        sb.append("4. **部分回答原则**：如果只能回答用户问题中的部分内容，请如实回答有信息的部分，对于缺失信息的部分明确告知\"暂无相关信息\"\n");
        sb.append("5. 禁止在没有**任何**相关信息时回答\"我不知道\"，应说明具体缺失什么信息\n");
        sb.append("6. 如果信息完全不足，请说明：\"根据现有信息，我只能回答[某部分]，关于[某部分]暂无数据\"\n\n");

        sb.append("【知识库内容】\n").append(StringUtils.hasText(rag) ? rag : "无相关知识").append("\n\n");
        sb.append("【工具结果】\n").append(StringUtils.hasText(tool) ? tool : "未调用工具").append("\n\n");
        sb.append("【历史对话】\n").append(StringUtils.hasText(hist) ? hist : "无历史对话").append("\n\n");

        return sb.toString();
    }

}