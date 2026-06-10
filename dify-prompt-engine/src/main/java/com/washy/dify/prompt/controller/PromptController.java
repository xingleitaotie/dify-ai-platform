package com.washy.dify.prompt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.washy.dify.common.entity.prompt.PromptTemplateVO;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.RagFeignClient;
import com.washy.dify.prompt.entity.GenerateRequest;
import com.washy.dify.prompt.entity.GenerateResponse;
import com.washy.dify.prompt.entity.PromptTemplateEntity;
import com.washy.dify.prompt.generator.AIPromptGenerator;
import com.washy.dify.prompt.generator.TemplateBasedGenerator;
import com.washy.dify.prompt.service.DynamicRouterService;
import com.washy.dify.prompt.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 提示词管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/prompt")
public class PromptController {
    
    @Autowired
    private AIPromptGenerator aiPromptGenerator;

    @Autowired
    private PromptTemplateService templateService;

    @Autowired
    private TemplateBasedGenerator templateBasedGenerator;

    @Autowired
    private DynamicRouterService dynamicRouterService;

    @Autowired
    private RagFeignClient ragFeignClient;

    /**
     * AI 生成提示词
     */
    @PostMapping("/generate")
    public Result<GenerateResponse> generate(@Valid @RequestBody GenerateRequest request, HttpServletRequest httpRequest) {
        log.info("收到提示词生成请求: requirement={}, type={}",
                request.getRequirement(), request.getType());

        long startTime = System.currentTimeMillis();

        // 使用指定的模型配置
        GenerateResponse response = null;

        // 方案四：先尝试大模型，失败时降级到模板
        try {
            log.info("尝试使用大模型生成提示词...");
            response = aiPromptGenerator.generate(request);
        } catch (Exception e) {
            log.warn("大模型生成失败，降级使用模板生成", e);
            response = templateBasedGenerator.generate(request);
        }

        long responseTime = System.currentTimeMillis() - startTime;
        log.info("生成完成，耗时: {}ms, 置信度: {}%",
                responseTime, response.getConfidenceScore());

        return Result.success(response);
    }

    /**
     * 保存模板
     */
    @PostMapping("/template")
    public Result<PromptTemplateVO> saveTemplate(@RequestBody PromptTemplateVO template) {
        PromptTemplateEntity entity = templateService.saveTemplate(template);

        PromptTemplateVO vo =  templateService.toVO(entity);
        return Result.success(vo);
    }

    /**
     * 更新模板
     */
    @PutMapping("/template/{id}")
    public Result<PromptTemplateVO> updateTemplate(@PathVariable String id,
                                           @RequestBody PromptTemplateVO template) {
        PromptTemplateEntity entity = templateService.updateTemplate(id, template);
        PromptTemplateVO vo =  templateService.toVO(entity);
        return Result.success(vo);
    }

    /**
     * 获取模板
     */
    @GetMapping("/template/{id}")
    public Result<PromptTemplateVO> getTemplate(@PathVariable String id) {
        PromptTemplateEntity entity = templateService.getTemplate(id);
        PromptTemplateVO vo = templateService.toVO(entity);
        return Result.success(vo);
    }

