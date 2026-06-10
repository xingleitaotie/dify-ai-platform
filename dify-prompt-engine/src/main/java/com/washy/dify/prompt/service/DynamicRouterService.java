package com.washy.dify.prompt.service;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.LlmFeignClient;
import com.washy.dify.feign.client.RagFeignClient;
import com.washy.dify.prompt.entity.PromptTemplateEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 动态路由服务 - 根据用户查询智能选择最合适的提示词模板
 */
@Slf4j
@Service
public class DynamicRouterService {
    
    @Autowired
    private RagFeignClient ragFeignClient;
    
    @Autowired
    private LlmFeignClient llmFeignClient;
    
    @Autowired
    private PromptTemplateService templateService;
    
    /**
     * 根据用户查询路由到最合适的模板
     * @param userQuery 用户查询
     * @return 选中的模板，如果没有匹配则返回null
     */
    public PromptTemplateEntity route(String userQuery) {
        log.info("开始动态路由: query={}", userQuery);
        
        try {
            // 1. 从向量库检索相似模板
            Map<String, Object> searchRequest = new HashMap<>();
            searchRequest.put("query", userQuery);
            searchRequest.put("topK", 5);
            
            Result<List<Map<String, Object>>> searchResult = ragFeignClient.searchPromptTemplates(searchRequest);
            
            if (searchResult == null || searchResult.getCode() != 200 || searchResult.getData() == null || searchResult.getData().isEmpty()) {
                log.warn("向量检索未找到相似模板，使用默认模板");
                return getDefaultTemplate();
            }
            
            List<Map<String, Object>> candidates = searchResult.getData();
            log.info("向量检索到 {} 个候选模板", candidates.size());
            
            // 2. 如果只有一个候选且相似度足够高，直接返回
            if (candidates.size() == 1) {
                double score = Double.parseDouble(candidates.get(0).getOrDefault("score", "0").toString());
                if (score > 0.85) {
                    String templateId = (String) candidates.get(0).get("templateId");
                    log.info("高置信度单一候选，直接使用: templateId={}, score={}", templateId, score);
                    return templateService.getTemplate(templateId);
                }
            }
            
            // 3. 使用轻量级LLM进行路由决策
            String selectedTemplateId = llmRouteDecision(userQuery, candidates);
            
            if (selectedTemplateId == null) {
                log.warn("LLM路由决策失败，使用相似度最高的模板");
                String bestTemplateId = (String) candidates.get(0).get("templateId");
                return templateService.getTemplate(bestTemplateId);
            }
            
            // 4. 获取并返回选中的模板
            PromptTemplateEntity selectedTemplate = templateService.getTemplate(selectedTemplateId);
            if (selectedTemplate == null) {
                log.warn("选中的模板不存在: {}", selectedTemplateId);
                return getDefaultTemplate();
            }
            
            // 5. 异步更新使用次数
            templateService.incrementUseCount(selectedTemplateId);
            
            log.info("路由决策完成: 选中模板={} ({})", selectedTemplate.getName(), selectedTemplate.getId());
            return selectedTemplate;
            
        } catch (Exception e) {
            log.error("动态路由失败", e);
            return getDefaultTemplate();
        }
    }
    
    /**
     * LLM路由决策
     */
    private String llmRouteDecision(String userQuery, List<Map<String, Object>> candidates) {
        try {
            // 构建候选模板信息
            StringBuilder candidatesInfo = new StringBuilder();
            for (int i = 0; i < candidates.size(); i++) {
                Map<String, Object> c = candidates.get(i);
                candidatesInfo.append(String.format(
                    "%d. 模板名称：%s\n   模板类型：%s\n   分类：%s\n   标签：%s\n   描述：%s\n\n",
                    i + 1,
                    c.getOrDefault("templateName", "未知"),
                    c.getOrDefault("templateType", "CUSTOM"),
                    c.getOrDefault("category", "无"),
                    c.getOrDefault("tags", "无"),
                    c.getOrDefault("metadata", new HashMap<>())
                ));
            }
            
            // 构建路由Prompt
            String routePrompt = String.format(
                "你是一个智能路由器，需要根据用户问题选择最合适的提示词模板。\n\n" +
                "【用户问题】\n%s\n\n" +
                "【候选模板】\n%s\n\n" +
                "【任务】\n" +
                "1. 分析用户问题的核心意图\n" +
                "2. 从候选模板中选择最匹配的一个\n" +
                "3. 只返回模板序号（1-%d），不要返回任何其他内容\n\n" +
                "【示例输出】\n" +
                "2",
                userQuery, candidatesInfo.toString(), candidates.size()
            );
            
            // 调用轻量级模型（temperature=0.1 保证决策稳定性）
            ChatMessage systemMsg =
                ChatMessage.system(routePrompt);
            List<ChatMessage> messages =
                Collections.singletonList(systemMsg);
            
            ChatRequestDTO request = new ChatRequestDTO();
            request.setMessages(messages);
            
            Map<String, Object> params = new HashMap<>();
            params.put("temperature", 0.1);  // 低温度保证决策稳定
            request.setParams(params);
            
            Result<String> result = llmFeignClient.chat(request);
            
            if (result != null && result.getCode() == 200 && result.getData() != null) {
                String response = result.getData().trim();
                try {
                    int selectedIndex = Integer.parseInt(response) - 1;
                    if (selectedIndex >= 0 && selectedIndex < candidates.size()) {
                        return (String) candidates.get(selectedIndex).get("templateId");
                    }
                } catch (NumberFormatException e) {
                    log.warn("路由响应格式错误: {}", response);
                }
            }
            
        } catch (Exception e) {
            log.error("LLM路由决策异常", e);
        }
        
        return null;
    }
    
    /**
     * 获取默认模板（通用聊天模板）
     */
    private PromptTemplateEntity getDefaultTemplate() {
        List<PromptTemplateEntity> templates = templateService.getActiveTemplatesByType("GENERAL");
        if (templates != null && !templates.isEmpty()) {
            return templates.get(0);
        }
        
        // 如果没有GENERAL类型，返回第一个ACTIVE模板
        List<PromptTemplateEntity> allActive = templateService.listAllTemplates().stream()
            .filter(t -> "ACTIVE".equals(t.getStatus()))
            .collect(Collectors.toList());
        
        if (!allActive.isEmpty()) {
            return allActive.get(0);
        }
        
        return null;
    }
}