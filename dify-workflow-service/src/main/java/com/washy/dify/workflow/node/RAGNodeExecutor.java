package com.washy.dify.workflow.node;

import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.RagFeignClient;
import com.washy.dify.workflow.config.WorkflowContext;
import com.washy.dify.workflow.util.WorkflowVariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RAGNodeExecutor implements NodeExecutor {

    private final RagFeignClient ragFeignClient;
    private final WorkflowVariableResolver resolver;

    @Override
    public String getNodeType() {
        return "RAG";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        log.info("===== RAG 节点执行（动态类型解析版）=====");
        log.info("节点配置: {}", nodeInput);

        // 取出真正的 config
        Map<String, Object> config = (Map<String, Object>) nodeInput.get("config");
        if (config == null) config = new HashMap<>();

        // ===================== 1. 动态解析查询（完全按前端配置） =====================
        String queryTemplate = getConfigString(config, "query");
        if (queryTemplate == null || queryTemplate.isEmpty()) {
            throw new RuntimeException("RAG 节点查询不能为空");
        }

        // 变量解析（支持 {{input.xxx}} {{var.xxx}} {{var.xxx.field}}）
        Object queryValue = resolver.resolve(queryTemplate, context);
        log.info("原始查询模板: {}", queryTemplate);
        log.info("解析后查询值: {} 类型: {}", queryValue, queryValue != null ? queryValue.getClass().getSimpleName() : "null");

        List<String> queryList = new ArrayList<>();
        if (queryValue instanceof List) {
            log.info("解析到数组类型查询，开始遍历元素");
            for (Object item : (List<?>) queryValue) {
                if (item == null) {
                    continue;
                }
                String itemStr = item.toString().trim();
                // 过滤纯空文本
                if (!itemStr.isEmpty()) {
                    queryList.add(itemStr);
                }
            }
        } else {
            queryList = toTextList(queryValue);
        }
        log.info("最终待执行查询列表: {}", queryList);
        if (queryList.isEmpty()) {
            throw new RuntimeException("未提取到有效查询内容");
        }

        // ===================== 2. 动态解析参数 =====================
        String kb = getConfigString(config, "kbName");
        if (kb == null) kb = getConfigString(config, "kb");

        Integer topK = getConfigInt(config, "topK", 5);
        Double threshold = getConfigDouble(config, "threshold", 0.0);

        log.info("RAG 执行参数：queryList={}, kb={}, topK={}, threshold={}", queryList, kb, topK, threshold);

        // ===================== 3. 执行检索 =====================
        List<RetrievedDocument> allDocs = new ArrayList<>();
        for (String q : queryList) {
            try {
                Map<String, Object> req = new HashMap<>();
                req.put("query", q);
                req.put("kb", kb == null ? "" : kb);
                req.put("topK", topK * 2);

                Result<Map<String, Object>> res = ragFeignClient.searchDocument(req);
                if (res == null || res.getCode() != 200 || res.getData() == null) {
                    continue;
                }
                allDocs.addAll(parseDocuments(res.getData()));
            } catch (Exception e) {
                log.error("RAG 查询异常", e);
            }
        }

        // ===================== 4. 去重 & 过滤 & 排序 =====================
        List<RetrievedDocument> finalDocs = deduplicateAndSort(allDocs, threshold);
        if (finalDocs.size() > topK) {
            finalDocs = finalDocs.subList(0, topK);
        }

        // ===================== 5. 输出（与前端配置结构一致） =====================
        List<String> documents = finalDocs.stream()
                .map(RetrievedDocument::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());


        Map<String, Object> output = new HashMap<>();

        output.put("query", queryValue);
        output.put("documentCount", finalDocs.size());
        output.put("documents", documents);

        log.info("RAG 执行完成，返回文档数: {}", finalDocs.size());
        return output;
    }

    // ===================== 核心：纯动态类型解析（不写死任何业务字段） =====================

    /**
     * 任意对象 → 字符串列表（纯类型推导，前端传什么都能正确解析）
     * 支持：string / number / boolean / List / Array / Map（自动toString）
     */
    private List<String> toTextList(Object obj) {
        List<String> result = new ArrayList<>();
        if (obj == null) return result;

        // 字符串
        if (obj instanceof String) {
            String s = ((String) obj).trim();
            if (!s.isEmpty()) result.add(s);
            return result;
        }

        // 数字/布尔
        if (obj instanceof Number || obj instanceof Boolean) {
            result.add(obj.toString());
            return result;
        }

        // 集合/数组
        if (obj instanceof Collection<?>) {
            for (Object item : (Collection<?>) obj) {
                result.addAll(toTextList(item));
            }
            return result;
        }
        if (obj.getClass().isArray()) {
            for (Object item : (Object[]) obj) {
                result.addAll(toTextList(item));
            }
            return result;
        }

        // Map：直接整体转字符串（不写死字段！！！）
        if (obj instanceof Map) {
            result.add(obj.toString());
            return result;
        }

        // 其他对象
        result.add(obj.toString().trim());
        return result;
    }

    /**
     * 解析 RAG 服务返回文档（通用解析）
     */
    private List<RetrievedDocument> parseDocuments(Map<String, Object> data) {
        List<RetrievedDocument> docs = new ArrayList<>();
        if (data == null) return docs;

        Object listObj = data.get("details");
        if (listObj == null) listObj = data.get("documents");
        if (listObj == null) listObj = data.get("data");

        if (!(listObj instanceof List)) return docs;

        for (Object o : (List<?>) listObj) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;

            RetrievedDocument doc = new RetrievedDocument();
            doc.setContent(getString(m, "document", "content", "text"));
            doc.setScore(getDouble(m, 0.0, "score"));

            if (!doc.getContent().isEmpty()) {
                docs.add(doc);
            }
        }
        return docs;
    }

    // ===================== 工具方法 =====================

    private List<RetrievedDocument> deduplicateAndSort(List<RetrievedDocument> docs, double threshold) {
        Map<String, RetrievedDocument> map = new LinkedHashMap<>();
        for (RetrievedDocument d : docs) {
            if (d.getContent() == null || d.getScore() < threshold) continue;
            String key = d.getContent().length() > 200 ? d.getContent().substring(0, 200) : d.getContent();
            if (!map.containsKey(key) || map.get(key).getScore() < d.getScore()) {
                map.put(key, d);
            }
        }
        return map.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
    }

    private String buildContext(List<RetrievedDocument> docs) {
        if (docs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            sb.append("【文档").append(i + 1).append("】\n");
            sb.append(docs.get(i).getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String getConfigString(Map<String, Object> config, String key) {
        Object v = config.get(key);
        return v == null ? null : v.toString().trim();
    }

    private int getConfigInt(Map<String, Object> config, String key, int def) {
        try {
            Object v = config.get(key);
            if (v instanceof Number) return ((Number) v).intValue();
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private double getConfigDouble(Map<String, Object> config, String key, double def) {
        try {
            Object v = config.get(key);
            if (v instanceof Number) return ((Number) v).doubleValue();
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private String getString(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v.toString().trim();
        }
        return "";
    }

    private double getDouble(Map<?, ?> map, double def, String... keys) {
        try {
            for (String k : keys) {
                Object v = map.get(k);
                if (v instanceof Number) return ((Number) v).doubleValue();
            }
        } catch (Exception ignored) {}
        return def;
    }

    // ===================== 内部实体 =====================
    @lombok.Data
    public static class RetrievedDocument {
        private String content;
        private double score;
        private String sourceQuery;
    }
}