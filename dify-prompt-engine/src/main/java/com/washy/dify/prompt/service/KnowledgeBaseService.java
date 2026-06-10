package com.washy.dify.prompt.service;

import com.washy.dify.common.constants.SystemConstants;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.RagFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库服务 - 全局共享一个向量库
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    @Autowired
    private RagFeignClient ragFeignClient;

    // 标记知识库是否已创建（避免重复创建）
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * 获取提示词模板向量库名称（全局唯一）
     */
    public String getVectorStoreName() {
        return SystemConstants.PROMPT_TEMPLATE_COLLECTION;
    }

    /**
     * 确保知识库存在（首次使用时创建）
     */
    public void ensureKnowledgeBaseExists() {
        // 双重检查，避免重复创建
        if (isInitialized.get()) {
            return;
        }

        synchronized (this) {
            if (isInitialized.get()) {
                return;
            }

            try {
                // 检查知识库是否已存在
                String vectorStoreName = getVectorStoreName();
                Result<List<Map<String, Object>>> listResult = ragFeignClient.getKnowledgeBaseList();

                boolean exists = false;
                if (listResult != null && listResult.getCode() == 200 && listResult.getData() != null) {
                    exists = listResult.getData().stream()
                            .anyMatch(kb -> vectorStoreName.equals(kb.get("name")));
                }

                if (!exists) {
                    // 创建知识库
                    Map<String, String> request = new HashMap<>();
                    request.put("name", vectorStoreName);
                    request.put("description", "提示词模板向量库（存储所有启用的提示词模板）");

                    Result<Map<String, Object>> result = ragFeignClient.createCollection(request);
                    if (result != null && result.getCode() == 200) {
                        log.info("✅ 创建全局提示词向量库成功: {}", vectorStoreName);
                    } else {
                        log.warn("创建向量库失败: {}", result != null ? result.getMsg() : "未知错误");
                    }
                } else {
                    log.info("✅ 全局提示词向量库已存在: {}", vectorStoreName);
                }

                isInitialized.set(true);
            } catch (Exception e) {
                log.error("初始化提示词向量库失败", e);
            }
        }
    }
}