package com.washy.dify.llm.service;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.entity.rag.RagRetrieveVO;
import com.washy.dify.llm.util.RagServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Washy
 * @date 2026-04-09 17:26
 * @description
 */
@Service
@Slf4j
public class RagQaService {

    @Resource
    private RagServiceUtil ragServiceUtil;

    @Resource
    private LlmService llmService;

    private static final int MAX_CONTEXT_LENGTH = 2000;

    /**
     * RAG问答（支持指定模型）
     */
    public String ragQa(ChatRequestDTO request) {
        LlmClient client = llmService.getClient(request);

        String query = request.getMessage();
        Integer topK = request.getParams() != null && request.getParams().get("topK") != null
                ? (Integer) request.getParams().get("topK") : 3;

        // 1. 调用RAG服务检索
        List<RagRetrieveVO> retrieveList = ragServiceUtil.retrieveDocument(query, topK);

        // 2. 组装上下文
        String context = retrieveList.stream()
                .map(RagRetrieveVO::getDocument)
                .collect(Collectors.joining("\n"));

        // 3. 无知识库时直接回答
        if (context.isEmpty()) {
            List<ChatMessage> noknowlegdeMessages = new ArrayList<>();
            noknowlegdeMessages.add(ChatMessage.system(buildRagUnionSystemPrompt()));
            noknowlegdeMessages.add(ChatMessage.system(buildRagUserPrompt(query)));
            return "未找到相关知识库内容，直接回答：\n" + client.chat(noknowlegdeMessages);
        }

        // 上下文裁剪
        if (context.length() > MAX_CONTEXT_LENGTH) {
            context = context.substring(0, MAX_CONTEXT_LENGTH);
            log.warn("RAG 上下文超长，已自动裁剪");
        }
        // 4. 构造RAG提示词
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildRagSystemPrompt(context)));
        messages.add(ChatMessage.system(buildRagUserPrompt(query)));

        // 5. 调用大模型
        return client.chat(messages);
    }

    private String buildRagUnionSystemPrompt() {
        return "你是一个专业的智能问答助手，可以根据客户的问题进行精确回答。\n" +
                "请直接给出精准答案，不要编造信息，答案简洁清晰。";
    }

    private String buildRagSystemPrompt(String context) {
        return "你是一个基于知识库的智能助手，请严格根据提供的知识库内容回答问题。\n" +
                "【知识库内容】：\n" + context + "\n" +
                "请直接给出精准答案，不要编造信息，答案简洁清晰。";
    }

    private String buildRagUserPrompt(String query) {
        return "【用户问题】：" + query + "\n";
    }
}
