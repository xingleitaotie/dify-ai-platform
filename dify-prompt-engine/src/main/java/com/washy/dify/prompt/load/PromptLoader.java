package com.washy.dify.prompt.load;


import com.washy.dify.prompt.core.PromptTemplate;

import java.util.List;

/**
 * 提示词加载器接口
 */
public interface PromptLoader {
    
    /**
     * 加载所有提示词模板
     */
    List<PromptTemplate> loadAll();
    
    /**
     * 根据名称加载单个模板
     */
    PromptTemplate loadByName(String name);
    
    /**
     * 重新加载
     */
    void reload();
}