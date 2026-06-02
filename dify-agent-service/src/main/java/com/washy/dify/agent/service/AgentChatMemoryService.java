package com.washy.dify.agent.service;

import com.washy.dify.agent.domain.AgentMemory;
import com.washy.dify.common.entity.llm.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatMemoryService {

    private final AgentRedisMemoryService redisMemory;
    private final AgentMemoryService agentMemoryService;

    // ====================== 保存记忆（适配你的表结构） ======================
    public void saveMemory(Long agentId, String conversationId,
                           String userQuery, String aiReply) {
        // 1. 保存 Redis
        ChatMessage userMsg =   ChatMessage.user(userQuery);
        ChatMessage aiMsg = ChatMessage.assistant(aiReply);
        redisMemory.saveMessage(conversationId, userMsg);
        redisMemory.saveMessage(conversationId, aiMsg);

        // 2. 保存 MySQL
        AgentMemory memory = new AgentMemory();
        memory.setAgentId(agentId);
        memory.setConversationId(conversationId);
        memory.setUserQuery(userQuery);
        memory.setAiReply(aiReply);
        memory.setMemoryType("short");
        memory.setCreateTime(LocalDateTime.now());

        agentMemoryService.save(memory);
    }

    // ====================== 获取最近5轮对话（给LLM用） ======================
    public List<ChatMessage> getRecentMessages(String sessionId) {
        // 🔥 这里统一传参：sessionId + 5
        return redisMemory.getRecentMessages(sessionId, 5);
    }

    // ====================== 拼接记忆上下文 ======================
    public String buildMemoryContext(String sessionId) {
        List<ChatMessage> messages = getRecentMessages(sessionId);
        if (messages == null || messages.isEmpty()) {
            return "无历史对话";
        }

        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append(msg.getRole()).append("：").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    // ====================== 定时同步 Redis → MySQL ======================
    @Scheduled(fixedRate = 30000)
    public void syncAllToDB() {
        log.info("【定时任务】开始同步 Redis 记忆 → MySQL");

        Set<String> keys = redisMemory.getAllSessionKeys();
        if (keys == null || keys.isEmpty()) {
            log.info("【定时任务】无记忆需要同步");
            return;
        }

        for (String key : keys) {
            String conversationId = key.replace(AgentRedisMemoryService.PREFIX, "");
            List<ChatMessage> messages = redisMemory.getAllMessages(conversationId);

            for (int i = 0; i < messages.size(); i += 2) {
                if (i + 1 >= messages.size()) break;

                ChatMessage userMsg = messages.get(i);
                ChatMessage aiMsg = messages.get(i + 1);

                boolean exists = agentMemoryService.lambdaQuery()
                        .eq(AgentMemory::getConversationId, conversationId)
                        .eq(AgentMemory::getUserQuery, userMsg.getContent())
                        .exists();

                if (!exists) {
                    AgentMemory memory = new AgentMemory();
                    memory.setConversationId(conversationId);
                    memory.setUserQuery(userMsg.getContent());
                    memory.setAiReply(aiMsg.getContent());
                    memory.setMemoryType("short");
                    memory.setCreateTime(LocalDateTime.now());
                    agentMemoryService.save(memory);
                }
            }
        }
        log.info("【定时任务】同步完成 ✅");
    }
}