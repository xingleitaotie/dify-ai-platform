package com.washy.dify.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.agent.domain.AgentConfig;
import com.washy.dify.agent.domain.AgentKbBind;
import com.washy.dify.agent.domain.AgentToolBind;
import com.washy.dify.agent.domain.dto.AgentStreamChatRequest;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            // RAG
            String ragContext = "";
            if (!kbs.isEmpty()) {
                send(emitter, "🔍 检索知识库...");

                List<String> kbNames = new ArrayList<>();
                for(AgentKbBind agentKbBind : kbs){
                    kbNames.add(agentKbBind.getKbName());
                }
                Map<String,Object> requestBody = new HashMap<>();
                requestBody.put("kbs",kbNames);
                requestBody.put("query",query);
                requestBody.put("topN",5);

                Result<String> ragResult = ragFeign.searchMuchDocument(requestBody);
                if (ragResult == null || ragResult.getData() == null) {
                    ragContext = "未检索到与【" + query + "】相关的知识库内容";
                    send(emitter, "⚠️ 未检索到相关内容");
                }else {
                    ragContext = ragResult.getData();
                    send(emitter, "✅检索知识库完成");
                }
            }

            // 记忆
            String history = memoryService.buildMemoryContext(sessionId);
            send(emitter, "🧠 记忆加载完成");

            // 函数决策
            String funcPrompt = buildFuncPrompt(agent, query, tools, ragContext, history);
            ChatRequestDTO dto = new ChatRequestDTO();
            dto.setMessage(funcPrompt);
            String toolResult = "";
            Result<String> decRes = llmFeign.functionChat(dto);
            try {
                if(decRes.getCode() == 200) {
                    toolResult = decRes.getData();
                    send(emitter, "✅ 工具执行完成");
                }
            } catch (Exception ignored) {
                log.error(ignored.getMessage());
            }

            // 最终回答
            send(emitter, "📝 生成回答...\n");
            String finalPrompt = buildFinalPrompt(agent, query, ragContext, toolResult, history);

            // ========== 调用 LLM 流式接口 ==========
            String llmUrl = llmServiceUrl + "/api/llm/stream/chat";

            StreamChatRequestDTO requestDTO = new StreamChatRequestDTO();
            requestDTO.setSessionId(sessionId);
            requestDTO.setMessage(finalPrompt);

            callLlmStream(llmUrl, requestDTO, emitter);

            // 保存记忆
            memoryService.saveMemory(agentId, sessionId, query, finalPrompt);

            emitter.complete();

        } catch (Exception e) {
            log.error("流式对话异常", e);
            send(emitter, "❌ 异常：" + e.getMessage());
            emitter.completeWithError(e);
        }
    }

    /**
     * 调用 LLM 流式接口
     */
    private void callLlmStream(String url, StreamChatRequestDTO dto, SseEmitter emitter) {

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
                    send(emitter, content);
                }
            } catch (Exception e) {
                log.error("流读取失败", e);
            }
            return null;
        });
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

    private String buildFuncPrompt(AgentConfig agent, String q, List<AgentToolBind> tools, String rag, String hist) {
        return "系统：" + agent.getSystemPrompt() + "\n" +
                "历史：" + hist + "\n" +
                "知识：" + rag + "\n" +
                "工具：" + tools + "\n" +
                "问题：" + q + "\n" +
                "需要调用工具返回 {\"toolName\":\"\",\"parameters\":{}}";
    }

    private String buildFinalPrompt(AgentConfig agent, String q, String rag, String tool, String hist) {
        return "系统：" + agent.getSystemPrompt() + "\n" +
                "历史：" + hist + "\n" +
                "知识：" + rag + "\n" +
                "工具结果：" + tool + "\n" +
                "用户：" + q + "\n" +
                "请自然回答：";
    }

}