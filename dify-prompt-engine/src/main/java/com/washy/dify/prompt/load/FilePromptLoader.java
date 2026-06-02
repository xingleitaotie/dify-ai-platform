package com.washy.dify.prompt.load;

import com.washy.dify.prompt.config.PromptConfig;
import com.washy.dify.prompt.core.ModelParams;
import com.washy.dify.prompt.core.PromptTemplate;
import com.washy.dify.prompt.exception.PromptException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FilePromptLoader implements PromptLoader {

    private final PromptConfig config;
    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private final Yaml yaml = new Yaml();

    public FilePromptLoader(PromptConfig config) {
        this.config = config;
        loadAll();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PromptTemplate> loadAll() {
        List<PromptTemplate> templates = new ArrayList<>();

        try {
            String locationPattern = config.getTemplatePath();
            if (!locationPattern.endsWith("/")) {
                locationPattern += "/";
            }
            locationPattern += "*.yaml";

            Resource[] resources = resourceResolver.getResources(locationPattern);

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> data = yaml.load(is);

                    String name = (String) data.getOrDefault("name",
                            resource.getFilename().replace(".yaml", ""));
                    String version = (String) data.getOrDefault("version", "v1.0.0");
                    String description = (String) data.getOrDefault("description", "");
                    String template = (String) data.getOrDefault("template", "");
                    boolean streaming = (boolean) data.getOrDefault("streaming", false);

                    ModelParams modelParams = parseModelParams(data);

                    PromptTemplate promptTemplate = createTemplate(
                            name, version, description, template, modelParams, streaming
                    );

                    templates.add(promptTemplate);
                    templateCache.put(name, promptTemplate);
                    log.info("加载提示词模板: {} (版本: {})", name, version);
                } catch (Exception e) {
                    log.error("解析模板文件失败: {}", resource.getFilename(), e);
                }
            }

        } catch (Exception e) {
            log.error("加载提示词模板失败", e);
            throw new PromptException("LOAD_ERROR", "加载提示词模板失败", e);
        }

        return templates;
    }

    @SuppressWarnings("unchecked")
    private ModelParams parseModelParams(Map<String, Object> data) {
        ModelParams.ModelParamsBuilder builder = ModelParams.builder();

        Map<String, Object> params = (Map<String, Object>) data.get("model_params");
        if (params != null) {
            if (params.containsKey("temperature")) {
                builder.temperature(((Number) params.get("temperature")).floatValue());
            }
            if (params.containsKey("max_tokens")) {
                builder.maxTokens(((Number) params.get("max_tokens")).intValue());
            }
            if (params.containsKey("top_p")) {
                builder.topP(((Number) params.get("top_p")).floatValue());
            }
            if (params.containsKey("top_k")) {
                builder.topK(((Number) params.get("top_k")).intValue());
            }
            if (params.containsKey("repeat_penalty")) {
                builder.repeatPenalty(((Number) params.get("repeat_penalty")).floatValue());
            }
        }

        return builder.build();
    }

    private PromptTemplate createTemplate(String name, String version, String description,
                                          String templateContent, ModelParams modelParams,
                                          boolean streaming) {
        return new PromptTemplate() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getVersion() {
                return version;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String render(Map<String, Object> context) {
                String result = templateContent;
                for (Map.Entry<String, Object> entry : context.entrySet()) {
                    String placeholder = "${" + entry.getKey() + "}";
                    String value = entry.getValue() != null ? entry.getValue().toString() : "";
                    result = result.replace(placeholder, value);
                }
                return result;
            }

            @Override
            public ModelParams getModelParams() {
                return modelParams;
            }

            @Override
            public boolean isStreaming() {
                return streaming;
            }
        };
    }

    @Override
    public PromptTemplate loadByName(String name) {
        return templateCache.get(name);
    }

    @Override
    public void reload() {
        templateCache.clear();
        loadAll();
        log.info("提示词模板已重新加载，共 {} 个模板", templateCache.size());
    }
}