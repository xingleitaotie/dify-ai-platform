package com.washy.dify.prompt.generator;

import com.washy.dify.prompt.entity.GenerateRequest;
import com.washy.dify.prompt.entity.GenerateResponse;
import org.springframework.stereotype.Service;

@Service
public interface PromptGenerator {

    // 带模型配置的生成方法
    GenerateResponse generateWithModel(GenerateRequest request, Long modelConfigId);
}
