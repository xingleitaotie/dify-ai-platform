package com.washy.dify.feign.client;

import com.washy.dify.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "dify-rag-service")
public interface RagFeignClient {

    @PostMapping("/api/rag/kb/{name}/search")
    ResponseEntity<Map<String, Object>> searchInKb(@PathVariable String name,
                                               @RequestBody Map<String, Object> params);
    // 获取知识库列表
    @GetMapping("/api/rag/kb/list")
    Result<List<Map<String, Object>>> getKnowledgeBaseList();

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

    /**
     * 存储提示词模板到向量库
     */
    @PostMapping("/api/rag/prompt-template/store")
    Result<Void> storePromptTemplate(@RequestBody Map<String, Object> request);

    /**
     * 批量存储提示词模板
     */
    @PostMapping("/api/rag/prompt-template/batch-store")
    Result<Void> batchStorePromptTemplates(@RequestBody List<Map<String, Object>> templates);

    /**
     * 删除提示词模板
     */
    @DeleteMapping("/api/rag/prompt-template/{templateId}")
    Result<Void> deletePromptTemplate(@PathVariable String templateId);

    /**
     * 批量删除提示词模板
     */
    @DeleteMapping("/api/rag/prompt-template/batch-delete")
    Result<Void> batchDeletePromptTemplates(@RequestBody List<String> templateIds);

    /**
     * 搜索相似提示词模板
     */
    @PostMapping("/api/rag/prompt-template/search")
    Result<List<Map<String, Object>>> searchPromptTemplates(@RequestBody Map<String, Object> request);

    /**
     * 获取所有提示词模板
     */
    @GetMapping("/api/rag/prompt-template/list")
    Result<List<Map<String, Object>>> listAllPromptTemplates();

    /**
     * 获取提示词模板数量
     */
    @GetMapping("/api/rag/prompt-template/count")
    Result<Integer> getPromptTemplateCount();

    /**
     * 删除知识库
     */
    @DeleteMapping("/api/rag/kb/{name}")
    Result<Void> deleteCollection(@PathVariable String name);

    /**
     * 创建知识库
     */
    @PostMapping("/api/rag/kb/create")
    Result<Map<String, Object>> createCollection(@RequestBody Map<String, String> params);
}