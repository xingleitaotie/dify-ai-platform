package com.washy.dify.prompt.initializer;

import com.washy.dify.prompt.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时智能同步模板到向量库
 * - 首次启动：全量同步
 * - 后续启动：只同步有变化的模板
 */
@Slf4j
@Component
public class TemplateVectorInitializer implements CommandLineRunner {

    @Autowired
    private PromptTemplateService templateService;

    @Override
    public void run(String... args) {
        log.info("启动模板向量库智能同步...");

        try {
            // 调用智能同步（内部自动判断是否需要同步）
            templateService.smartSyncToVectorStore();
            log.info("模板向量库初始化完成");
        } catch (Exception e) {
            log.error("模板向量库初始化失败", e);
            // 初始化失败不影响服务启动
        }
    }
}