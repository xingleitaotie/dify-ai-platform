package com.washy.dify.prompt.editor;

import com.washy.dify.common.entity.prompt.PromptTemplateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存提示词编辑器（可替换为数据库实现）
 */
@Slf4j
@Service
public class InMemoryPromptEditor implements PromptEditor {
    
    private final Map<String, PromptTemplateVO> templateStore = new ConcurrentHashMap<>();
    
    @Override
    public PromptTemplateVO save(PromptTemplateVO template) {
        String id = UUID.randomUUID().toString();
        template.setId(id);
        template.setCreatedAt(new Date());
        template.setUpdatedAt(new Date());
        template.setStatus("DRAFT");
        
        templateStore.put(id, template);
        log.info("保存提示词模板: {} ({})", template.getName(), id);
        
        return template;
    }
    
    @Override
    public PromptTemplateVO update(String id, PromptTemplateVO template) {
        PromptTemplateVO existing = templateStore.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        
        template.setId(id);
        template.setCreatedAt(existing.getCreatedAt());
        template.setUpdatedAt(new Date());
        template.setStatus(existing.getStatus());
        
        templateStore.put(id, template);
        log.info("更新提示词模板: {} ({})", template.getName(), id);
        
        return template;
    }

    @Override
    public void delete(String id) {
        PromptTemplateVO removed = templateStore.remove(id);
        if (removed != null) {
            log.info("删除提示词模板: {}", removed.getName());
        }
    }
    
    @Override
    public PromptTemplateVO get(String id) {
        return templateStore.get(id);
    }
    
    @Override
    public List<PromptTemplateVO> listAll() {
        return new ArrayList<>(templateStore.values());
    }
    
    @Override
    public PromptTemplateVO getByName(String name) {
        return templateStore.values().stream()
            .filter(t -> t.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public String test(String templateId, Map<String, Object> context) {
        PromptTemplateVO vo = templateStore.get(templateId);
        if (vo == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        
        // 渲染提示词
        String rendered = renderTemplate(vo.getTemplate(), context);

        // 返回渲染结果，实际应该调用 LLM
        return rendered;
    }
    
    @Override
    public PromptTemplateVO copy(String id, String newName) {
        PromptTemplateVO original = templateStore.get(id);
        if (original == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        
        PromptTemplateVO copy = new PromptTemplateVO();
        copy.setName(newName);
        copy.setVersion("v1.0.0");
        copy.setDescription("复制自: " + original.getName());
        copy.setTemplate(original.getTemplate());
        copy.setModelParams(original.getModelParams());
        copy.setStreaming(original.getStreaming());
        copy.setStatus("DRAFT");
        
        return save(copy);
    }
    
    @Override
    public void setStatus(String id, String status) {
        PromptTemplateVO template = templateStore.get(id);
        if (template != null) {
            template.setStatus(status);
            template.setUpdatedAt(new Date());
            log.info("模板状态变更: {} -> {}", template.getName(), status);
        }
    }
    
    private String renderTemplate(String template, Map<String, Object> context) {
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}