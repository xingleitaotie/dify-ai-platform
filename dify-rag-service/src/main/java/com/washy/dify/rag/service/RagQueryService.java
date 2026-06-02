package com.washy.dify.rag.service;

import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.LlmFeignClient;
import com.washy.dify.rag.factory.VectorStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagQueryService {

    @Autowired
    private VectorStoreFactory vectorStoreFactory;

    @Resource
    private LlmFeignClient llmFeignClient;

    // ==================== 配置常量 ====================

    // 默认检索数量
    private static final int DEFAULT_TOP_K = 5;

    // 单路检索数量（原问题检索）
    private static final int SINGLE_PATH_TOP_K = 5;

    // 扩展检索时每路数量
    private static final int EXPAND_TOP_K = 3;

    // 扩展查询最大数量
    private static final int MAX_EXPAND_QUERIES = 2;

    // 相似度阈值（距离 < 阈值才保留）
    // cosine距离: 0.6 表示夹角约53度，相关性中等
    private static final double SIMILARITY_THRESHOLD = 0.6;

    // 结果充足判断阈值
    private static final int SUFFICIENT_RESULT_COUNT = 2;
    // 结果充足判断阈值（距离 < 此值认为结果充足）
    private static final double SUFFICIENT_SCORE_THRESHOLD = 0.4;  // 距离小于0.4才认为充足

    // ==================== 原有方法（保持兼容） ====================

    /**
     * 完整 RAG 查询流程（带回答生成）
     */
    public RAGResponse ragQuery(String userQuestion, String kbName, Integer topK, String configId) {
        long startTime = System.currentTimeMillis();
        RAGResponse response = new RAGResponse();
        response.setOriginalQuestion(userQuestion);
        response.setKbName(kbName != null ? kbName : "default");

        try {
            // 混合检索
            int actualTopK = topK != null ? topK : DEFAULT_TOP_K;
            List<RetrievedDocument> finalDocs = hybridRetrieve(userQuestion, kbName, actualTopK, configId);

            response.setRetrievedDocs(finalDocs);
            log.info("最终检索完成: 共找到 {} 条相关文档", finalDocs.size());

            // 构建上下文
            String context = buildContext(finalDocs);
            response.setContextLength(context.length());

            // 生成专业回答
            String answer = generateAnswer(userQuestion, context, finalDocs, configId);
            response.setAnswer(answer);

        } catch (Exception e) {
            log.error("RAG查询失败", e);
            response.setError(e.getMessage());
            response.setAnswer("查询失败：" + e.getMessage());
        }

        response.setTotalTime(System.currentTimeMillis() - startTime);
        return response;
    }

    /**
     * 简化版 RAG（直接检索，无扩展）
     */
    public String simpleRAG(String question, String kbName) {
        List<Map<String, Object>> results = searchVectors(kbName, question, 3);

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> r = results.get(i);
            context.append(String.format("【参考 %d】%s\n\n", i + 1, r.get("document")));
        }

        String prompt = String.format(
                "根据以下文档内容回答问题。如果找不到相关信息，请如实说明。\n\n" +
                        "文档：\n%s\n问题：%s\n请用中文回答。",
                context, question
        );
        ChatRequestDTO request = new ChatRequestDTO();
        request.setMessage(prompt);
        Result<String> response = llmFeignClient.chat(request);

        if (response.getCode() == 200) {
            return response.getData().trim();
        }
        return "查询失败";
    }

    // ==================== 工作流专用方法 ====================

    /**
     * 工作流专用：只查询知识库，返回格式化的提示词（不调用大模型生成回答）
     * 使用混合检索策略
     *
     * @param query 查询问题
     * @param kbName 知识库名称（可选）
     * @param topK 返回文档数量
     * @param configId 模型配置ID（用于查询扩展）
     * @return 格式化的提示词，可直接用于LLM节点
     */
    public String queryForWorkflow(String query, String kbName, Integer topK, String configId) {
        long startTime = System.currentTimeMillis();
        int actualTopK = topK != null ? topK : DEFAULT_TOP_K;

        log.info("工作流RAG查询开始: query={}, kbName={}, topK={}", query, kbName, actualTopK);

        try {
            // 混合检索
            List<RetrievedDocument> finalDocs = hybridRetrieve(query, kbName, actualTopK, configId);

            // 构建格式化的提示词
            String formattedPrompt = buildRagPrompt(query, finalDocs);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("工作流RAG查询完成: 找到 {} 条文档, 耗时 {}ms", finalDocs.size(), totalTime);

            return formattedPrompt;

        } catch (Exception e) {
            log.error("工作流RAG查询失败", e);
            return buildFallbackPrompt(query, e.getMessage());
        }
    }

    /**
     * 工作流专用：只查询知识库，返回拼接好的知识库内容（不包含提示词）
     *
     * @param query 查询问题
     * @param kbName 知识库名称（可选）
     * @param topK 返回文档数量
     * @return 拼接好的知识库内容字符串，可直接用于LLM节点的上下文
     */
    public String queryForWorkflow(String query, String kbName, Integer topK) {
        long startTime = System.currentTimeMillis();
        int actualTopK = topK != null ? topK : DEFAULT_TOP_K;

        log.info("工作流RAG查询开始: query={}, kbName={}, topK={}", query, kbName, actualTopK);

        try {
            // 直接检索
            List<RetrievedDocument> docs = retrieveDocuments(query, kbName, actualTopK);

            // 只返回拼接好的知识库内容，不包含提示词
            String knowledgeContent = buildKnowledgeContent(docs);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("工作流RAG查询完成: 找到 {} 条文档, 耗时 {}ms", docs.size(), totalTime);

            return knowledgeContent;

        } catch (Exception e) {
            log.error("工作流RAG查询失败", e);
            return "";
        }
    }

    /**
     * 工作流专用：返回结构化的检索结果（包含文档内容和相似度分数）
     * 用于前端测试检索功能
     */
    public Map<String, Object> queryForWorkflowStructured(String query, String kbName, Integer topK) {
        long startTime = System.currentTimeMillis();
        int actualTopK = topK != null ? topK : DEFAULT_TOP_K;

        log.info("结构化RAG查询开始: query={}, kbName={}, topK={}", query, kbName, actualTopK);

        List<Map<String, Object>> details = new ArrayList<>();

        try {
            List<RetrievedDocument> docs = retrieveDocuments(query, kbName, actualTopK);

            for (RetrievedDocument doc : docs) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("document", doc.getContent());
                // 将距离转换为相似度分数（0-1之间，越大越相似）
                double similarityScore = 1.0 - (doc.getDistance() / 2.0);
                similarityScore = Math.max(0, Math.min(1, similarityScore));
                detail.put("score", Math.round(similarityScore * 100) / 100.0);
                details.add(detail);
            }

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("结构化RAG查询完成: 找到 {} 条文档, 耗时 {}ms", details.size(), totalTime);

        } catch (Exception e) {
            log.error("结构化RAG查询失败", e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("details", details);
        return result;
    }

    /**
     * 工作流专用：批量查询多个知识库
     *
     * @param query 查询问题
     * @param kbNames 知识库名称列表
     * @param topK 每个知识库返回文档数量
     * @return 拼接好的知识库内容字符串
     */
    public String queryMultipleForWorkflow(String query, List<String> kbNames, Integer topK) {
        if (kbNames == null || kbNames.isEmpty()) {
            return queryForWorkflow(query, null, topK);
        }

        int actualTopK = topK != null ? topK : DEFAULT_TOP_K;
        List<RetrievedDocument> allDocs = new ArrayList<>();

        for (String kbName : kbNames) {
            try {
                List<RetrievedDocument> docs = retrieveDocuments(query, kbName, actualTopK);
                allDocs.addAll(docs);
                log.info("知识库 {} 检索到 {} 条文档", kbName, docs.size());
            } catch (Exception e) {
                log.error("检索知识库 {} 失败: {}", kbName, e.getMessage());
            }
        }

        // 去重排序
        List<RetrievedDocument> finalDocs = deduplicateAndRank(allDocs);

        // 限制数量
        if (finalDocs.size() > actualTopK) {
            finalDocs = finalDocs.subList(0, actualTopK);
        }

        // 返回拼接好的知识库内容
        return buildKnowledgeContentWithSource(finalDocs, kbNames);
    }

    /**
     * 构建纯知识库内容（不包含提示词）
     */
    private String buildKnowledgeContent(List<RetrievedDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }

        StringBuilder content = new StringBuilder();

        for (int i = 0; i < docs.size(); i++) {
            RetrievedDocument doc = docs.get(i);
            if (i > 0) {
                content.append("\n\n");
            }
            content.append(doc.getContent());
        }

        return content.toString();
    }

    /**
     * 构建带来源的知识库内容
     */
    private String buildKnowledgeContentWithSource(List<RetrievedDocument> docs, List<String> kbNames) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }

        StringBuilder content = new StringBuilder();

        // 添加知识库来源说明
        if (kbNames != null && !kbNames.isEmpty()) {
            content.append("【检索知识库】").append(String.join("、", kbNames)).append("\n\n");
        }

        for (int i = 0; i < docs.size(); i++) {
            RetrievedDocument doc = docs.get(i);
            Map<String, Object> meta = doc.getMetadata();

            // 添加来源信息
            content.append("【文档 ").append(i + 1).append("】");
            if (meta != null && meta.get("sectionTitle") != null) {
                content.append(" 来源：").append(meta.get("sectionTitle"));
            }
            content.append("\n");
            content.append(doc.getContent());

            if (i < docs.size() - 1) {
                content.append("\n\n");
            }
        }

        return content.toString();
    }

    /**
     * 工作流专用：指定相似度阈值的检索
     *
     * @param query 查询问题
     * @param kbName 知识库名称
     * @param topK 返回文档数量
     * @param threshold 相似度阈值（0-1），只返回相似度高于此值的文档
     * @return 格式化的提示词
     */
    public String queryForWorkflowWithThreshold(String query, String kbName, Integer topK, Double threshold) {
        long startTime = System.currentTimeMillis();
        int actualTopK = topK != null ? topK : DEFAULT_TOP_K;
        double actualThreshold = threshold != null ? threshold : SIMILARITY_THRESHOLD;

        log.info("工作流RAG查询开始: query={}, kbName={}, topK={}, threshold={}",
                query, kbName, actualTopK, actualThreshold);

        try {
            // 使用指定阈值检索
            List<RetrievedDocument> docs = retrieveDocumentsWithThreshold(query, kbName, actualTopK, actualThreshold);

            String formattedPrompt = buildRagPrompt(query, docs);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("工作流RAG查询完成: 找到 {} 条文档, 耗时 {}ms", docs.size(), totalTime);

            return formattedPrompt;

        } catch (Exception e) {
            log.error("工作流RAG查询失败", e);
            return buildFallbackPrompt(query, e.getMessage());
        }
    }

    /**
     * 带阈值的向量检索
     */
    private List<RetrievedDocument> retrieveDocumentsWithThreshold(String query, String kbName, int topK, double threshold) {
        try {
            List<Map<String, Object>> results = searchVectors(kbName, query, topK);

            return results.stream()
                    .filter(r -> {
                        double distance = parseDistance(r.get("distance"));
                        return distance < threshold;  // 使用指定阈值
                    })
                    .map(r -> {
                        RetrievedDocument doc = new RetrievedDocument();
                        doc.setContent((String) r.get("document"));
                        double distance = parseDistance(r.get("distance"));
                        doc.setDistance(distance);
                        doc.setScore(distanceToNormalizedScore(distance));
                        doc.setMetadata((Map<String, Object>) r.get("metadata"));
                        return doc;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("检索失败: query={}", query, e);
            return new ArrayList<>();
        }
    }

    // ==================== 核心：混合检索 ====================

    /**
     * 混合检索核心方法
     * 策略：
     * 1. 先用原问题检索
     * 2. 结果充足则直接返回
     * 3. 结果不足则进行查询扩展，多路检索
     * 4. 合并去重排序
     */
    private List<RetrievedDocument> hybridRetrieve(String query, String kbName, Integer topK, String configId) {
        int actualTopK = topK != null ? topK : DEFAULT_TOP_K;

        // ===== 步骤1：先用原问题检索 =====
        List<RetrievedDocument> originalResults = retrieveDocuments(query, kbName, SINGLE_PATH_TOP_K);
        log.info("原问题检索完成: 找到 {} 条相关文档", originalResults.size());

        // ===== 步骤2：判断结果是否充足 =====
        if (isResultsSufficient(originalResults)) {
            log.info("原问题检索结果充足，无需扩展查询");
            return originalResults.stream().limit(actualTopK).collect(Collectors.toList());
        }

        // ===== 步骤3：结果不足，进行查询扩展 =====
        log.info("原问题检索结果不足，进行查询扩展");
        List<String> expandedQueries = expandQuery(query, configId);
        log.info("查询扩展完成: {} -> {}", query, expandedQueries);

        // ===== 步骤4：多路检索 =====
        List<RetrievedDocument> expandedResults = multiPathRetrieval(expandedQueries, kbName, EXPAND_TOP_K);
        log.info("扩展检索完成: 找到 {} 条相关文档", expandedResults.size());

        // ===== 步骤5：合并去重并排序 =====
        List<RetrievedDocument> allDocs = new ArrayList<>();
        allDocs.addAll(originalResults);
        allDocs.addAll(expandedResults);

        List<RetrievedDocument> finalResults = deduplicateAndRank(allDocs);
        log.info("最终结果: {} 条文档", finalResults.size());

        return finalResults.stream().limit(actualTopK).collect(Collectors.toList());
    }

    // ==================== 结果充足性判断 ====================

    /**
     * 判断检索结果是否充足
     */
    private boolean isResultsSufficient(List<RetrievedDocument> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }

        // 至少有 SUFFICIENT_RESULT_COUNT 个结果
        if (results.size() < SUFFICIENT_RESULT_COUNT) {
            return false;
        }

        // 计算平均距离（越小越相关）
        double avgDistance = results.stream()
                .limit(SUFFICIENT_RESULT_COUNT)
                .mapToDouble(RetrievedDocument::getDistance)
                .average()
                .orElse(2.0);

        // 计算平均相似度
        double avgScore = results.stream()
                .limit(SUFFICIENT_RESULT_COUNT)
                .mapToDouble(RetrievedDocument::getScore)
                .average()
                .orElse(0.0);

        log.debug("结果充足性判断: 数量={}, 平均距离={}, 平均相似度={}",
                results.size(), avgDistance, avgScore);

        // 距离小于阈值才算充足
        return avgDistance < SUFFICIENT_SCORE_THRESHOLD;
    }

    // ==================== 查询扩展 ====================

    /**
     * 智能查询扩展（只在需要时生成扩展查询）
     */
    private List<String> expandQuery(String userQuestion, String configId) {

        List<ChatMessage> messages  = new ArrayList<>();
        messages.add(ChatMessage.system(expandSystemPrompt()));
        messages.add(ChatMessage.user(expandUserPrompt(userQuestion)));
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessages(messages);

        Map<String,Object> params = new HashMap<>();
        params.put("temperature", 0.5);
        params.put("max_tokens", 256);
        dto.setParams(params);

        if (configId != null && !configId.isEmpty()) {
            dto.setConfigId(Long.parseLong(configId));
        }

        try {
            Result<String> result = llmFeignClient.chatWithConfig(dto);
            if (result.getCode() == 200 && result.getData() != null) {
                String response = result.getData();
                List<String> queries = Arrays.stream(response.split("\n"))
                        .map(String::trim)
                        .filter(q -> !q.isEmpty() && q.length() > 2)
                        .filter(q -> !q.startsWith("扩展") && !q.startsWith("查询"))
                        .collect(Collectors.toList());

                // 去重，最多返回 MAX_EXPAND_QUERIES 个
                return queries.stream().distinct().limit(MAX_EXPAND_QUERIES).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("查询扩展失败，使用原问题", e);
        }

        return Collections.singletonList(userQuestion);
    }

    private String expandSystemPrompt(){
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个查询优化助手。用户的问题可能检索不到相关信息，请生成2-3个更宽泛或同义表达的查询语句。\n\n");
        sb.append("规则：\n");
        sb.append("1. 保留核心意图，适当放宽条件\n");
        sb.append("2. 每行一个查询，不要编号\n");
        sb.append("3. 不要改变原意\n");
        sb.append("4. 如果原问题已经很清晰，只输出1个查询\n");

        return sb.toString();
    }

    private String expandUserPrompt(String userQuestion){


        return String.format("原始问题：%s\n\n" +
                        "扩展查询：",
                userQuestion );
    }

    // ==================== 检索相关 ====================

    /**
     * 多路检索：对多个查询分别检索，合并结果
     */
    private List<RetrievedDocument> multiPathRetrieval(List<String> queries, String kbName, int topK) {
        List<RetrievedDocument> allDocs = new ArrayList<>();

        for (String query : queries) {
            List<RetrievedDocument> docs = retrieveDocuments(query, kbName, topK);
            allDocs.addAll(docs);
            log.debug("查询 '{}' 检索到 {} 条结果", query, docs.size());
        }

        return allDocs;
    }

    /**
     * 向量检索
     */
    private List<RetrievedDocument> retrieveDocuments(String query, String kbName, int topK) {
        try {
            List<Map<String, Object>> results = searchVectors(kbName, query, topK);

            return results.stream()
                    .filter(r -> {
                        double distance = parseDistance(r.get("distance"));
                        return distance < SIMILARITY_THRESHOLD;  // 距离小于阈值才保留
                    })
                    .map(r -> {
                        RetrievedDocument doc = new RetrievedDocument();
                        doc.setContent((String) r.get("document"));
                        double distance = parseDistance(r.get("distance"));
                        doc.setDistance(distance);
                        // 使用归一化版本
                        doc.setScore(distanceToNormalizedScore(distance));
                        doc.setMetadata((Map<String, Object>) r.get("metadata"));
                        return doc;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("检索失败: query={}", query, e);
            return new ArrayList<>();
        }
    }

    /**
     * 执行向量检索
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchVectors(String kbName, String query, int topK) {
        if (kbName != null && !kbName.isEmpty()) {
            return vectorStoreFactory.getVectorStoreService().search(kbName, query, topK);
        } else {
            return vectorStoreFactory.getVectorStoreService().search(query, topK);
        }
    }

    /**
     * 去重并排序
     */
    private List<RetrievedDocument> deduplicateAndRank(List<RetrievedDocument> docs) {
        // 按内容前100字符去重，保留距离最小的
        Map<String, RetrievedDocument> uniqueDocs = new LinkedHashMap<>();

        for (RetrievedDocument doc : docs) {
            String content = doc.getContent();
            String key = content.length() > 100 ? content.substring(0, 100) : content;

            if (!uniqueDocs.containsKey(key) || doc.getDistance() < uniqueDocs.get(key).getDistance()) {
                uniqueDocs.put(key, doc);
            }
        }

        // 按距离升序排序（距离越小越相关）
        return uniqueDocs.values().stream()
                .sorted(Comparator.comparingDouble(RetrievedDocument::getDistance))
                .collect(Collectors.toList());
    }

    // ==================== 提示词构建 ====================

    /**
     * 构建RAG提示词（供LLM节点使用）
     */
    private String buildRagPrompt(String query, List<RetrievedDocument> docs) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个基于知识库的智能助手。请根据以下知识库内容回答用户的问题。\n\n");

        // 添加检索到的文档内容
        if (docs == null || docs.isEmpty()) {
            prompt.append("【知识库内容】\n");
            prompt.append("未检索到与问题相关的知识库内容。\n\n");
            prompt.append("请根据你自己的知识回答用户的问题，并明确告知用户这是通用知识而非知识库内容。\n\n");
        } else {
            prompt.append("【知识库内容】\n\n");

            for (int i = 0; i < docs.size(); i++) {
                RetrievedDocument doc = docs.get(i);
                Map<String, Object> meta = doc.getMetadata();

                // 添加来源信息
                prompt.append("【文档 ").append(i + 1).append("】");
                if (meta != null && meta.get("sectionTitle") != null) {
                    prompt.append(" 来源：").append(meta.get("sectionTitle"));
                }
                if (meta != null && meta.get("sectionNumber") != null) {
                    prompt.append("（第").append(meta.get("sectionNumber")).append("节）");
                }
                prompt.append("\n");

                // 添加文档内容
                prompt.append(doc.getContent()).append("\n\n");
            }

            prompt.append("【检索说明】\n");
            prompt.append("共检索到 ").append(docs.size()).append(" 条相关文档，请优先使用上述知识库内容回答问题。\n\n");
        }

        // 添加用户问题
        prompt.append("【用户问题】\n");
        prompt.append(query).append("\n\n");

        // 添加回答要求
        prompt.append("【回答要求】\n");
        prompt.append("1. 优先使用知识库中的信息，不要编造\n");
        prompt.append("2. 如果知识库中有具体步骤，请列出步骤\n");
        prompt.append("3. 如果知识库中有配置参数，请说明参数含义\n");
        prompt.append("4. 如果知识库信息不足以回答问题，请明确说明\n");
        prompt.append("5. 回答要简洁、准确、有条理\n");
        prompt.append("6. 不要输出JSON格式，不要输出<think>标签\n\n");

        prompt.append("请回答：");

        return prompt.toString();
    }

    /**
     * 构建降级提示词（检索失败时使用）
     */
    private String buildFallbackPrompt(String query, String errorMsg) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个基于知识库的智能助手。\n\n");
        prompt.append("【注意】\n");
        prompt.append("知识库检索失败，原因：").append(errorMsg).append("\n\n");
        prompt.append("请根据你自己的知识回答用户的问题，并告知用户知识库暂时不可用。\n\n");
        prompt.append("【用户问题】\n");
        prompt.append(query).append("\n\n");
        prompt.append("请回答：");

        return prompt.toString();
    }

    // ==================== 回答生成 ====================

    /**
     * 基于上下文生成专业回答
     */
    private String generateAnswer(String question, String context, List<RetrievedDocument> docs, String configId) {
        String sources = buildSources(docs);

        List<ChatMessage> messages  = new ArrayList<>();
        messages.add(ChatMessage.system(answerSystemPrompt()));
        messages.add(ChatMessage.user(answerUserPrompt(question,context,sources)));
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessages(messages);

        Map<String,Object> params = new HashMap<>();
        params.put("temperature", 0.7);
        params.put("max_tokens", 4096);
        dto.setParams(params);

        if (configId != null && !configId.isEmpty()) {
            dto.setConfigId(Long.parseLong(configId));
        }

        Result<String> result = llmFeignClient.chatWithConfig(dto);

        if (result.getCode() == 200) {
            return result.getData();
        }
        return "查询失败";
    }

    private String answerSystemPrompt(){
        String prompt = String.format(
                "你是一个专业的知识库助手。请根据以下文档内容回答用户的问题。\n\n" +
                        "【回答规则】\n" +
                        "1. 优先使用文档中的信息，不要编造\n" +
                        "2. 如果文档中有具体步骤，请列出步骤\n" +
                        "3. 如果文档中有配置参数，请说明参数含义\n" +
                        "4. 如果文档信息不足以回答问题，请明确说明\n");
        return prompt;
    }

    private String answerUserPrompt(String question, String context, String sources){
        String prompt = String.format(
                "【相关文档内容】\n%s\n\n" +
                        "【用户问题】\n%s\n\n" +
                        "【引用来源】\n%s\n\n" +
                        "请用中文回答。",
                context, question, sources);
        return prompt;
    }

    /**
     * 构建引用来源
     */
    private String buildSources(List<RetrievedDocument> docs) {
        if (docs.isEmpty()) {
            return "无";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            RetrievedDocument doc = docs.get(i);
            Map<String, Object> meta = doc.getMetadata();
            String title = meta != null && meta.get("sectionTitle") != null
                    ? (String) meta.get("sectionTitle") : "未知来源";
            sb.append(String.format("%d. %s（相似度：%.2f）\n", i + 1, title, doc.getScore()));
        }
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建上下文（供内部生成回答使用）
     */
    private String buildContext(List<RetrievedDocument> docs) {
        if (docs.isEmpty()) {
            return "未找到相关文档内容。";
        }

        StringBuilder context = new StringBuilder();
        context.append("以下是与问题相关的文档内容：\n\n");

        for (int i = 0; i < docs.size(); i++) {
            RetrievedDocument doc = docs.get(i);
            Map<String, Object> meta = doc.getMetadata();

            context.append(String.format("【参考文档 %d】", i + 1));
            if (meta != null && meta.get("sectionTitle") != null) {
                context.append(String.format(" 章节：%s %s",
                        meta.get("sectionNumber"), meta.get("sectionTitle")));
            }
            context.append("\n");
            context.append(doc.getContent());
            context.append("\n\n---\n\n");
        }

        return context.toString();
    }

    /**
     * 解析距离值
     * 使用 cosine 相似度时，距离范围 [0, 2]
     * - 0: 完全相同
     * - 1: 正交（无关）
     * - 2: 完全相反
     */
    private double parseDistance(Object distance) {
        double rawDistance = 999.0;

        if (distance instanceof Double) {
            rawDistance = (Double) distance;
        } else if (distance instanceof Float) {
            rawDistance = ((Float) distance).doubleValue();
        } else if (distance instanceof String) {
            try {
                rawDistance = Double.parseDouble((String) distance);
            } catch (NumberFormatException e) {
                log.warn("解析距离值失败: {}", distance);
                return 999.0;
            }
        } else if (distance instanceof Integer) {
            rawDistance = ((Integer) distance).doubleValue();
        } else {
            log.warn("未知的距离类型: {}", distance != null ? distance.getClass() : "null");
            return 999.0;
        }

        // 验证距离值是否在合理范围内
        if (rawDistance < 0 || rawDistance > 2) {
            log.warn("距离值超出合理范围(0-2): {}", rawDistance);
        }

        return rawDistance;
    }


    /**
     * 将距离转换为相似度分数（归一化到0-1）
     */
    private double distanceToNormalizedScore(double distance) {
        // 限制范围
        double clampedDistance = Math.max(0, Math.min(2, distance));
        // 归一化: (2 - distance) / 2
        double normalizedScore = (2.0 - clampedDistance) / 2.0;
        return Math.max(0, Math.min(1, normalizedScore));
    }

    // ==================== 内部类 ====================

    @lombok.Data
    public static class RAGResponse {
        private String originalQuestion;
        private String kbName;
        private List<String> rewrittenQueries;
        private List<RetrievedDocument> retrievedDocs;
        private int contextLength;
        private String answer;
        private String error;
        private long totalTime;
    }

    @lombok.Data
    public static class RetrievedDocument {
        private String content;
        private double distance;
        private double score;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    public static class WorkflowQueryResponse {
        private List<DocumentDetail> details;

        @lombok.Data
        public static class DocumentDetail {
            private String document;
            private Double score;
        }
    }
}