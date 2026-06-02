package com.washy.dify.prompt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.washy.dify.prompt.entity.PromptTemplateEntity;
import com.washy.dify.prompt.entity.PromptTemplateVO;
import com.washy.dify.prompt.entity.PromptVersionHistoryEntity;
import com.washy.dify.prompt.mapper.PromptTemplateMapper;
import com.washy.dify.prompt.mapper.PromptVersionHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class PromptTemplateService {
    
    @Autowired
    private PromptTemplateMapper promptTemplateMapper;
    
    @Autowired
    private PromptVersionHistoryMapper versionHistoryMapper;
    
    /**
     * 保存模板
     */
    @Transactional
    public PromptTemplateEntity saveTemplate(PromptTemplateVO vo) {
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(vo.getName());
        entity.setVersion(vo.getVersion());
        entity.setDescription(vo.getDescription());
        entity.setTemplateContent(vo.getTemplate());
        entity.setStatus(vo.getStatus() != null ? vo.getStatus() : "DRAFT");
        entity.setType(vo.getType() != null ? vo.getType() : "CUSTOM");
        
        if (vo.getModelParams() != null) {
            entity.setTemperature(BigDecimal.valueOf(vo.getModelParams().getTemperature()));
            entity.setMaxTokens(vo.getModelParams().getMaxTokens());
            entity.setTopP(BigDecimal.valueOf(vo.getModelParams().getTopP()));
            entity.setRepeatPenalty(BigDecimal.valueOf(vo.getModelParams().getRepeatPenalty()));
        }
        
        entity.setStreaming(vo.getStreaming());
        entity.setCreatedAt(new Date());
        entity.setUpdatedAt(new Date());
        entity.setUseCount(0);
        
        promptTemplateMapper.insert(entity);
        log.info("保存模板成功: {}", entity.getName());
        
        return entity;
    }
    
    /**
     * 更新模板
     */
    @Transactional
    public PromptTemplateEntity updateTemplate(String id, PromptTemplateVO vo) {
        // 保存当前版本到历史
        PromptTemplateEntity oldEntity = promptTemplateMapper.selectById(id);
        if (oldEntity != null) {
            saveToHistory(oldEntity, "更新模板");
        }
        
        // 更新模板
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(id);
        entity.setName(vo.getName());
        entity.setVersion(vo.getVersion());
        entity.setDescription(vo.getDescription());
        entity.setTemplateContent(vo.getTemplate());
        entity.setStatus(vo.getStatus());
        entity.setType(vo.getType());
        entity.setUpdatedAt(new Date());
        
        if (vo.getModelParams() != null) {
            entity.setTemperature(BigDecimal.valueOf(vo.getModelParams().getTemperature()));
            entity.setMaxTokens(vo.getModelParams().getMaxTokens());
            entity.setTopP(BigDecimal.valueOf(vo.getModelParams().getTopP()));
            entity.setRepeatPenalty(BigDecimal.valueOf(vo.getModelParams().getRepeatPenalty()));
        }
        
        promptTemplateMapper.updateById(entity);
        log.info("更新模板成功: {}", id);
        
        return promptTemplateMapper.selectById(id);
    }
    
    /**
     * 保存到历史
     */
    private void saveToHistory(PromptTemplateEntity entity, String changeLog) {
        PromptVersionHistoryEntity history = new PromptVersionHistoryEntity();
        history.setId(UUID.randomUUID().toString());
        history.setTemplateId(entity.getId());
        history.setVersion(entity.getVersion());
        history.setTemplateContent(entity.getTemplateContent());
        history.setChangeLog(changeLog);
        history.setCreatedAt(new Date());
        versionHistoryMapper.insert(history);
    }
    
    /**
     * 删除模板
     */
    @Transactional
    public void deleteTemplate(String id) {
        promptTemplateMapper.deleteById(id);
        log.info("删除模板成功: {}", id);
    }
    
    /**
     * 获取模板
     */
    public PromptTemplateEntity getTemplate(String id) {
        return promptTemplateMapper.selectById(id);
    }
    
    /**
     * 根据名称获取模板
     */
    public PromptTemplateEntity getTemplateByName(String name) {
        LambdaQueryWrapper<PromptTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplateEntity::getName, name);
        return promptTemplateMapper.selectOne(wrapper);
    }
    
    /**
     * 获取所有模板
     */
    public List<PromptTemplateEntity> listAllTemplates() {
        LambdaQueryWrapper<PromptTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PromptTemplateEntity::getUpdatedAt);
        return promptTemplateMapper.selectList(wrapper);
    }
    
    /**
     * 分页查询模板
     */
    public Page<PromptTemplateEntity> pageTemplates(int pageNum, int pageSize, String keyword, String status, String type) {
        Page<PromptTemplateEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PromptTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(PromptTemplateEntity::getName, keyword)
                   .or()
                   .like(PromptTemplateEntity::getDescription, keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PromptTemplateEntity::getStatus, status);
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(PromptTemplateEntity::getType, type);
        }
        
        wrapper.orderByDesc(PromptTemplateEntity::getUpdatedAt);
        
        return promptTemplateMapper.selectPage(page, wrapper);
    }
    
    /**
     * 启用/禁用模板
     */
    public Boolean setStatus(String id, String status) {
        boolean flag = false;

        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setUpdatedAt(new Date());
        try{
            promptTemplateMapper.updateById(entity);
            log.info("模板状态变更: {} -> {}", id, status);
            flag = true;
        }catch (Exception e){
            log.info("模板状态变更: {}", "启用/禁用模板更新失败");
        }

        return flag;

    }
    
    /**
     * 增加使用次数
     */
    public void incrementUseCount(String id) {
        promptTemplateMapper.incrementUseCount(id);
    }
    
    /**
     * 复制模板
     */
    @Transactional
    public PromptTemplateEntity copyTemplate(String id, String newName) {
        PromptTemplateEntity original = promptTemplateMapper.selectById(id);
        if (original == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        
        PromptTemplateEntity copy = new PromptTemplateEntity();
        BeanUtils.copyProperties(original, copy, "id", "createdAt", "updatedAt", "useCount");
        copy.setId(UUID.randomUUID().toString());
        copy.setName(newName != null ? newName : original.getName() + " (副本)");
        copy.setVersion("v1.0.0");
        copy.setStatus("DRAFT");
        copy.setUseCount(0);
        copy.setCreatedAt(new Date());
        copy.setUpdatedAt(new Date());
        
        promptTemplateMapper.insert(copy);
        log.info("复制模板成功: {} -> {}", original.getName(), copy.getName());
        
        return copy;
    }
    
    /**
     * 搜索模板
     */
    public List<PromptTemplateEntity> searchTemplates(String keyword) {
        return promptTemplateMapper.searchTemplates(keyword);
    }
    
    /**
     * 根据类型获取活跃模板
     */
    public List<PromptTemplateEntity> getActiveTemplatesByType(String type) {
        return promptTemplateMapper.getActiveTemplatesByType(type);
    }
    
    /**
     * 实体转VO
     */
    public PromptTemplateVO toVO(PromptTemplateEntity entity) {
        if (entity == null) {
            return null;
        }
        
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setVersion(entity.getVersion());
        vo.setDescription(entity.getDescription());
        vo.setTemplate(entity.getTemplateContent());
        vo.setStatus(entity.getStatus());
        vo.setType(entity.getType());
        vo.setStreaming(entity.getStreaming());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        
        PromptTemplateVO.ModelParamsDTO params = new PromptTemplateVO.ModelParamsDTO();
        if (entity.getTemperature() != null) {
            params.setTemperature(entity.getTemperature().floatValue());
        }
        params.setMaxTokens(entity.getMaxTokens() != null ? entity.getMaxTokens() : 2048);
        if (entity.getTopP() != null) {
            params.setTopP(entity.getTopP().floatValue());
        }
        if (entity.getRepeatPenalty() != null) {
            params.setRepeatPenalty(entity.getRepeatPenalty().floatValue());
        }
        vo.setModelParams(params);
        
        return vo;
    }
}