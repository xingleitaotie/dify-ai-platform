package com.washy.dify.function.functions;

import com.washy.dify.common.annotation.AiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Component
@ConditionalOnProperty(name = "web-search.enabled", havingValue = "true", matchIfMissing = true)
public class WebSearchFunction {

    @Value("${web-search.searxng.url}")
    private String searxngUrl;

    @Resource
    private RestTemplate restTemplate;

    @AiFunction(
            name = "web_search",
            desc = "在互联网上搜索信息。可以搜索新闻、知识、资讯等各类网络信息。适用于需要获取最新信息、实时数据、网络资料等场景。",
            params = "{\n" +
                    "  \"query\": {\n" +
                    "    \"type\": \"string\",\n" +
                    "    \"description\": \"搜索关键词或问题，例如：'Spring Boot 教程'、'Java 8 新特性'\",\n" +
                    "    \"required\": true\n" +
                    "  },\n" +
                    "  \"count\": {\n" +
                    "    \"type\": \"integer\",\n" +
                    "    \"description\": \"返回搜索结果数量，默认为5，范围1-10\",\n" +
                    "    \"default\": 5,\n" +
                    "    \"minimum\": 1,\n" +
                    "    \"maximum\": 10\n" +
                    "  }\n" +
                    "}"
    )
    public String webSearch(Map<String, Object> params) {
        try {
            // 提取参数
            String query = extractQuery(params);
            if (query == null || query.trim().isEmpty()) {
                return "{\"success\": false, \"error\": \"搜索关键词不能为空\", \"results\": []}";
            }

            int count = params.containsKey("count") ? 
                    Integer.parseInt(params.get("count").toString()) : 5;
            count = Math.min(count, 10);

            log.info("互联网搜索 - 关键词：{}，数量：{}", query, count);

            // 调用 SearXNG API
            List<Map<String, Object>> results = searchWithSearxng(query, count);

            if (results.isEmpty()) {
                return String.format("{\"success\": true, \"message\": \"未找到与'%s'相关的搜索结果\", \"results\": []}", query);
            }

            // 构建返回结果
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("query", query);
            response.put("total_results", results.size());
            
            List<Map<String, Object>> resultList = new ArrayList<>();
            StringBuilder contextBuilder = new StringBuilder();
            
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> item = results.get(i);
                Map<String, Object> formattedItem = new HashMap<>();
                formattedItem.put("index", i + 1);
                formattedItem.put("title", item.getOrDefault("title", "无标题"));
                formattedItem.put("snippet", item.getOrDefault("content", item.getOrDefault("description", "无描述")));
                formattedItem.put("url", item.getOrDefault("url", ""));
                resultList.add(formattedItem);
                
                contextBuilder.append(String.format("[%d] %s\n%s\n来源：%s\n\n", 
                        i + 1, 
                        formattedItem.get("title"), 
                        formattedItem.get("snippet"), 
                        formattedItem.get("url")));
            }
            
            response.put("results", resultList);
            response.put("context_summary", contextBuilder.toString());
            
            // 使用 fastjson2 或你项目中已有的 JSON 工具
            return new com.alibaba.fastjson2.JSONObject(response).toJSONString();
            
        } catch (Exception e) {
            log.error("互联网搜索失败", e);
            return String.format("{\"success\": false, \"error\": \"搜索失败：%s\", \"results\": []}", 
                    e.getMessage().replace("\"", "\\\""));
        }
    }

    /**
     * 使用 SearXNG 搜索
     */
    private List<Map<String, Object>> searchWithSearxng(String query, int count) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(searxngUrl + "/search")
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("limit", count)
                    .build()
                    .toUriString();

            log.debug("搜索 URL: {}", url);

            // 构建完整浏览器请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            headers.set("Accept-Encoding", "gzip, deflate, br");
            headers.set("Connection", "keep-alive");
            headers.set("Upgrade-Insecure-Requests", "1");
            headers.set("Sec-Fetch-Dest", "document");
            headers.set("Sec-Fetch-Mode", "navigate");
            headers.set("Sec-Fetch-Site", "none");
            headers.set("Sec-Fetch-User", "?1");
            headers.set("Cache-Control", "max-age=0");
            headers.set("Referer", searxngUrl);  // 关键：防止防盗链

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("results")) {
                return (List<Map<String, Object>>) response.getBody().get("results");
            }
        } catch (Exception e) {
            log.error("SearXNG 搜索调用失败", e);
        }
        return Collections.emptyList();
    }

    private String extractQuery(Map<String, Object> params) {
        if (params.containsKey("query")) {
            return params.get("query").toString();
        } else if (params.containsKey("keyword")) {
            return params.get("keyword").toString();
        } else if (params.containsKey("question")) {
            return params.get("question").toString();
        } else if (!params.isEmpty()) {
            return params.values().iterator().next().toString();
        }
        return null;
    }
}