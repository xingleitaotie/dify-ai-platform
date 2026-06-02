package com.washy.dify.function.functions;

import com.washy.dify.common.annotation.AiFunction;
import com.washy.dify.common.entity.rag.EmbeddingDTO;
import com.washy.dify.common.entity.rag.RagRetrieveVO;
import com.washy.dify.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模拟搜索工具函数
 * @author Day7
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "dify.service", name = "rag")
public class SearchFunction {

    @Value("${dify.service.rag}")
    private String ragHost;

    @Resource
    private RestTemplate restTemplate;

    @AiFunction(
            name = "knowledge_search",
            desc = "RAG信息搜索",
            params = "{\"query\":\"查询关键字，必填\"}"
    )
    public String searchInfo(Object params) {
        // 1. 获取参数
        String keyword = null;

        try {

            // ==============================================
            // 万能兼容：自动识别 query / keyword / 数组
            // ==============================================
            if (params instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) params;

                // 优先取 query → 再取 keyword → 最后取第一个值
                if (map.containsKey("query")) {
                    keyword = map.get("query").toString();
                } else if (map.containsKey("keyword")) {
                    keyword = map.get("keyword").toString();
                } else if (map.containsKey("question")) {
                    keyword = map.get("question").toString();
                } else {
                    // 兜底：随便取第一个值
                    keyword = map.values().iterator().next().toString();
                }

            } else if (params instanceof List) {
                // 兼容数组格式：["内容"]
                List<Object> list = (List<Object>) params;
                if (!list.isEmpty()) {
                    keyword = list.get(0).toString();
                }
            }

            // 如果都没取到
            if (null == keyword || "".equals(keyword.trim())) {
                return "未获取到查询关键词";
            }

            log.info("RAG检索关键词：{}", keyword);

            // 请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 2. 调用RAG服务接口
            String ragUrl = ragHost + "/api/rag/search";
            EmbeddingDTO embeddingDTO = new EmbeddingDTO();
            embeddingDTO.setText(keyword);
            // 封装请求
            HttpEntity<EmbeddingDTO> requestEntity = new HttpEntity<>(embeddingDTO, headers);

            // 3. 发送POST请求,接收统一返回体 Result
            Result<Map<String, Object>> result = restTemplate.postForObject(ragUrl, requestEntity, Result.class);

            if (result == null || result.getData() == null) {
                return "RAG中未获取到与["+keyword+"]相关信息";
            }

            // 2. 从data中提取Chroma返回的字段
            Map<String, Object> chromaData = result.getData();

            // 3. 解析双层数组
            List<List<String>> documents = (List<List<String>>) chromaData.get("documents");
            List<List<Double>> distances = (List<List<Double>>) chromaData.get("distances");
            List<List<Map<String, Object>>> metadatas = (List<List<Map<String, Object>>>) chromaData.get("metadatas");

            List<RagRetrieveVO> resultList = new ArrayList<>();

            // 4. 遍历组装（Chroma返回结构：第一层是批次，第二层是真实数据）
            if (documents != null && !documents.isEmpty()) {
                for (int i = 0; i < documents.size(); i++) {
                    List<String> docList = documents.get(i);
                    List<Double> distList = distances.get(i);
                    List<Map<String, Object>> metaList = metadatas.get(i);

                    for (int j = 0; j < docList.size(); j++) {
                        com.washy.dify.common.entity.rag.RagRetrieveVO vo = new com.washy.dify.common.entity.rag.RagRetrieveVO();
                        vo.setDocument(docList.get(j));
                        vo.setDistance(distList.get(j));
                        vo.setMetadata(metaList.get(j));
                        resultList.add(vo);
                    }
                }
            }
            String context = resultList.stream()
                    .map(RagRetrieveVO::getDocument)
                    .collect(Collectors.joining("\n"));
            // 4. 返回结果给AI大模型
            log.info("RAG检索返回结果：{}", context);
            return "RAG知识库检索结果：" + context;

        } catch (Exception e) {
            log.error("RAG检索接口调用失败", e);
            return "RAG检索失败：" + e.getMessage();
        }
    }
}