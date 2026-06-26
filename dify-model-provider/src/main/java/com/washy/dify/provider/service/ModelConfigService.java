package com.washy.dify.provider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.provider.client.chat.ChatClient;
import com.washy.dify.provider.client.embedding.EmbeddingClient;
import com.washy.dify.provider.client.rerank.RerankClient;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.entity.SystemCapabilityEntity;
import com.washy.dify.provider.exception.ModelProviderException;
import com.washy.dify.provider.factory.UnifiedClientFactory;
import com.washy.dify.provider.mapper.ModelConfigMapper;
import com.washy.dify.provider.mapper.ProviderMapper;
import com.washy.dify.provider.mapper.SystemCapabilityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final UnifiedClientFactory clientFactory;

    private final SystemCapabilityMapper systemCapabilityMapper;

    /**
     * 获取供应商下的所有模型
     */
    public List<ModelConfigEntity> listByProvider(Long providerId) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getProviderId, providerId)
               .orderByAsc(ModelConfigEntity::getCapabilityType)
               .orderByAsc(ModelConfigEntity::getSortOrder);
        
        List<ModelConfigEntity> models = modelConfigMapper.selectList(wrapper);
        
        fillProviderInfo(models);
        
        return models;
    }

    /**
     * 获取指定能力的可用模型（只返回启用的）
     */
    public List<ModelConfigEntity> listByCapability(String capabilityType) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getCapabilityType, capabilityType)
               .eq(ModelConfigEntity::getStatus, 1)
               .orderByAsc(ModelConfigEntity::getSortOrder);
        
        List<ModelConfigEntity> models = modelConfigMapper.selectList(wrapper);
        
        fillProviderInfo(models);
        
        return models;
    }

    /**
     * 批量填充供应商信息（优化N+1查询）
     */
    private void fillProviderInfo(List<ModelConfigEntity> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        
        Set<Long> providerIds = models.stream()
                .map(ModelConfigEntity::getProviderId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        
        if (providerIds.isEmpty()) {
            return;
        }
        
        LambdaQueryWrapper<ProviderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ProviderEntity::getId, providerIds);
        List<ProviderEntity> providers = providerMapper.selectList(wrapper);
        
        Map<Long, ProviderEntity> providerMap = providers.stream()
                .collect(java.util.stream.Collectors.toMap(ProviderEntity::getId, p -> p));
        
        for (ModelConfigEntity model : models) {
            model.setProvider(providerMap.get(model.getProviderId()));
        }
    }

    /**
     * 根据ID获取模型配置
     */
    public ModelConfigEntity getById(Long id) {
        ModelConfigEntity model = modelConfigMapper.selectById(id);
        if (model != null) {
            ProviderEntity provider = providerMapper.selectById(model.getProviderId());
            model.setProvider(provider);
        }
        return model;
    }

    /**
     * 新增模型配置
     */
    @Transactional
    public boolean add(ModelConfigEntity modelConfig) {
        // 验证供应商是否存在
        ProviderEntity provider = providerMapper.selectById(modelConfig.getProviderId());
        if (provider == null) {
            throw new ModelProviderException("供应商不存在");
        }
        
        // 检查是否已存在相同的配置（同一供应商、同一能力类型）
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getProviderId, modelConfig.getProviderId())
               .eq(ModelConfigEntity::getCapabilityType, modelConfig.getCapabilityType());
        
        ModelConfigEntity existing = modelConfigMapper.selectOne(wrapper);
        if (existing != null) {
            throw new ModelProviderException("该供应商下已存在相同能力的模型配置");
        }
        
        modelConfig.setStatus(modelConfig.getStatus() != null ? modelConfig.getStatus() : 1);
        return modelConfigMapper.insert(modelConfig) > 0;
    }

    /**
     * 更新模型配置
     */
    @Transactional
    public boolean update(ModelConfigEntity modelConfig) {
        if (modelConfig.getId() == null) {
            throw new ModelProviderException("模型配置ID不能为空");
        }
        
        return modelConfigMapper.updateById(modelConfig) > 0;
    }

    /**
     * 删除模型配置
     */
    @Transactional
    public boolean delete(Long id) {
        ModelConfigEntity modelConfig = modelConfigMapper.selectById(id);
        if (modelConfig == null) {
            throw new ModelProviderException("模型配置不存在");
        }
        
        // TODO: 检查是否被系统能力使用，如果被使用则不能删除
        // 检查是否被系统能力使用
        LambdaQueryWrapper<SystemCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemCapabilityEntity::getModelConfigId, id);
        SystemCapabilityEntity capability = systemCapabilityMapper.selectOne(wrapper);

        if (capability != null) {
            throw new ModelProviderException("该模型正在被系统使用（" + capability.getCapabilityType() + "），请先切换系统配置");
        }

        return modelConfigMapper.deleteById(id) > 0;
        
    }

    /**
     * 测试模型连接
     * @param params 测试参数，包含：
     *   - providerKey: 供应商标识（openai, ollama, modelscope等）
     *   - modelKey: 模型标识（必填，如 gpt-3.5-turbo, Qwen/Qwen3-8B-GGUF）
     *   - capabilityType: 能力类型（chat, embedding, rerank等）
     *   - modelSchema: 协议类型（openai, ollama, dashscope等）
     *   - baseUrl: API地址
     *   - apiKey: API密钥
     *   - secret: Secret密钥（可选）
     */
    public boolean test(Map<String, String> params) {
        String providerKey = params.get("providerKey");
        String modelKey = params.get("modelKey");
        String capabilityType = params.getOrDefault("capabilityType", "chat");
        String modelSchema = params.get("modelSchema");
        String baseUrl = params.get("baseUrl");
        String apiKey = params.get("apiKey");
        String secret = params.get("secret");

        // 参数校验
        if (providerKey == null || providerKey.isEmpty()) {
            log.error("模型测试失败: providerKey不能为空");
            return false;
        }
        if (modelKey == null || modelKey.isEmpty()) {
            log.error("模型测试失败: modelKey不能为空");
            return false;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            log.error("模型测试失败: baseUrl不能为空");
            return false;
        }
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("模型测试: apiKey为空，某些服务可能无法连接");
        }

        try {
            // 构建临时供应商配置
            ProviderEntity tempProvider = new ProviderEntity();
            tempProvider.setProviderKey(providerKey);
            tempProvider.setBaseUrl(baseUrl);
            tempProvider.setApiKey(apiKey);
            tempProvider.setSecret(secret);

            // 构建临时模型配置 - 使用传入的 modelKey
            ModelConfigEntity tempModel = new ModelConfigEntity();
            tempModel.setModelKey(modelKey);  // 直接使用传入的模型标识，不用默认值
            tempModel.setCapabilityType(capabilityType);
            tempModel.setModelSchema(modelSchema != null && !modelSchema.isEmpty()
                    ? modelSchema : getDefaultSchema(providerKey));

            // 根据能力类型测试
            switch (capabilityType) {
                case "chat":
                    return testChat(tempProvider, tempModel);
                case "embedding":
                    return testEmbedding(tempProvider, tempModel);
                case "rerank":
                    return testRerank(tempProvider, tempModel);
                default:
                    log.warn("不支持的能力类型: {}", capabilityType);
                    return false;
            }

        } catch (Exception e) {
            log.error("模型测试失败: providerKey={}, modelKey={}, capabilityType={}",
                    providerKey, modelKey, capabilityType);
            return false;
        }
    }

    /**
     * 测试Chat模型
     */
    private boolean testChat(ProviderEntity provider, ModelConfigEntity modelConfig) {
        ChatClient client = clientFactory.createChatClient(provider, modelConfig);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("你好，请回复'连接成功'"));
        String result = client.chat(messages);
        return result != null && !result.isEmpty();
    }

    /**
     * 测试Embedding模型
     */
    private boolean testEmbedding(ProviderEntity provider, ModelConfigEntity modelConfig) {
        EmbeddingClient client = clientFactory.createEmbeddingClient(provider, modelConfig);
        float[] embedding = client.embed("测试文本");
        return embedding != null && embedding.length > 0;
    }

    /**
     * 测试Rerank模型
     */
    private boolean testRerank(ProviderEntity provider, ModelConfigEntity modelConfig) {
        RerankClient client = clientFactory.createRerankClient(provider, modelConfig);
        List<String> documents = Arrays.asList("测试文档1", "测试文档2");
        List<RerankClient.RerankResult> results = client.rerank("测试查询", documents, 2);
        return results != null && !results.isEmpty();
    }

    /**
     * 获取默认协议（仅当未指定时使用）
     */
    private String getDefaultSchema(String providerKey) {
        switch (providerKey) {
            case "openai": return "openai";
            case "ollama": return "ollama";
            case "modelscope": return "modelscope";
            case "aliyun": return "dashscope";
            case "baidu": return "ernie";
            case "xfyun": return "spark";
            case "zhipu": return "openai";
            default: return "openai";
        }
    }
}