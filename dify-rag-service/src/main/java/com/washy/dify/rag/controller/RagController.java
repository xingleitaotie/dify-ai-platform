package com.washy.dify.rag.controller;

import com.washy.dify.common.entity.rag.EmbeddingDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.rag.domain.dto.ChunkResult;
import com.washy.dify.rag.domain.dto.RagSearchRequest;
import com.washy.dify.rag.factory.EmbeddingFactory;
import com.washy.dify.rag.factory.VectorStoreFactory;
import com.washy.dify.rag.service.AdaptiveTextChunker;
import com.washy.dify.rag.service.RagQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG控制器 - Day2 文档上传+分块
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/rag")
public class RagController {

    private final EmbeddingFactory embeddingFactory;
    private final VectorStoreFactory vectorStoreFactory;
    private final AdaptiveTextChunker chunker;
    private final RagQueryService ragQueryService;
    /**
     * 文档上传并自动分块
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kbName") String kbName) {
        long start = System.currentTimeMillis();
        log.info("收到文档上传请求：{}", file.getOriginalFilename());
        ChunkResult result = chunker.processDocument(file,kbName);
        long elapsed = System.currentTimeMillis() - start;
            // ===== 构建响应 =====
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("documentId", result.getDocumentId());
        response.put("fileName", result.getFileName());
        response.put("chunks", result.getChunkCount());
        response.put("images", result.getImageCount());
        response.put("tables", result.getTableCount());
        response.put("processingTime", elapsed + "ms");
        response.put("method", "file_upload");

        if (!result.isSuccess()) {
            response.put("error", result.getError());
        }

        return ResponseEntity.ok(response);

    }

    @PostMapping("/embedding/single")
    public Result<List<Float>> singleEmbedding(@RequestBody EmbeddingDTO dto) {
        log.info("收到单文本向量化请求");
        return Result.success(embeddingFactory.getEmbeddingService().getEmbedding(dto.getText()));
    }

    /**
     * 工作流查询向量库测试检索功能
     */
    @PostMapping("/search/single/document")
    public Result<Map<String, Object>> searchSignleDocument(@RequestBody RagSearchRequest dto) {

        try {
            if (dto != null && !dto.getQuery().isEmpty()) {
                String query = dto.getQuery();
                String kb = dto.getKb() == null ? "" : dto.getKb();
                int topK = dto.getTopK() == 0 ? 5 : dto.getTopK();

                // 1. 检索相关文档块
                Map<String, Object> content = ragQueryService.queryForWorkflowStructured(query,kb,topK);

                return Result.success(content);
            }else{
                log.error("RAG查询添加为空");
                return Result.error("RAG查询添加为空");
            }

        } catch (Exception e) {
            log.error("RAG查询失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 多个知识库节点查询
     * 工作流查询向量库形成提示词
     */
    @PostMapping("/search/much/document")
    public Result<String> searchMuchDocument(@RequestBody Map<String,Object> request) {

        try {
            if (request != null && null != request.get("query") && !"".equals(request.get("query"))) {
                String query = request.get("query").toString();
                List<String> kbs = Collections.singletonList("");
                Object kbsObj = request.get("kbs");
                if (kbsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> temp = (List<String>) kbsObj;
                    kbs = temp;
                }
                int topN = request.get("topN") == "5" ? Integer.parseInt(request.get("topN").toString()) : 5;

                // 1. 检索相关文档块
                String response = ragQueryService.queryMultipleForWorkflow(query,kbs,topN);

                return Result.success(response);
            }else{
                log.error("RAG查询添加为空");
                return Result.error("RAG查询添加为空");
            }

        } catch (Exception e) {
            log.error("RAG查询失败", e);
            return Result.error(e.getMessage());
        }
    }



    // ==================== 提示词模板管理接口 ====================

    /**
     * 存储提示词模板到向量库
     */
    @PostMapping("/prompt-template/store")
    public Result<Void> storePromptTemplate(@RequestBody Map<String, Object> request) {
        try {
            String templateId = (String) request.get("templateId");
            String templateName = (String) request.get("templateName");
            String content = (String) request.get("content");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) request.getOrDefault("metadata", new HashMap<>());

            if (templateId == null || content == null) {
                return Result.error("templateId和content不能为空");
            }

            vectorStoreFactory.getVectorStoreService()
                    .storePromptTemplate(templateId, templateName, content, metadata);

            return Result.success();

        } catch (Exception e) {
            log.error("存储提示词模板失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量存储提示词模板
     */
    @PostMapping("/prompt-template/batch-store")
    public Result<Void> batchStorePromptTemplates(@RequestBody List<Map<String, Object>> templates) {
        try {
            vectorStoreFactory.getVectorStoreService().batchStorePromptTemplates(templates);
            return Result.success();
        } catch (Exception e) {
            log.error("批量存储提示词模板失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除提示词模板
     */
    @DeleteMapping("/prompt-template/{templateId}")
    public Result<Void> deletePromptTemplate(@PathVariable String templateId) {
        try {
            vectorStoreFactory.getVectorStoreService().deletePromptTemplate(templateId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除提示词模板失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量删除提示词模板
     */
    @DeleteMapping("/prompt-template/batch-delete")
    public Result<Void> batchDeletePromptTemplates(@RequestBody List<String> templateIds) {
        try {
            vectorStoreFactory.getVectorStoreService().batchDeletePromptTemplates(templateIds);
            return Result.success();
        } catch (Exception e) {
            log.error("批量删除提示词模板失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 搜索相似提示词模板
     */
    @PostMapping("/prompt-template/search")
    public Result<List<Map<String, Object>>> searchPromptTemplates(@RequestBody Map<String, Object> request) {
        try {
            String query = (String) request.get("query");
            int topK = request.get("topK") != null ? (Integer) request.get("topK") : 5;

            if (query == null || query.trim().isEmpty()) {
                return Result.error("查询内容不能为空");
            }

            List<Map<String, Object>> results = vectorStoreFactory.getVectorStoreService()
                    .searchPromptTemplates(query, topK);

            return Result.success(results);

        } catch (Exception e) {
            log.error("搜索提示词模板失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有提示词模板
     */
    @GetMapping("/prompt-template/list")
    public Result<List<Map<String, Object>>> listAllPromptTemplates() {
        try {
            List<Map<String, Object>> templates = vectorStoreFactory.getVectorStoreService()
                    .listAllPromptTemplates();
            return Result.success(templates);
        } catch (Exception e) {
            log.error("获取提示词模板列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取提示词模板数量
     */
    @GetMapping("/prompt-template/count")
    public Result<Integer> getPromptTemplateCount() {
        try {
            int count = vectorStoreFactory.getVectorStoreService().getPromptTemplateCount();
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取提示词模板数量失败", e);
            return Result.error(e.getMessage());
        }
    }
}