package com.washy.dify.prompt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.washy.dify.common.result.Result;
import com.washy.dify.prompt.entity.GenerateRequest;
import com.washy.dify.prompt.entity.GenerateResponse;
import com.washy.dify.prompt.entity.PromptTemplateEntity;
import com.washy.dify.prompt.entity.PromptTemplateVO;
import com.washy.dify.prompt.generator.AIPromptGenerator;
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
    private AIPromptGenerator promptGenerator;

    @Autowired
    private PromptTemplateService templateService;

    /**
     * AI 生成提示词
     */
    @PostMapping("/generate")
    public Result<GenerateResponse> generate(@Valid @RequestBody GenerateRequest request, HttpServletRequest httpRequest) {
        log.info("收到提示词生成请求: requirement={}, type={}, modelConfigId={}",
                request.getRequirement(), request.getType(), request.getModelConfigId());

        long startTime = System.currentTimeMillis();

        // 使用指定的模型配置
        GenerateResponse response = promptGenerator.generateWithModel(request, request.getModelConfigId());

        long responseTime = System.currentTimeMillis() - startTime;
        log.info("生成完成，耗时: {}ms, 置信度: {}%, 使用的模型配置ID: {}",
                responseTime, response.getConfidenceScore(), request.getModelConfigId());

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
}