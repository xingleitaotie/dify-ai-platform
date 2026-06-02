// dify-llm-service/src/main/java/com/washy/dify/llm/controller/LlmConfigController.java
package com.washy.dify.llm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.common.result.Result;
import com.washy.dify.llm.config.DynamicLlmProperties;
import com.washy.dify.llm.domain.entity.LlmConfigEntity;
import com.washy.dify.llm.factory.LlmClientFactory;
import com.washy.dify.llm.service.LlmClient;
import com.washy.dify.llm.service.LlmConfigService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;

/**
 * 大模型配置管理接口
 */
@RestController
@RequestMapping("/api/llm/config")
@Api(tags = "大模型配置管理")
@Slf4j
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmConfigService llmConfigService;
    private final DynamicLlmProperties dynamicLlmProperties;
    private final LlmClientFactory llmClientFactory;

    // ==================== 兼容原有接口 ====================

    /**
     * 获取当前配置（兼容原有前端）
     */
    @GetMapping
    @ApiOperation("获取当前配置")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", dynamicLlmProperties.getType());
        config.put("modelName", dynamicLlmProperties.getModelName());
        config.put("baseUrl", dynamicLlmProperties.getBaseUrl());
        config.put("apiKey", maskApiKey(dynamicLlmProperties.getApiKey()));
        config.put("maxTokens", dynamicLlmProperties.getMaxTokens());
        config.put("temperature", dynamicLlmProperties.getTemperature());
        config.put("timeout", dynamicLlmProperties.getTimeout());
        return Result.success(config);
    }

    /**
     * 更新配置（兼容原有前端）
     */
    @PostMapping
    @ApiOperation("更新配置")
    public Result<Void> updateConfig(@RequestBody Map<String, Object> config) {
        log.info("收到配置更新请求: {}", config);

        // 更新动态配置
        if (config.containsKey("type")) {
            dynamicLlmProperties.setType((String) config.get("type"));
        }
        if (config.containsKey("modelName")) {
            dynamicLlmProperties.setModelName((String) config.get("modelName"));
        }
        if (config.containsKey("baseUrl")) {
            dynamicLlmProperties.setBaseUrl((String) config.get("baseUrl"));
        }
        if (config.containsKey("apiKey")) {
            String apiKey = (String) config.get("apiKey");
            if (apiKey != null && !apiKey.startsWith("***")) {
                dynamicLlmProperties.setApiKey(apiKey);
            }
        }
        if (config.containsKey("maxTokens")) {
            dynamicLlmProperties.setMaxTokens(((Number) config.get("maxTokens")).intValue());
        }
        if (config.containsKey("temperature")) {
            dynamicLlmProperties.setTemperature(((Number) config.get("temperature")).doubleValue());
        }
        if (config.containsKey("timeout")) {
            dynamicLlmProperties.setTimeout(((Number) config.get("timeout")).intValue());
        }

        log.info("配置更新完成: type={}, modelName={}",
                dynamicLlmProperties.getType(), dynamicLlmProperties.getModelName());

        return Result.success();
    }

    /**
     * 测试连接（兼容原有前端）
     */
    @PostMapping("/test")
    @ApiOperation("测试连接")
    public Result<String> testConnection(@RequestBody LlmConfigEntity config) {
        log.info("测试LLM连接");
        try {
            // 使用当前配置测试连接
            boolean success = llmConfigService.testConfig(config);
            if (success) {
                return Result.success("连接正常");
            } else {
                return Result.error("连接失败，请检查配置");
            }
        } catch (Exception e) {
            log.error("连接测试失败", e);
            return Result.error("连接失败：" + e.getMessage());
        }
    }

    // ==================== 新增的配置管理接口（可选） ====================

    /**
     * 分页查询配置列表
     */
    @GetMapping("/page")
    @ApiOperation("分页查询配置列表")
    public Result<Page<LlmConfigEntity>> pageConfig(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status) {
        Page<LlmConfigEntity> page = llmConfigService.pageConfig(pageNum, pageSize, type, status);
        return Result.success(page);
    }

    /**
     * 获取所有启用的配置
     */
    @GetMapping("/enabled")
    @ApiOperation("获取所有启用的配置")
    public Result<List<LlmConfigEntity>> getEnabledConfigs() {
        return Result.success(llmConfigService.getEnabledConfigs());
    }

    /**
     * 根据ID获取配置
     */
    @GetMapping("/detail/{id}")
    @ApiOperation("根据ID获取配置")
    public Result<LlmConfigEntity> getConfigById(@PathVariable Long id) {
        LlmConfigEntity config = llmConfigService.getConfigById(id);
        if (config != null && config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            config.setApiKey(maskApiKey(config.getApiKey()));
        }
        return Result.success(config);
    }

    /**
     * 获取当前使用的配置详情
     */
    @GetMapping("/current/detail")
    @ApiOperation("获取当前使用的配置详情")
    public Result<LlmConfigEntity> getCurrentConfigDetail() {
        LlmConfigEntity config = llmConfigService.getDefaultConfig();
        if (config != null && config.getApiKey() != null) {
            config.setApiKey(maskApiKey(config.getApiKey()));
        }
        return Result.success(config);
    }

    /**
     * 新增配置
     */
    @PostMapping("/add")
    @ApiOperation("新增配置")
    public Result<Boolean> addConfig(@Valid @RequestBody LlmConfigEntity config) {
        if (!llmConfigService.testConfig(config)) {
            return Result.error("配置测试失败，请检查参数是否正确");
        }
        boolean success = llmConfigService.addConfig(config);
        return success ? Result.success(true) : Result.error("新增失败");
    }

    /**
     * 更新配置
     */
    @PutMapping("/update")
    @ApiOperation("更新配置")
    public Result<Boolean> updateConfig(@Valid @RequestBody LlmConfigEntity config) {
        boolean success = llmConfigService.updateConfig(config);
        if (success && config.getId().equals(dynamicLlmProperties.getCurrentConfigId())) {
            llmClientFactory.clearCache(config.getId());
        }
        return success ? Result.success(true) : Result.error("更新失败");
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/delete/{id}")
    @ApiOperation("删除配置")
    public Result<Boolean> deleteConfig(@PathVariable Long id) {
        boolean success = llmConfigService.deleteConfig(id);
        if (success) {
            llmClientFactory.clearCache(id);
        }
        return success ? Result.success(true) : Result.error("删除失败");
    }

    /**
     * 切换配置（热加载）
     */
    @PostMapping("/switch/{id}")
    @ApiOperation("切换配置")
    public Result<Boolean> switchConfig(@PathVariable Long id) {
        boolean success = llmConfigService.switchConfig(id);
        if (success) {
            Long oldConfigId = dynamicLlmProperties.getCurrentConfigId();
            if (oldConfigId != null) {
                llmClientFactory.clearCache(oldConfigId);
            }
            log.info("配置切换成功: {} -> {}", oldConfigId, id);
        }
        return success ? Result.success(true) : Result.error("切换失败");
    }

    /**
     * 测试指定配置连接
     */
    @PostMapping("/test-config")
    @ApiOperation("测试指定配置连接")
    public Result<Boolean> testConfig(@RequestBody LlmConfigEntity config) {
        boolean success = llmConfigService.testConfig(config);
        return success ? Result.success(true) : Result.error("连接测试失败");
    }

    /**
     * 获取支持的模型类型
     */
    @GetMapping("/types")
    @ApiOperation("获取支持的模型类型")
    public Result<List<Map<String, String>>> getSupportedTypes() {
        List<Map<String, String>> types = Arrays.asList(
                createTypeMap("ollama", "Ollama"),
                createTypeMap("openai", "OpenAI"),
                createTypeMap("modelScope", "ModelScope"),
                createTypeMap("qwen", "通义千问"),
                createTypeMap("ernie", "文心一言"),
                createTypeMap("spark", "讯飞星火"),
                createTypeMap("zhipu", "智谱AI")
        );
        return Result.success(types);
    }

    /**
     * 测试指定模型类型的连接
     */
    @PostMapping("/test-model")
    @ApiOperation("测试指定模型类型的连接")
    public Result<Boolean> testModelType(@RequestBody Map<String, String> params) {
        String modelType = params.get("modelType");
        if (modelType == null || modelType.isEmpty()) {
            return Result.error("模型类型不能为空");
        }

        try {
            // 获取对应类型的客户端并测试
            LlmClient client = llmClientFactory.getLlmClientByType(modelType);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是一个专业测试大模型是否连接成功的助手，请根据客户要求，直接输出"));
            messages.add(ChatMessage.user("你好，请回复'连接成功'"));
            String result = client.chat(messages);
            boolean success = result != null && !result.isEmpty();
            return success ? Result.success(true) : Result.error("连接失败");
        } catch (Exception e) {
            log.error("测试模型连接失败: {}", modelType, e);
            return Result.error("连接失败：" + e.getMessage());
        }
    }

    private Map<String, String> createTypeMap(String value, String label) {
        Map<String, String> map = new HashMap<>();
        map.put("value", value);
        map.put("label", label);
        return map;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "***";
        }
        return "***" + apiKey.substring(apiKey.length() - 4);
    }
}