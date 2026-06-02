package com.washy.dify.prompt.core;

import com.washy.dify.prompt.exception.PromptException;
import com.washy.dify.prompt.load.PromptLoader;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词管理器 - 核心服务
 */
@Slf4j
public class PromptManager {

    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();
    private final Map<String, PromptTemplate> versionedTemplates = new ConcurrentHashMap<>();
    private List<PromptTemplate> templates;
    private PromptLoader promptLoader;
    public void setTemplates(List<PromptTemplate> templates) {
        this.templates = templates;
    }

    public void setPromptLoader(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    @PostConstruct
    public void init() {
        // 加载内置模板
        if (templates != null) {
            for (PromptTemplate template : templates) {
                registerTemplate(template);
            }
        }

        if (promptLoader != null) {
            List<PromptTemplate> externalTemplates = promptLoader.loadAll();
            for (PromptTemplate template : externalTemplates) {
                registerTemplate(template);
            }
        }

        log.info("提示词管理器初始化完成，共加载 {} 个模板", templateCache.size());
    }

    public void registerTemplate(PromptTemplate template) {
        String key = template.getName();
        templateCache.put(key, template);

        String versionKey = key + ":" + template.getVersion();
        versionedTemplates.put(versionKey, template);

        log.debug("注册提示词模板: {} (版本: {})", key, template.getVersion());
    }

    public String render(String templateName, Map<String, Object> context) {
        PromptTemplate template = templateCache.get(templateName);
        if (template == null) {
            throw new PromptException("未找到提示词模板: " + templateName);
        }
        return template.render(context);
    }

    public String renderWithVersion(String templateName, String version, Map<String, Object> context) {
        String key = templateName + ":" + version;
        PromptTemplate template = versionedTemplates.get(key);
        if (template == null) {
            throw new PromptException("未找到提示词模板: " + key);
        }
        return template.render(context);
    }

    public ModelParams getModelParams(String templateName) {
        PromptTemplate template = templateCache.get(templateName);
        if (template == null) {
            return ModelParams.builder().build();
        }
        return template.getModelParams();
    }

    public PromptTemplate getTemplate(String templateName) {
        return templateCache.get(templateName);
    }

    public boolean exists(String templateName) {
        return templateCache.containsKey(templateName);
    }

    public List<String> getAllTemplateNames() {
        return new ArrayList<>(templateCache.keySet());
    }
}