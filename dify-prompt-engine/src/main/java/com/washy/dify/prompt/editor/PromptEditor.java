package com.washy.dify.prompt.editor;

import com.washy.dify.prompt.entity.PromptTemplateVO;

import java.util.List;
import java.util.Map;

/**
 * 提示词编辑器接口
 */
public interface PromptEditor {
    
    /**
     * 保存模板
     */
    PromptTemplateVO save(PromptTemplateVO template);
    
    /**
     * 更新模板
     */
    PromptTemplateVO update(String id, PromptTemplateVO template);
    
    /**
     * 删除模板
     */
    void delete(String id);
    
    /**
     * 获取模板
     */
    PromptTemplateVO get(String id);
    
    /**
     * 获取所有模板
     */
    List<PromptTemplateVO> listAll();
    
    /**
     * 根据名称获取
     */
    PromptTemplateVO getByName(String name);
    
    /**
     * 测试模板
     */
    String test(String templateId, Map<String, Object> context);
    
    /**
     * 复制模板
     */
    PromptTemplateVO copy(String id, String newName);
    
    /**
     * 启用/禁用模板
     */
    void setStatus(String id, String status);
}