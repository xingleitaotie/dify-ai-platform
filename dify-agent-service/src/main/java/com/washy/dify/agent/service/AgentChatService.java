package com.washy.dify.agent.service;

import com.alibaba.fastjson2.JSON;
import com.washy.dify.agent.domain.AgentConfig;
import com.washy.dify.agent.domain.AgentKbBind;
import com.washy.dify.agent.domain.AgentToolBind;
import com.washy.dify.agent.domain.dto.AgentExecuteRequest;
import com.washy.dify.agent.domain.dto.ToolCallRequest;
import com.washy.dify.agent.util.AgentMessageBuilder;
import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.exception.GlobalExceptionHandler;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.FunctionFeignClient;
import com.washy.dify.feign.client.LlmFeignClient;
import com.washy.dify.feign.client.RagFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准 Agent 智能编排（RAG + Function Calling）
 * 流程严格按照：用户提问 → 加载上下文 → LLM决策 → 执行工具 → 回传LLM → 生成答案
 */
@Slf4j
@Service
public class AgentChatService {

    @Resource
    private AgentConfigService agentConfigService;
    @Resource
    private AgentToolBindService agentToolBindService;
    @Resource
    private AgentKbBindService agentKbBindService;
    @Resource
    private AgentMessageBuilder messageBuilder;

    // 你的三个服务
    @Resource
    private LlmFeignClient llmFeign;
    @Resource
    private RagFeignClient ragFeign;
    @Resource
    private FunctionFeignClient functionFeign;

    /**
     * 统一入口：复用 AgentExecuteRequest
     */
    public String chat(AgentExecuteRequest request) {
        Long agentId = request.getAgentId();
        String query = request.getQuery();

        log.info("======================================");
        log.info("【1】用户提问：agentId={}，query={}", agentId, query);

        // 1. 加载Agent
        AgentConfig agent = agentConfigService.getById(agentId);
        log.info("【2】加载Agent：{}", agent.getAgentName());

        // 2. 加载工具
        List<AgentToolBind> tools = agentToolBindService.lambdaQuery()
                .eq(AgentToolBind::getAgentId, agentId)
                .eq(AgentToolBind::getIsEnabled, 1)
                .list();
        log.info("【3】加载工具：{} 个", tools.size());

        // 3. 加载知识库
        List<AgentKbBind> kbs = agentKbBindService.lambdaQuery()
                .eq(AgentKbBind::getAgentId, agentId)
                .list();
        log.info("【4】加载知识库：{} 个", kbs.size());

        // 4. RAG检索
        String ragContext = "";
        if (!kbs.isEmpty()) {
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
                return "RAG中未获取到与["+query+"]相关信息";
            }

            // 2. 返回结果给AI大模型
            ragContext = ragResult.getData();
            log.info("【5】RAG检索结果：{}", ragContext);
        }

        // 5. 构建Prompt（包含工具+知识库）
        String prompt = buildFunctionPrompt(agent, query, tools, ragContext);
        log.info("【6】发送给LLM函数决策");

        // 6. LLM决策：是否调用工具（/api/llm/chat/function）
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessage(prompt);
        Result<String> llmFuncResult = llmFeign.functionChat(dto);
        String llmDecision = llmFuncResult.getData();
        log.info("【7】LLM决策返回：{}", llmDecision);

        // 7. 解析是否需要调用工具
        ToolCallRequest toolCall = null;
        try {
            toolCall = JSON.parseObject(llmDecision, ToolCallRequest.class);
        } catch (Exception e) {
            log.info("【8】无需调用工具，直接返回回答");
            return llmDecision;
        }

        // 8. 执行工具
        log.info("【8】开始执行工具：{}", toolCall.getToolName());

        // 解析函数调用参数
        FunctionCallRequest functionRequest = parseFunctionCall(toolCall.getToolName(),toolCall.getParameters());
        // 执行函数
        Result<FunctionExecuteResult> funcResult = functionFeign.invokeFunction(functionRequest);


        Object toolResult = funcResult.getData();
        log.info("【9】工具执行结果：{}", toolResult);

        // 9. 最终对话：整合结果给LLM生成答案
        List<ChatMessage> messages = messageBuilder.buildForAgent(agent, query, ragContext, toolResult);
        ChatRequestDTO requestDTO  = new ChatRequestDTO();
        requestDTO.setMessages(messages);
        Result<String> finalAnswer = llmFeign.chat(requestDTO);
        log.info("【10】最终回答：{}", finalAnswer.getData());

        log.info("======================================\n");
        return finalAnswer.getData();
    }

    /**
     * 解析大模型返回的 JSON，转为 FunctionRequest
     */
    private FunctionCallRequest parseFunctionCall(String toolName,Object params) {
        try {
            FunctionCallRequest request = new FunctionCallRequest();
            request.setFunctionName(toolName);
            request.setParameters(params);
            return request;
        } catch (Exception e) {
            throw new GlobalExceptionHandler("解析函数调用失败：" + e.getMessage());
        }
    }

    /**
     * 函数决策专用Prompt
     */
    private String buildFunctionPrompt(AgentConfig agent, String query, List<AgentToolBind> tools, String ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("系统提示：").append(agent.getSystemPrompt()).append("\n");
        sb.append("知识库：").append(ragContext).append("\n");
        sb.append("你可以使用以下工具：").append("\n");
        for (AgentToolBind t : tools) {
            sb.append("- ").append(t.getToolName()).append("：").append(t.getToolDesc()).append("\n");
        }
        sb.append("用户问题：").append(query).append("\n");
        sb.append("需要调用工具请返回JSON格式：{\"toolName\":\"x\",\"parameters\":{}}，不需要则直接回答");
        return sb.toString();
    }

    /**
     * 最终回答Prompt
     */
    private String buildFinalPrompt(AgentConfig agent, String query, String rag, Object tool) {
        return "系统提示：" + agent.getSystemPrompt() + "\n" +
                "检索信息：" + rag + "\n" +
                "工具结果：" + tool + "\n" +
                "用户问题：" + query + "\n" +
                "请整合信息，自然回答用户";
    }

}