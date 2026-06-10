package com.washy.dify.provider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.washy.dify.provider.dto.SystemCapabilitiesDTO;
import com.washy.dify.provider.dto.SystemCapabilityDTO;
import com.washy.dify.provider.entity.ModelConfigEntity;
import com.washy.dify.provider.entity.ProviderEntity;
import com.washy.dify.provider.entity.SystemCapabilityEntity;
import com.washy.dify.provider.mapper.ModelConfigMapper;
import com.washy.dify.provider.mapper.ProviderMapper;
import com.washy.dify.provider.mapper.SystemCapabilityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemConfigService {
    
    private final SystemCapabilityMapper systemCapabilityMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    
    // 缓存系统能力配置
    private final Map<String, ModelConfigEntity> capabilityCache = new ConcurrentHashMap<>();
    
    /**
     * 获取系统能力配置
     */
    public ModelConfigEntity getCapabilityConfig(String capabilityType) {
        // 先从缓存获取
        if (capabilityCache.containsKey(capabilityType)) {
            return capabilityCache.get(capabilityType);
        }
        
        // 从数据库加载
        LambdaQueryWrapper<SystemCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemCapabilityEntity::getCapabilityType, capabilityType);
        SystemCapabilityEntity systemConfig = systemCapabilityMapper.selectOne(wrapper);
        
        if (systemConfig == null || systemConfig.getModelConfigId() == null) {
            return null;
        }
        
        ModelConfigEntity modelConfig = modelConfigMapper.selectById(systemConfig.getModelConfigId());
        if (modelConfig != null) {
            // 加载供应商信息
            ProviderEntity provider = providerMapper.selectById(modelConfig.getProviderId());
            modelConfig.setProvider(provider);
            capabilityCache.put(capabilityType, modelConfig);
        }
        
        return modelConfig;
    }
    
    /**
     * 获取指定能力类型的可用模型列表
     */
    public List<ModelConfigEntity> getAvailableModels(String capabilityType) {
        return modelConfigMapper.selectByCapabilityType(capabilityType);
    }
    
    /**
     * 获取所有系统能力配置
     */
    public SystemCapabilitiesDTO getAllCapabilities() {
        SystemCapabilitiesDTO dto = new SystemCapabilitiesDTO();
        
        String[] types = {"chat", "embedding", "rerank", "stt", "tts", "vision"};
        for (String type : types) {
            SystemCapabilityDTO capability = getCapabilityDTO(type);
            switch (type) {
                case "chat": dto.setChat(capability); break;
                case "embedding": dto.setEmbedding(capability); break;
                case "rerank": dto.setRerank(capability); break;
                case "stt": dto.setStt(capability); break;
                case "tts": dto.setTts(capability); break;
                case "vision": dto.setVision(capability); break;
            }
        }
        return dto;
    }
    
    private SystemCapabilityDTO getCapabilityDTO(String capabilityType) {
        LambdaQueryWrapper<SystemCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemCapabilityEntity::getCapabilityType, capabilityType);
        SystemCapabilityEntity entity = systemCapabilityMapper.selectOne(wrapper);
        
        SystemCapabilityDTO dto = new SystemCapabilityDTO();
        dto.setCapabilityType(capabilityType);
        dto.setCapabilityName(getCapabilityName(capabilityType));
        
        if (entity != null) {
            dto.setModelConfigId(entity.getModelConfigId());
            dto.setFallbackConfigId(entity.getFallbackConfigId());
            
            if (entity.getModelConfigId() != null) {
                ModelConfigEntity modelConfig = modelConfigMapper.selectById(entity.getModelConfigId());
                if (modelConfig != null) {
                    dto.setModelName(modelConfig.getModelName());
                    ProviderEntity provider = providerMapper.selectById(modelConfig.getProviderId());
                    if (provider != null) {
                        dto.setProviderName(provider.getProviderName());
                    }
                }
            }
        }
        return dto;
    }
    
    /**
     * 更新系统能力配置
     */
    @Transactional
    public boolean updateCapability(String capabilityType, Long modelConfigId) {
        LambdaQueryWrapper<SystemCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemCapabilityEntity::getCapabilityType, capabilityType);
        
        SystemCapabilityEntity entity = systemCapabilityMapper.selectOne(wrapper);
        if (entity == null) {
            entity = new SystemCapabilityEntity();
            entity.setCapabilityType(capabilityType);
            entity.setModelConfigId(modelConfigId);
            systemCapabilityMapper.insert(entity);
        } else {
            entity.setModelConfigId(modelConfigId);
            systemCapabilityMapper.updateById(entity);
        }
        
        // 清除缓存
        capabilityCache.remove(capabilityType);
        
        log.info("更新系统能力配置: {} -> modelConfigId: {}", capabilityType, modelConfigId);
        return true;
    }
    
    /**
     * 批量更新系统能力配置
     */
    @Transactional
    public boolean updateCapabilities(Map<String, Long> configMap) {
        for (Map.Entry<String, Long> entry : configMap.entrySet()) {
            updateCapability(entry.getKey(), entry.getValue());
        }
        return true;
    }
    
    /**
     * 刷新缓存
     */
    public void refreshCache() {
        capabilityCache.clear();
    }
    
    private String getCapabilityName(String type) {
        Map<String, String> names = new HashMap<>();
        names.put("chat", "大语言模型");
        names.put("embedding", "向量嵌入模型");
        names.put("rerank", "重排序模型");
        names.put("stt", "语音转文本");
        names.put("tts", "文本转语音");
        names.put("vision", "图片识别模型");
        return names.getOrDefault(type, type);
    }

    /**
     * 获取能力类型列表
     */
    public List<Map<String, String>> getCapabilityTypes() {
        List<Map<String, String>> list = new ArrayList<>();

        Map<String, String> chat = new HashMap<>();
        chat.put("value", "chat");
        chat.put("label", "大语言模型");
        chat.put("description", "文本对话、推理、代码生成等");
        list.add(chat);

        Map<String, String> embedding = new HashMap<>();
        embedding.put("value", "embedding");
        embedding.put("label", "向量嵌入模型");
        embedding.put("description", "文本向量化，用于知识库检索");
        list.add(embedding);

        Map<String, String> rerank = new HashMap<>();
        rerank.put("value", "rerank");
        rerank.put("label", "重排序模型");
        rerank.put("description", "对检索结果重新排序，提升相关性");
        list.add(rerank);

        Map<String, String> stt = new HashMap<>();
        stt.put("value", "stt");
        stt.put("label", "语音转文本");
        stt.put("description", "将语音转换为文字");
        list.add(stt);

        Map<String, String> tts = new HashMap<>();
        tts.put("value", "tts");
        tts.put("label", "文本转语音");
        tts.put("description", "将文字转换为语音");
        list.add(tts);

        Map<String, String> vision = new HashMap<>();
        vision.put("value", "vision");
        vision.put("label", "图片识别模型");
        vision.put("description", "图像理解、视觉问答");
        list.add(vision);

        return list;
    }
}