    /**
     * 获取所有模板
     */
    @GetMapping("/templates")
    public Result<List<PromptTemplateVO>> listTemplates() {
        List<PromptTemplateEntity> entities = templateService.listAllTemplates();
        List<PromptTemplateVO> list = entities.stream()
                .map(templateService::toVO)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    /**
     * 分页查询模板
     */
    @GetMapping("/templates/page")
    public Result<Map<String, Object>> pageTemplates(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {

        Page<PromptTemplateEntity> pageResult = templateService.pageTemplates(page, size, keyword, status, type);

        Map<String, Object> result = new HashMap<>();
        result.put("total", pageResult.getTotal());
        result.put("records", pageResult.getRecords().stream()
                .map(templateService::toVO)
                .collect(Collectors.toList()));
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return Result.success(result);
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/template/{id}")
    public Result<Map<String, String>> deleteTemplate(@PathVariable String id) {
        templateService.deleteTemplate(id);
        Map<String, String> result = new HashMap<>();
        result.put("message", "删除成功");
        return Result.success(result);
    }

    /**
     * 测试模板渲染
     */
    @PostMapping("/template/{id}/test")
    public Result<Map<String, String>> testTemplate(@PathVariable String id,
                                            @RequestBody Map<String, Object> context) {
        PromptTemplateEntity entity = templateService.getTemplate(id);
        if (entity == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }

        String result = entity.getTemplateContent();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }

        Map<String, String> response = new HashMap<>();
        response.put("rendered", result);
        return Result.success(response);
    }

    /**
     * 复制模板
     */
    @PostMapping("/template/{id}/copy")
    public Result<PromptTemplateVO> copyTemplate(@PathVariable String id,
                                         @RequestParam String newName) {
        PromptTemplateEntity entity = templateService.copyTemplate(id, newName);
        PromptTemplateVO vo = templateService.toVO(entity);
        return Result.success(vo);
    }

    /**
     * 启用/禁用模板
     */
    @PutMapping("/template/{id}/status")
    public Result<Boolean> setStatus(@PathVariable String id, @RequestParam String status) {
        Boolean flag = templateService.setStatus(id, status);
        return Result.success(flag);
    }

    /**
     * 根据类型获取模板
     */
    @GetMapping("/templates/by-type")
    public Result<List<PromptTemplateVO>> getTemplatesByType(@RequestParam String type) {
        List<PromptTemplateEntity> entities = templateService.getActiveTemplatesByType(type);
        List<PromptTemplateVO> list = entities.stream()
                .map(templateService::toVO)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    /**
     * 搜索模板
     */
    @GetMapping("/templates/search")
    public Result<List<PromptTemplateVO>> searchTemplates(@RequestParam String keyword) {
        List<PromptTemplateEntity> entities = templateService.searchTemplates(keyword);
        List<PromptTemplateVO> list = entities.stream()
                .map(templateService::toVO)
                .collect(Collectors.toList());

        return Result.success(list);
    }

    /**
     * 动态路由：根据用户查询智能选择提示词模板
     */
    @PostMapping("/route")
    public Result<PromptTemplateVO> route(@RequestBody Map<String, String> request) {
        String userQuery = request.get("query");
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return Result.error("查询内容不能为空");
        }

        try {
            PromptTemplateEntity entity = dynamicRouterService.route(userQuery);
            if (entity == null) {
                return Result.error("未找到合适的模板");
            }
            PromptTemplateVO vo = templateService.toVO(entity);
            return Result.success(vo);
        } catch (Exception e) {
            log.error("动态路由失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 手动同步所有ACTIVE模板到向量库（管理接口）
     */
    @PostMapping("/templates/sync-to-vector")
    public Result<String> syncAllToVector() {
        try {
            templateService.syncAllActiveTemplatesToVectorStore();
            return Result.success("同步完成");
        } catch (Exception e) {
            log.error("同步失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量导入模板并同步到向量库
     */
    @PostMapping("/templates/batch-import")
    public Result<String> batchImport(@RequestBody List<PromptTemplateVO> templates) {
        try {
            int successCount = 0;
            for (PromptTemplateVO vo : templates) {
                try {
                    templateService.saveTemplate(vo);
                    successCount++;
                } catch (Exception e) {
                    log.error("导入模板失败: {}", vo.getName(), e);
                }
            }
            return Result.success("导入完成，成功: " + successCount + "，失败: " + (templates.size() - successCount));
        } catch (Exception e) {
            log.error("批量导入失败", e);
            return Result.error(e.getMessage());
        }
    }

    // ==================== 新增：向量库相关接口 ====================

    /**
     * 获取所有提示词模板向量（从向量库获取）
     * 用于前端查看同步状态
     */
    @GetMapping("/vector/templates")
    public Result<List<Map<String, Object>>> listVectorTemplates() {
        try {
            Result<List<Map<String, Object>>> result = ragFeignClient.listAllPromptTemplates();
            return result;
        } catch (Exception e) {
            log.error("获取向量库模板列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取提示词模板向量数量
     */
    @GetMapping("/vector/count")
    public Result<Integer> getVectorTemplateCount() {
        try {
            Result<Integer> result = ragFeignClient.getPromptTemplateCount();
            log.info("向量库数量返回: code={}, data={}", result.getCode(), result.getData());

            if (result != null && result.getCode() == 200) {
                return Result.success(result.getData());
            }
            return Result.success(0);
        } catch (Exception e) {
            log.error("获取向量库模板数量失败", e);
            return Result.success(0);
        }
    }

    /**
     * 搜索相似模板（向量检索）
     * @param request { query: string, topK?: number }
     */
    @PostMapping("/vector/search")
    public Result<List<Map<String, Object>>> searchVectorTemplates(@RequestBody Map<String, Object> request) {
        try {
            String query = (String) request.get("query");
            Integer topK = request.get("topK") != null ? (Integer) request.get("topK") : 5;

            if (query == null || query.trim().isEmpty()) {
                return Result.error("查询内容不能为空");
            }

            Map<String, Object> searchRequest = new HashMap<>();
            searchRequest.put("query", query);
            searchRequest.put("topK", topK);

            Result<List<Map<String, Object>>> result = ragFeignClient.searchPromptTemplates(searchRequest);
            return result;
        } catch (Exception e) {
            log.error("向量检索失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 同步单个模板到向量库
     */
    @PostMapping("/vector/sync/{templateId}")
    public Result<Void> syncTemplateToVector(@PathVariable String templateId) {
        try {
            PromptTemplateEntity entity = templateService.getTemplate(templateId);
            if (entity == null) {
                return Result.error("模板不存在");
            }

            // 构建请求
            Map<String, Object> request = new HashMap<>();
            request.put("templateId", entity.getId());
            request.put("templateName", entity.getName());

            // 构建用于向量化的内容
            String content = buildVectorContent(entity);
            request.put("content", content);

            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", entity.getType() != null ? entity.getType() : "CUSTOM");
            metadata.put("category", entity.getCategory() != null ? entity.getCategory() : "");
            metadata.put("tags", entity.getTags() != null ? entity.getTags() : "");
            metadata.put("status", entity.getStatus());
            metadata.put("useCount", entity.getUseCount() != null ? entity.getUseCount() : 0);
            metadata.put("description", entity.getDescription() != null ? entity.getDescription() : "");
            request.put("metadata", metadata);

            ragFeignClient.storePromptTemplate(request);
            return Result.success();
        } catch (Exception e) {
            log.error("同步模板到向量库失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 从向量库删除模板
     */
    @DeleteMapping("/vector/template/{templateId}")
    public Result<Void> deleteVectorTemplate(@PathVariable String templateId) {
        try {
            ragFeignClient.deletePromptTemplate(templateId);
            return Result.success();
        } catch (Exception e) {
            log.error("从向量库删除模板失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 强制重新同步（清空后重新同步所有ACTIVE模板）
     */
    @PostMapping("/vector/resync")
    public Result<String> forceResync() {
        try {
            // 先清空向量库中的模板集合
            ragFeignClient.deleteCollection("prompt_templates");
            Map<String,String> collectionInfo = new HashMap<>();
            collectionInfo.put("name", "prompt_templates");
            collectionInfo.put("description","提示词模板向量库");
            ragFeignClient.createCollection(collectionInfo);

            // 重新同步
            templateService.syncAllActiveTemplatesToVectorStore();
            return Result.success("重新同步完成");
        } catch (Exception e) {
            log.error("强制重新同步失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 构建用于向量化的内容
     */
    private String buildVectorContent(PromptTemplateEntity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("模板名称：").append(entity.getName()).append("\n");
        sb.append("模板类型：").append(entity.getType() != null ? entity.getType() : "CUSTOM").append("\n");
        sb.append("分类：").append(entity.getCategory() != null ? entity.getCategory() : "").append("\n");
        sb.append("标签：").append(entity.getTags() != null ? entity.getTags() : "").append("\n");
        sb.append("描述：").append(entity.getDescription() != null ? entity.getDescription() : "").append("\n");
        sb.append("模板内容：\n").append(entity.getTemplateContent() != null ? entity.getTemplateContent() : "");

        if (entity.getSystemPrompt() != null && !entity.getSystemPrompt().isEmpty()) {
            sb.append("\n系统提示词：\n").append(entity.getSystemPrompt());
        }
        if (entity.getUserPromptTemplate() != null && !entity.getUserPromptTemplate().isEmpty()) {
            sb.append("\n用户提示词模板：\n").append(entity.getUserPromptTemplate());
        }

        return sb.toString();
    }
}