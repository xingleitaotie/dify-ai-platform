package com.washy.dify.rag.service;

import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.LlmFeignClient;
import com.washy.dify.rag.domain.DocumentChunk;
import com.washy.dify.rag.util.ChatRequestConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AIOptimizationService {

    @Resource
    private LlmFeignClient llmFeignClient;
    @Resource
    private ChatRequestConverter requestConverter;

    /**
     * 最小内容长度，低于此长度的块不进行优化
     */
    private static final int MIN_CONTENT_LENGTH = 100;

    /**
     * 批量处理的大小
     */
    private static final int BATCH_SIZE = 5;

    /**
     * 批量调用的超时时间（秒）
     */
    private static final int BATCH_TIMEOUT_SECONDS = 600;

    /**
     * 最大并发批次数
     */
    private static final int MAX_CONCURRENT_BATCHES = 2;

    /**
     * 缓存优化结果（避免重复调用）
     */
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    

    /**
     * 优化分块列表（主入口 - 批量模式）
     */
    public List<DocumentChunk> optimizeChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return chunks;
        }

        long startTime = System.currentTimeMillis();
        log.info("开始批量优化 {} 个文档块", chunks.size());

        // 1. 筛选需要优化的块
        List<OptimizeTask> tasks = buildOptimizeTasks(chunks);

        if (tasks.isEmpty()) {
            log.info("没有需要优化的块");
            return chunks;
        }

        log.info("需要优化: {} 个块，跳过: {} 个块", tasks.size(), chunks.size() - tasks.size());

        // 2. 批量处理
        List<OptimizeResult> results = batchOptimize(tasks);

        // 3. 应用优化结果
        List<DocumentChunk> optimizedChunks = applyOptimizationResults(chunks, results);

        long duration = System.currentTimeMillis() - startTime;
        long successCount = results.stream().filter(r -> r.success).count();
        log.info("批量优化完成: 成功={}, 失败={}, 耗时={}ms",
                successCount, results.size() - successCount, duration);

        return optimizedChunks;
    }

    /**
     * 构建优化任务列表
     */
    private List<OptimizeTask> buildOptimizeTasks(List<DocumentChunk> chunks) {
        List<OptimizeTask> tasks = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);

            // 检查是否需要优化
            if (chunk.getContent() == null || chunk.getContent().length() < MIN_CONTENT_LENGTH) {
                continue;
            }

            // 检查缓存
            String cacheKey = getCacheKey(chunk);
            Map<String, String> cached = getFromCache(cacheKey);
            if (cached != null) {
                log.debug("块 {} 命中缓存", chunk.getChunkId());
                tasks.add(OptimizeResult.success(chunk.getChunkId(), cached).toTask());
                continue;
            }

            tasks.add(new OptimizeTask(chunk.getChunkId(), chunk));
        }

        return tasks;
    }

    /**
     * 批量优化（核心方法）
     */
    private List<OptimizeResult> batchOptimize(List<OptimizeTask> tasks) {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 过滤掉已经缓存的任务
        List<OptimizeTask> needOptimize = tasks.stream()
                .filter(t -> !t.cached)
                .collect(Collectors.toList());

        if (needOptimize.isEmpty()) {
            return tasks.stream()
                    .map(t -> OptimizeResult.success(t.chunkId, t.cachedResult))
                    .collect(Collectors.toList());
        }

        // 使用线程池并发处理批次
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_BATCHES);
        List<Future<List<OptimizeResult>>> futures = new ArrayList<>();

        // 分批
        for (int i = 0; i < needOptimize.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, needOptimize.size());
            List<OptimizeTask> batch = needOptimize.subList(i, end);

            Future<List<OptimizeResult>> future = executor.submit(() -> {
                try {
                    return processBatch(batch);
                } catch (Exception e) {
                    log.error("批次处理失败: {}", e.getMessage());
                    // 返回失败结果
                    return batch.stream()
                            .map(t -> OptimizeResult.fail(t.chunkId))
                            .collect(Collectors.toList());
                }
            });
            futures.add(future);
        }

        // 收集结果
        List<OptimizeResult> allResults = new ArrayList<>();
        for (Future<List<OptimizeResult>> future : futures) {
            try {
                List<OptimizeResult> batchResults = future.get(BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                allResults.addAll(batchResults);
            } catch (TimeoutException e) {
                log.warn("批次处理超时");
                // 超时的批次标记为失败
                // 这里无法获取具体是哪个批次，在实际应用中需要改进
            } catch (Exception e) {
                log.error("获取批次结果失败: {}", e.getMessage());
            }
        }

        executor.shutdownNow();

        // 合并缓存结果
        Map<String, OptimizeResult> resultMap = allResults.stream()
                .collect(Collectors.toMap(r -> r.chunkId, r -> r));

        return tasks.stream()
                .map(t -> resultMap.getOrDefault(t.chunkId, OptimizeResult.fail(t.chunkId)))
                .collect(Collectors.toList());
    }

    /**
     * 处理单个批次
     */
    private List<OptimizeResult> processBatch(List<OptimizeTask> batch) {
        if (batch.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("处理批次: {} 个块", batch.size());
        long startTime = System.currentTimeMillis();

        try {
            // 构建批量请求
            String batchPrompt = buildBatchPrompt(batch);

            // 调用LLM
            ChatRequestDTO request = requestConverter.toRequest(
                    buildBatchSystemPrompt(),
                    batchPrompt,
                    0.3,
                    null
            );

            Result<String> response = llmFeignClient.chat(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("批次调用完成: {}个块, 耗时={}ms", batch.size(), duration);

            if (response.getCode() == 200 && response.getData() != null) {
                return parseBatchResponse(response.getData(), batch);
            } else {
                log.warn("批次调用返回错误码: {}", response.getCode());
                return batch.stream()
                        .map(t -> OptimizeResult.fail(t.chunkId))
                        .collect(Collectors.toList());
            }

        } catch (Exception e) {
            log.error("批次处理异常: {}", e.getMessage(), e);
            return batch.stream()
                    .map(t -> OptimizeResult.fail(t.chunkId))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 构建批量系统提示词
     */
    private String buildBatchSystemPrompt() {
        return "你是一个专业的文档分析助手。\n" +
                "【任务】\n" +
                "分析多个文档块，为每个块提取摘要、关键词和内容类型。\n\n" +
                "【输出要求】\n" +
                "1. 严格按照指定的格式输出\n" +
                "2. 每个块的输出用 ===BLOCK_START=== 和 ===BLOCK_END=== 包裹\n" +
                "3. 摘要控制在30-50字\n" +
                "4. 关键词3-5个，用逗号分隔\n" +
                "5. 内容类型从以下选项选择：概念说明、操作步骤、功能描述、技术规范、配置参数、接口说明、数据结构、其他\n\n" +
                "【输出格式示例】\n" +
                "===BLOCK_START===\n" +
                "摘要：这是一个关于某某技术的说明文档\n" +
                "关键词：技术,文档,说明\n" +
                "类型：概念说明\n" +
                "===BLOCK_END===\n" +
                "===BLOCK_START===\n" +
                "摘要：这是第二个文档块的内容\n" +
                "关键词：步骤,操作,指南\n" +
                "类型：操作步骤\n" +
                "===BLOCK_END===";
    }

    /**
     * 构建批量用户提示词
     */
    private String buildBatchPrompt(List<OptimizeTask> batch) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下").append(batch.size()).append("个文档块，为每个块生成摘要、关键词和内容类型。\n\n");

        for (int i = 0; i < batch.size(); i++) {
            OptimizeTask task = batch.get(i);
            DocumentChunk chunk = task.chunk;

            sb.append("【文档块 ").append(i + 1).append("】\n");
            if (chunk.getSectionNumber() != null && !chunk.getSectionNumber().isEmpty()) {
                sb.append("章节：").append(chunk.getSectionNumber()).append(" ");
            }
            if (chunk.getSectionTitle() != null && !chunk.getSectionTitle().isEmpty()) {
                sb.append(chunk.getSectionTitle());
            }
            sb.append("\n");
            sb.append("内容：\n").append(truncateContent(chunk.getContent(), 600)).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 解析批量响应
     */
    private List<OptimizeResult> parseBatchResponse(String response, List<OptimizeTask> batch) {
        List<OptimizeResult> results = new ArrayList<>();

        // 解析每个块的结果
        String[] blocks = response.split("===BLOCK_START===");
        int blockIndex = 0;

        for (int i = 1; i < blocks.length && blockIndex < batch.size(); i++) {
            String block = blocks[i];
            int endIndex = block.indexOf("===BLOCK_END===");
            if (endIndex > 0) {
                block = block.substring(0, endIndex);
            }

            Map<String, String> parsed = parseSingleBlock(block);
            OptimizeTask task = batch.get(blockIndex);

            // 存入缓存
            String cacheKey = getCacheKey(task.chunk);
            putToCache(cacheKey, parsed);

            results.add(OptimizeResult.success(task.chunkId, parsed));
            blockIndex++;
        }

        // 处理解析失败的块
        while (blockIndex < batch.size()) {
            log.warn("块 {} 解析失败，使用默认值", batch.get(blockIndex).chunkId);
            results.add(OptimizeResult.fail(batch.get(blockIndex).chunkId));
            blockIndex++;
        }

        return results;
    }

    /**
     * 解析单个块的结果
     */
    private Map<String, String> parseSingleBlock(String block) {
        Map<String, String> result = new HashMap<>();
        result.put("summary", "");
        result.put("keywords", "");
        result.put("contentType", "其他");

        String[] lines = block.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("摘要：") || line.startsWith("摘要:")) {
                String value = line.substring(line.indexOf("：") + 1);
                if (value.startsWith(":")) value = value.substring(1);
                result.put("summary", value.trim());
            } else if (line.startsWith("关键词：") || line.startsWith("关键词:")) {
                String value = line.substring(line.indexOf("：") + 1);
                if (value.startsWith(":")) value = value.substring(1);
                result.put("keywords", value.trim());
            } else if (line.startsWith("类型：") || line.startsWith("类型:")) {
                String value = line.substring(line.indexOf("：") + 1);
                if (value.startsWith(":")) value = value.substring(1);
                result.put("contentType", value.trim());
            }
        }

        return result;
    }

    /**
     * 应用优化结果到原始块
     */
    private List<DocumentChunk> applyOptimizationResults(List<DocumentChunk> chunks,
                                                         List<OptimizeResult> results) {
        // 建立映射
        Map<String, OptimizeResult> resultMap = results.stream()
                .collect(Collectors.toMap(r -> r.chunkId, r -> r));

        List<DocumentChunk> optimizedChunks = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            OptimizeResult optResult = resultMap.get(chunk.getChunkId());

            if (optResult != null && optResult.success) {
                // 应用优化结果
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("aiSummary", optResult.summary);
                metadata.put("aiKeywords", optResult.keywords);
                metadata.put("aiContentType", optResult.contentType);
                metadata.put("aiOptimized", true);

                String enhancedContent = buildEnhancedContent(
                        chunk, optResult.summary, optResult.keywords, optResult.contentType
                );

                DocumentChunk optimized = DocumentChunk.builder()
                        .chunkId(chunk.getChunkId())
                        .documentId(chunk.getDocumentId())
                        .sectionNumber(chunk.getSectionNumber())
                        .sectionTitle(chunk.getSectionTitle())
                        .sectionLevel(chunk.getSectionLevel())
                        .content(enhancedContent)
                        .images(chunk.getImages())
                        .tables(chunk.getTables())
                        .metadata(metadata)
                        .build();
                optimizedChunks.add(optimized);
            } else {
                // 保持原样或使用本地分析
                DocumentChunk fallback = applyLocalAnalysis(chunk);
                optimizedChunks.add(fallback);
            }
        }

        return optimizedChunks;
    }

    /**
     * 应用本地分析（降级方案）
     */
    private DocumentChunk applyLocalAnalysis(DocumentChunk chunk) {
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());

        // 本地快速分析
        String summary = extractFirstSentence(chunk.getContent());
        String keywords = extractSimpleKeywords(chunk.getContent());
        String contentType = detectContentTypeSimple(chunk.getContent());

        metadata.put("aiSummary", summary);
        metadata.put("aiKeywords", keywords);
        metadata.put("aiContentType", contentType);
        metadata.put("aiOptimized", false);
        metadata.put("aiFallback", true);

        String enhancedContent = buildEnhancedContent(chunk, summary, keywords, contentType);

        return DocumentChunk.builder()
                .chunkId(chunk.getChunkId())
                .documentId(chunk.getDocumentId())
                .sectionNumber(chunk.getSectionNumber())
                .sectionTitle(chunk.getSectionTitle())
                .sectionLevel(chunk.getSectionLevel())
                .content(enhancedContent)
                .images(chunk.getImages())
                .tables(chunk.getTables())
                .metadata(metadata)
                .build();
    }

    /**
     * 提取第一句话作为摘要
     */
    private String extractFirstSentence(String content) {
        if (content == null || content.isEmpty()) return "";
        int endIndex = content.indexOf("。");
        if (endIndex > 0 && endIndex <= 100) {
            return content.substring(0, endIndex + 1);
        }
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }

    /**
     * 提取简单关键词
     */
    private String extractSimpleKeywords(String content) {
        if (content == null || content.isEmpty()) return "";
        // 简单的关键词提取：取高频词
        String[] words = content.split("\\s+");
        Map<String, Integer> freq = new HashMap<>();
        for (String word : words) {
            if (word.length() >= 2 && !isStopWord(word)) {
                freq.put(word, freq.getOrDefault(word, 0) + 1);
            }
        }
        return freq.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
    }

    /**
     * 简单的内容类型检测
     */
    private String detectContentTypeSimple(String content) {
        if (content.contains("步骤") || content.contains("首先") || content.contains("然后")) {
            return "操作步骤";
        }
        if (content.contains("配置") || content.contains("参数") || content.contains("设置")) {
            return "配置参数";
        }
        if (content.contains("接口") || content.contains("API") || content.contains("调用")) {
            return "接口说明";
        }
        if (content.contains("class") || content.contains("function") || content.contains("数据结构")) {
            return "数据结构";
        }
        if (content.contains("概念") || content.contains("定义") || content.contains("介绍")) {
            return "概念说明";
        }
        return "其他";
    }

    /**
     * 停用词集合（静态常量，避免重复创建）
     */
    private static final Set<String> STOP_WORDS;

    static {
        Set<String> words = new HashSet<>();
        words.add("的");
        words.add("了");
        words.add("是");
        words.add("在");
        words.add("和");
        words.add("与");
        words.add("或");
        words.add("一个");
        words.add("这个");
        words.add("那个");
        words.add("有");
        words.add("被");
        words.add("把");
        words.add("从");
        words.add("到");
        words.add("对");
        words.add("为");
        words.add("以");
        words.add("着");
        words.add("过");
        words.add("也");
        words.add("都");
        words.add("不");
        words.add("没");
        words.add("很");
        words.add("太");
        words.add("非常");
        words.add("就");
        words.add("还");
        words.add("而");
        words.add("且");
        words.add("并");
        STOP_WORDS = Collections.unmodifiableSet(words);
    }

    /**
     * 判断是否为停用词
     */
    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word);
    }

    /**
     * 构建增强内容
     */
    private String buildEnhancedContent(DocumentChunk chunk, String summary,
                                        String keywords, String contentType) {
        StringBuilder sb = new StringBuilder();

        if (summary != null && !summary.isEmpty()) {
            sb.append("【摘要】").append(summary).append("\n");
        }
        if (keywords != null && !keywords.isEmpty()) {
            sb.append("【关键词】").append(keywords).append("\n");
        }
        if (contentType != null && !contentType.isEmpty()) {
            sb.append("【内容类型】").append(contentType).append("\n");
        }
        sb.append("\n").append(chunk.getContent());

        return sb.toString();
    }

    /**
     * 获取缓存Key
     */
    private String getCacheKey(DocumentChunk chunk) {
        // 使用内容哈希作为缓存key
        return chunk.getChunkId() + "_" +
                (chunk.getContent() != null ? chunk.getContent().hashCode() : 0);
    }

    /**
     * 从缓存获取
     */
    private Map<String, String> getFromCache(String key) {
        // 简单实现，可扩展为Redis
        return cache.get(key);
    }

    /**
     * 存入缓存
     */
    private void putToCache(String key, Map<String, String> value) {
        cache.put(key, value);
    }

    /**
     * 截断内容
     */
    private String truncateContent(String content, int maxLen) {
        if (content == null || content.isEmpty()) return "";
        if (content.length() <= maxLen) return content;
        int cutIndex = content.lastIndexOf("。", maxLen);
        if (cutIndex > maxLen / 2) {
            return content.substring(0, cutIndex + 1);
        }
        return content.substring(0, maxLen) + "...";
    }

    /**
     * 优化任务内部类
     */
    private static class OptimizeTask {
        String chunkId;
        DocumentChunk chunk;
        boolean cached;
        Map<String, String> cachedResult;

        OptimizeTask(String chunkId, DocumentChunk chunk) {
            this.chunkId = chunkId;
            this.chunk = chunk;
            this.cached = false;
        }

        private OptimizeTask(String chunkId, Map<String, String> cachedResult) {
            this.chunkId = chunkId;
            this.cachedResult = cachedResult;
            this.cached = true;
        }
    }

    /**
     * 优化结果内部类
     */
    private static class OptimizeResult {
        String chunkId;
        boolean success;
        String summary;
        String keywords;
        String contentType;

        static OptimizeResult success(String chunkId, Map<String, String> result) {
            OptimizeResult r = new OptimizeResult();
            r.chunkId = chunkId;
            r.success = true;
            r.summary = result.getOrDefault("summary", "");
            r.keywords = result.getOrDefault("keywords", "");
            r.contentType = result.getOrDefault("contentType", "其他");
            return r;
        }

        static OptimizeResult fail(String chunkId) {
            OptimizeResult r = new OptimizeResult();
            r.chunkId = chunkId;
            r.success = false;
            r.summary = "";
            r.keywords = "";
            r.contentType = "其他";
            return r;
        }

        OptimizeTask toTask() {
            Map<String, String> result = new HashMap<>();
            result.put("summary", summary);
            result.put("keywords", keywords);
            result.put("contentType", contentType);
            return new OptimizeTask(chunkId, result);
        }
    }
}