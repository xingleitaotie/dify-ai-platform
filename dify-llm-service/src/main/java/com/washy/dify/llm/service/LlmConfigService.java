// dify-llm-service/src/main/java/com/washy/dify/llm/service/LlmConfigService.java
package com.washy.dify.llm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.washy.dify.common.entity.llm.ChatMessage;
import com.washy.dify.llm.config.DynamicLlmProperties;
import com.washy.dify.llm.domain.entity.LlmConfigEntity;
import com.washy.dify.llm.factory.LlmClientFactory;
import com.washy.dify.llm.mapper.LlmConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LlmConfigService {
    
    private final LlmConfigMapper llmConfigMapper;
    private final DynamicLlmProperties dynamicLlmProperties;
    
    /**
     * 分页查询配置列表
     */
    public Page<LlmConfigEntity> pageConfig(int pageNum, int pageSize, String type, Integer status) {
        Page<LlmConfigEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LlmConfigEntity> wrapper = new LambdaQueryWrapper<>();
        
        if (type != null && !type.isEmpty()) {
            wrapper.eq(LlmConfigEntity::getType, type);
        }
        if (status != null) {
            wrapper.eq(LlmConfigEntity::getStatus, status);
        }
        wrapper.orderByDesc(LlmConfigEntity::getIsDefault)
               .orderByDesc(LlmConfigEntity::getUpdateTime);
        
        return llmConfigMapper.selectPage(page, wrapper);
    }
    
    /**
     * 获取所有启用的配置
     */
    public List<LlmConfigEntity> getEnabledConfigs() {
        LambdaQueryWrapper<LlmConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LlmConfigEntity::getStatus, 1);
        return llmConfigMapper.selectList(wrapper);
    }
    
    /**
     * 根据ID获取配置
     */
    public LlmConfigEntity getConfigById(Long id) {
        return llmConfigMapper.selectById(id);
    }
    
    /**
     * 获取默认配置
     */
    public LlmConfigEntity getDefaultConfig() {
        LlmConfigEntity config = llmConfigMapper.selectDefaultConfig();
        if (config == null) {
            // 如果没有默认配置，获取第一个启用的
            LambdaQueryWrapper<LlmConfigEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(LlmConfigEntity::getStatus, 1)
                   .last("limit 1");
            config = llmConfigMapper.selectOne(wrapper);
        }
        return config;
    }
    

    /**
     * 新增配置
     */
    @Transactional
    public boolean addConfig(LlmConfigEntity config) {
        // 如果是默认配置，清除其他默认标记
        if (config.getIsDefault() == 1) {
            clearDefaultConfig();
        }
        return llmConfigMapper.insert(config) > 0;
    }
    
    /**
     * 更新配置
     */
    @Transactional
    public boolean updateConfig(LlmConfigEntity config) {
        // 如果是默认配置，清除其他默认标记
        if (config.getIsDefault() == 1) {
            clearDefaultConfig();
        }
        
        int result = llmConfigMapper.updateById(config);
        
        // 如果更新的是当前使用的配置，刷新动态配置
        if (dynamicLlmProperties.getCurrentConfigId() != null && 
            dynamicLlmProperties.getCurrentConfigId().equals(config.getId())) {
            dynamicLlmProperties.refreshConfig(config);
        }
        
        return result > 0;
    }
    
    /**
     * 删除配置
     */
    @Transactional
    public boolean deleteConfig(Long id) {
        LlmConfigEntity config = llmConfigMapper.selectById(id);
        if (config == null) {
            return false;
        }
        
        // 如果是当前使用的配置，切换到默认配置
        if (dynamicLlmProperties.getCurrentConfigId() != null && 
            dynamicLlmProperties.getCurrentConfigId().equals(id)) {
            LlmConfigEntity defaultConfig = getDefaultConfig();
            if (defaultConfig != null) {
                dynamicLlmProperties.switchConfig(defaultConfig);
            }
        }
        
        return llmConfigMapper.deleteById(id) > 0;
    }
    
    /**
     * 切换配置（热加载）
     */
    @Transactional
    public boolean switchConfig(Long id) {
        LlmConfigEntity config = getConfigById(id);
        if (config == null || config.getStatus() != 1) {
            return false;
        }
        
        // 更新数据库默认标记
        clearDefaultConfig();
        config.setIsDefault(1);
        llmConfigMapper.updateById(config);
        
        // 刷新动态配置
        dynamicLlmProperties.switchConfig(config);
        
        log.info("切换大模型配置：{} -> {} ({})", config.getConfigName(), config.getType(), config.getModelName());
        return true;
    }
    
    /**
     * 测试配置连接
     */
    public boolean testConfig(LlmConfigEntity config) {
        try {
            // 临时创建客户端测试连接
            LlmClient client = LlmClientFactory.createClient(config);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是一个专业测试大模型是否连接成功的助手，请根据客户要求，直接输出"));
            messages.add(ChatMessage.user("你好，请回复'连接成功'"));
            String result = client.chat(messages);
            return result != null && !result.isEmpty();
        } catch (Exception e) {
            log.error("配置测试失败", e);
            return false;
        }
    }
    
    /**
     * 清除所有默认标记
     */
    private void clearDefaultConfig() {
        LlmConfigEntity updateEntity = new LlmConfigEntity();
        updateEntity.setIsDefault(0);
        LambdaQueryWrapper<LlmConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LlmConfigEntity::getIsDefault, 1);
        llmConfigMapper.update(updateEntity, wrapper);
    }
}