package com.washy.dify.provider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.washy.dify.provider.dto.SystemCapabilitiesDTO;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.mapper.ModelConfigMapper;
import com.washy.dify.provider.mapper.ProviderMapper;
import com.washy.dify.provider.vo.ModelConfigVO;
import com.washy.dify.provider.vo.ProviderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderService {
    
    private final ProviderMapper providerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final SystemConfigService systemConfigService;
    
    /**
     * 分页查询供应商
     */
    public Page<ProviderEntity> pageProvider(int pageNum, int pageSize, String keyword) {
        Page<ProviderEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ProviderEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(ProviderEntity::getProviderName, keyword)
                   .or()
                   .like(ProviderEntity::getProviderKey, keyword);
        }
        wrapper.orderByAsc(ProviderEntity::getSortOrder);
        return providerMapper.selectPage(page, wrapper);
    }
    
    /**
     * 获取所有启用的供应商
     */
    public List<ProviderEntity> getEnabledProviders() {
        LambdaQueryWrapper<ProviderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProviderEntity::getStatus, 1)
               .orderByAsc(ProviderEntity::getSortOrder);
        return providerMapper.selectList(wrapper);
    }
    
    /**
     * 获取供应商详情(包含模型列表)
     */
    public ProviderVO getProviderDetail(Long providerId) {
        ProviderEntity provider = providerMapper.selectById(providerId);
        if (provider == null) return null;
        
        ProviderVO vo = new ProviderVO();
        vo.setId(provider.getId());
        vo.setProviderKey(provider.getProviderKey());
        vo.setProviderName(provider.getProviderName());
        vo.setBaseUrl(provider.getBaseUrl());
        vo.setApiKey(maskApiKey(provider.getApiKey()));
        vo.setStatus(provider.getStatus());
        
        // 获取该供应商下的所有模型
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getProviderId, providerId)
               .orderByAsc(ModelConfigEntity::getCapabilityType)
               .orderByAsc(ModelConfigEntity::getSortOrder);
        List<ModelConfigEntity> models = modelConfigMapper.selectList(wrapper);
        
        // 获取系统能力配置，判断哪些模型正在被使用
        SystemCapabilitiesDTO systemCapabilities = systemConfigService.getAllCapabilities();
        java.util.Map<Long, String> usedModelMap = new java.util.HashMap<>();
        if (systemCapabilities.getChat() != null && systemCapabilities.getChat().getModelConfigId() != null) {
            usedModelMap.put(systemCapabilities.getChat().getModelConfigId(), "chat");
        }
        if (systemCapabilities.getEmbedding() != null && systemCapabilities.getEmbedding().getModelConfigId() != null) {
            usedModelMap.put(systemCapabilities.getEmbedding().getModelConfigId(), "embedding");
        }
        if (systemCapabilities.getRerank() != null && systemCapabilities.getRerank().getModelConfigId() != null) {
            usedModelMap.put(systemCapabilities.getRerank().getModelConfigId(), "rerank");
        }
        
        List<ModelConfigVO> modelVOs = models.stream().map(m -> {
            ModelConfigVO mvo = new ModelConfigVO();
            mvo.setId(m.getId());
            mvo.setProviderId(m.getProviderId());
            mvo.setModelKey(m.getModelKey());
            mvo.setModelName(m.getModelName());
            mvo.setCapabilityType(m.getCapabilityType());
            mvo.setModelSchema(m.getModelSchema());
            mvo.setContextLength(m.getContextLength());
            mvo.setDimension(m.getDimension());
            mvo.setStatus(m.getStatus());
            if (usedModelMap.containsKey(m.getId())) {
                mvo.setIsUsed(true);
                mvo.setUsedBy(usedModelMap.get(m.getId()));
            }
            return mvo;
        }).collect(Collectors.toList());
        
        vo.setModels(modelVOs);
        return vo;
    }
    
    /**
     * 新增供应商
     */
    @Transactional
    public boolean addProvider(ProviderEntity provider) {
        return providerMapper.insert(provider) > 0;
    }
    
    /**
     * 更新供应商
     */
    @Transactional
    public boolean updateProvider(ProviderEntity provider) {
        return providerMapper.updateById(provider) > 0;
    }
    
    /**
     * 删除供应商
     */
    @Transactional
    public boolean deleteProvider(Long id) {
        // 检查是否有模型关联
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getProviderId, id);
        Long count = modelConfigMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("请先删除该供应商下的所有模型配置");
        }
        return providerMapper.deleteById(id) > 0;
    }
    
    /**
     * 添加模型配置
     */
    @Transactional
    public boolean addModelConfig(ModelConfigEntity modelConfig) {
        return modelConfigMapper.insert(modelConfig) > 0;
    }
    
    /**
     * 更新模型配置
     */
    @Transactional
    public boolean updateModelConfig(ModelConfigEntity modelConfig) {
        return modelConfigMapper.updateById(modelConfig) > 0;
    }
    
    /**
     * 删除模型配置
     */
    @Transactional
    public boolean deleteModelConfig(Long id) {
        // 检查是否被系统使用
        SystemCapabilitiesDTO capabilities = systemConfigService.getAllCapabilities();
        if (capabilities.getChat() != null && id.equals(capabilities.getChat().getModelConfigId())) {
            throw new RuntimeException("该模型正在被系统使用，请先切换系统配置");
        }
        if (capabilities.getEmbedding() != null && id.equals(capabilities.getEmbedding().getModelConfigId())) {
            throw new RuntimeException("该模型正在被系统使用，请先切换系统配置");
        }
        if (capabilities.getRerank() != null && id.equals(capabilities.getRerank().getModelConfigId())) {
            throw new RuntimeException("该模型正在被系统使用，请先切换系统配置");
        }
        return modelConfigMapper.deleteById(id) > 0;
    }
    
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) return "***";
        return "***" + apiKey.substring(apiKey.length() - 4);
    }
}