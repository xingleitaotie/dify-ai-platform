package com.washy.dify.rag.controller;

import com.washy.dify.common.result.Result;
import com.washy.dify.rag.factory.VectorStoreFactory;
import com.washy.dify.rag.service.RagQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rag/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {
    
    private final VectorStoreFactory factory;
    private final RagQueryService ragQueryService;
    
    /**
     * 获取所有知识库
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = factory.getVectorStoreService().listKnowledgeBases();
        return Result.success(list);
    }
    
    /**
     * 创建知识库
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, String> params) {
        String name = params.get("name");
        String description = params.get("description") == null ? "" : params.get("description");
        
        if (name == null || name.trim().isEmpty()) {
            return Result.error("知识库名称不能为空");
        }
        
        Map<String, Object> result = factory.getVectorStoreService().createKnowledgeBase(name, description);
        return Result.success(result);
    }
    
    /**
     * 删除知识库
     */
    @DeleteMapping("/{name}")
    public Result<Void> delete(@PathVariable String name) {
        factory.getVectorStoreService().deleteKnowledgeBase(name);
        return Result.success();
    }
    
    /**
     * 获取知识库的分块
     */
    @GetMapping("/{name}/chunks")
    public Result<List<Map<String, Object>>> getChunks(@PathVariable String name) {
        List<Map<String, Object>> chunks = factory.getVectorStoreService().listAllChunks(name);
        return Result.success(chunks);
    }
    
    /**
     * 在知识库中检索
     */
    @PostMapping("/{name}/search")
    public ResponseEntity<Map<String, Object>> search(@PathVariable String name,
                                                     @RequestBody Map<String, Object> params) {
        try {
            String query = (String) params.get("text");
            Integer topN = params.get("topN") != null ? (Integer) params.get("topN") : 5;
            String configId = params.get("configId") == null ? "" : params.get("configId").toString();
            // 1. 检索相关文档块
            RagQueryService.RAGResponse response = ragQueryService.ragQuery(query,name,topN,configId);


            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("answer", response.getAnswer());
            result.put("sources", response.getRetrievedDocs().size());
            result.put("details", response.getRetrievedDocs());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("RAG查询失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取知识库的文档列表
     */
    @GetMapping("/{kbId}/documents")
    public Result<List<Map<String, Object>>> getDocuments(@PathVariable String kbId) {
        List<Map<String, Object>> documents = factory.getVectorStoreService().getDocuments(kbId);
        return Result.success(documents);
    }



    /**
     * 获取知识库配置
     */
    @GetMapping("/{kbId}/config")
    public Result<Map<String, Object>> getConfig(@PathVariable String kbId) {
        Map<String, Object> config = factory.getVectorStoreService().getConfig(kbId);
        return Result.success(config);
    }

    /**
     * 更新知识库配置
     */
    @PutMapping("/{kbId}/config")
    public Result<Void> updateConfig(@PathVariable String kbId, @RequestBody Map<String, Object> config) {
        factory.getVectorStoreService().updateConfig(kbId, config);
        return Result.success();
    }

    /**
     * 删除知识库中的文档
     */
    @DeleteMapping("/{kbName}/documents/{documentId}")
    public Result<Void> deleteDocument(
            @PathVariable String kbName,
            @PathVariable String documentId) {

        factory.getVectorStoreService().deleteDocumentChunks(kbName, documentId);
        return Result.success();
    }
}