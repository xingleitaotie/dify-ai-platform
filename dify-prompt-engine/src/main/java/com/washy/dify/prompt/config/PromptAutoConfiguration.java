package com.washy.dify.prompt.config;

import com.washy.dify.prompt.core.PromptManager;
import com.washy.dify.prompt.core.PromptTemplate;
import com.washy.dify.prompt.load.FilePromptLoader;
import com.washy.dify.prompt.load.PromptLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 提示词模块自动配置
 */
@Configuration
@ComponentScan("com.washy.dify.prompt")
@EnableConfigurationProperties({PromptConfig.class, PromptVersionConfig.class})
public class PromptAutoConfiguration {

    @Autowired(required = false)
    private List<PromptTemplate> templates;

    @Bean
    @ConditionalOnMissingBean
    public PromptLoader promptLoader(PromptConfig promptConfig) {
        return new FilePromptLoader(promptConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptManager promptManager(PromptLoader promptLoader) {
        PromptManager manager = new PromptManager();
        manager.setTemplates(templates);
        manager.setPromptLoader(promptLoader);
        return manager;
    }
}