package com.washy.dify.prompt.load;

import com.washy.dify.prompt.core.ModelParams;
import com.washy.dify.prompt.core.PromptTemplate;
import com.washy.dify.prompt.exception.PromptException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 数据库提示词加载器（支持动态刷新）
 */
@Slf4j
public class DatabasePromptLoader implements PromptLoader {
    
    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private int refreshIntervalSeconds = 60;
    
    public DatabasePromptLoader() {
        startAutoRefresh();
    }
    
    public DatabasePromptLoader(int refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
        startAutoRefresh();
    }
    
    private void startAutoRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                reload();
            } catch (Exception e) {
                log.error("自动刷新提示词模板失败", e);
            }
        }, refreshIntervalSeconds, refreshIntervalSeconds, TimeUnit.SECONDS);
    }
    
    @Override
    public List<PromptTemplate> loadAll() {
        List<PromptTemplate> templates = new ArrayList<>();
        
        // TODO: 从数据库加载
        // SELECT * FROM prompt_template WHERE status = 'ACTIVE'
        // 这里提供一个示例实现框架
        try {
            List<PromptTemplate> dbTemplates = loadFromDatabase();
            for (PromptTemplate template : dbTemplates) {
                templates.add(template);
                templateCache.put(template.getName(), template);
            }
            log.info("从数据库加载提示词模板，共 {} 个", templates.size());
        } catch (Exception e) {
            log.error("从数据库加载提示词模板失败", e);
            throw new PromptException("DB_LOAD_ERROR", "数据库加载失败", e);
        }
        
        return templates;
    }
    
    private List<PromptTemplate> loadFromDatabase() {
        // 示例：实际使用时替换为真实的数据库查询
        List<PromptTemplate> templates = new ArrayList<>();
        
        // 示例数据
        templates.add(new DatabasePromptTemplate(
            "document.summary",
            "v2.1.0",
            "文档摘要模板",
            buildDefaultSummaryTemplate(),
            ModelParams.forSummary(),
            false
        ));
        
        return templates;
    }
    
    private String buildDefaultSummaryTemplate() {
        return "<|im_start|>system\n你是文档摘要助手。请对以下文本生成30-50字的摘要。\n" +
               "要求：只输出摘要，不要有任何前缀。\n<|im_end|>\n" +
               "<|im_start|>user\n${content}\n<|im_end|>\n<|im_start|>assistant\n";
    }
    
    @Override
    public PromptTemplate loadByName(String name) {
        return templateCache.get(name);
    }
    
    @Override
    public void reload() {
        templateCache.clear();
        loadAll();
        log.info("数据库提示词模板已重新加载，共 {} 个模板", templateCache.size());
    }
    
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
    
    /**
     * 数据库提示词模板实现
     */
    private static class DatabasePromptTemplate implements PromptTemplate {
        private final String name;
        private final String version;
        private final String description;
        private final String template;
        private final ModelParams modelParams;
        private final boolean streaming;
        
        public DatabasePromptTemplate(String name, String version, String description, 
                                      String template, ModelParams modelParams, boolean streaming) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.template = template;
            this.modelParams = modelParams;
            this.streaming = streaming;
        }
        
        @Override
        public String getName() { return name; }
        
        @Override
        public String getVersion() { return version; }
        
        @Override
        public String getDescription() { return description; }
        
        @Override
        public String render(Map<String, Object> context) {
            String result = template;
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
            return result;
        }
        
        @Override
        public ModelParams getModelParams() { return modelParams; }
        
        @Override
        public boolean isStreaming() { return streaming; }
    }
}