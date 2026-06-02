package com.washy.dify.feign.client;

import com.washy.dify.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "dify-rag-service")
public interface RagFeignClient {

    @PostMapping("/api/rag/kb/{name}/search")
    ResponseEntity<Map<String, Object>> searchInKb(@PathVariable String name,
                                               @RequestBody Map<String, Object> params);
    // 获取知识库列表
    @GetMapping("/api/rag/kb/list")
    Result<Map<String, Object>> getKnowledgeBaseList();

    /**
     * 单个知识库节点查询
     * 工作流查询向量库形成提示词
     */
    @PostMapping("/api/rag/search/single/document")
    Result<Map<String, Object>> searchDocument(@RequestBody Map<String, Object> request);

    /**
     * 多个知识库节点查询
     * 工作流查询向量库形成提示词
     */
    @PostMapping("/api/rag/search/much/document")
    Result<String> searchMuchDocument(@RequestBody Map<String,Object> request);

}