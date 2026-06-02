package com.washy.dify.llm.util;

import com.washy.dify.common.entity.rag.EmbeddingDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.common.entity.rag.RagRetrieveVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 调用Day4实现的RAG检索服务
 */
@Component
@RequiredArgsConstructor
public class RagServiceUtil {

    private final RestTemplate restTemplate;

    @Value("${rag.service-url}")
    private String ragServiceUrl;

    /**
     * 调用RAG服务检索相关文档
     */
    public List<RagRetrieveVO> retrieveDocument(String query, Integer topK) {
        // 请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 🔥 直接发送 DTO（和接收端完全一致）
        EmbeddingDTO dto = new EmbeddingDTO();
        dto.setText(query);
        dto.setTopN(topK);
        // 请求参数
        HttpEntity<EmbeddingDTO> request = new HttpEntity<>(dto, headers);


        String url = ragServiceUrl + "/api/rag/search";

        // 1. 接收统一返回体 Result
        Result<Map<String, Object>> result = restTemplate.postForObject(
                url, request, Result.class
        );

        if (result == null || result.getData() == null) {
            return new ArrayList<>();
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
                    RagRetrieveVO vo = new RagRetrieveVO();
                    vo.setDocument(docList.get(j));
                    vo.setDistance(distList.get(j));
                    vo.setMetadata(metaList.get(j));
                    resultList.add(vo);
                }
            }
        }

        return resultList;
    }
}