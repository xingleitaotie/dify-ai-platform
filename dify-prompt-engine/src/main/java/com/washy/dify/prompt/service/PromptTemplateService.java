package com.washy.dify.prompt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.washy.dify.common.entity.prompt.PromptTemplateVO;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.RagFeignClient;
import com.washy.dify.prompt.entity.PromptTemplateEntity;
import com.washy.dify.prompt.entity.PromptVersionHistoryEntity;
import com.washy.dify.prompt.mapper.PromptTemplateMapper;
import com.washy.dify.prompt.mapper.PromptVersionHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PromptTemplateService {
    
    @Autowired
    private PromptTemplateMapper promptTemplateMapper;
    
    @Autowired
    private PromptVersionHistoryMapper versionHistoryMapper;

    @Autowired
    private RagFeignClient ragFeignClient;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 保存模板（原有方法修改，增加向量同步）
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
        entity.setCategory(vo.getCategory());
        entity.setTags(vo.getTags());

        // 设置system_prompt和user_prompt_template
        if (vo.getSystemPrompt() != null) {
            entity.setSystemPrompt(vo.getSystemPrompt());
        }
        if (vo.getUserPromptTemplate() != null) {
            entity.setUserPromptTemplate(vo.getUserPromptTemplate());
        }

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

        // ========== 新增：自动创建/获取知识库 ==========
        entity.setVectorStoreName(knowledgeBaseService.getVectorStoreName());
        // ========== 新增结束 ==========

        promptTemplateMapper.insert(entity);
        log.info("保存模板成功: {}", entity.getName());

        // 如果是ACTIVE状态，同步到向量库
        if ("ACTIVE".equals(entity.getStatus())) {
            // 确保全局知识库存在
            knowledgeBaseService.ensureKnowledgeBaseExists();
            syncToVectorStore(entity);
        }

        return entity;
    }

    /**
     * 更新模板（原有方法修改，增加向量同步）
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
        entity.setCategory(vo.getCategory());
        entity.setTags(vo.getTags());
        entity.setUpdatedAt(new Date());

        if (vo.getSystemPrompt() != null) {
            entity.setSystemPrompt(vo.getSystemPrompt());
        }
        if (vo.getUserPromptTemplate() != null) {
            entity.setUserPromptTemplate(vo.getUserPromptTemplate());
        }

        if (vo.getModelParams() != null) {
            entity.setTemperature(BigDecimal.valueOf(vo.getModelParams().getTemperature()));
            entity.setMaxTokens(vo.getModelParams().getMaxTokens());
            entity.setTopP(BigDecimal.valueOf(vo.getModelParams().getTopP()));
            entity.setRepeatPenalty(BigDecimal.valueOf(vo.getModelParams().getRepeatPenalty()));
        }

        // ========== 新增：更新知识库关联 ==========
        entity.setVectorStoreName(knowledgeBaseService.getVectorStoreName());
        // ========== 新增结束 ==========

        promptTemplateMapper.updateById(entity);
        log.info("更新模板成功: {}", id);

        PromptTemplateEntity updatedEntity = promptTemplateMapper.selectById(id);

        // 根据状态同步或删除向量
        // ========== 新增：确保知识库存在 ==========
        if ("ACTIVE".equals(updatedEntity.getStatus())) {
            knowledgeBaseService.ensureKnowledgeBaseExists();
        }
        // ========== 新增结束 ==========
        syncToVectorStore(updatedEntity);

        return updatedEntity;
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
     * 删除模板（原有方法修改，增加向量删除）
     */
    @Transactional
    public void deleteTemplate(String id) {
        // 删除前先从向量库删除
        try {
            ragFeignClient.deletePromptTemplate(id);
            log.info("从向量库删除模板: {}", id);
        } catch (Exception e) {
            log.error("从向量库删除模板失败: {}", id, e);
        }

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
     * 启用/禁用模板（原有方法修改，增加向量同步）
     */
    public Boolean setStatus(String id, String status) {
        PromptTemplateEntity entity = promptTemplateMapper.selectById(id);
        if (entity == null) {
            log.warn("模板不存在: {}", id);
            return false;
        }

        String oldStatus = entity.getStatus();
        entity.setStatus(status);
        entity.setUpdatedAt(new Date());

        try {
            promptTemplateMapper.updateById(entity);
            log.info("模板状态变更: {} -> {} -> {}", id, oldStatus, status);

            // ========== 新增：状态变为ACTIVE时确保知识库存在 ==========
            if ("ACTIVE".equals(status)) {
                knowledgeBaseService.ensureKnowledgeBaseExists();
            }
            // ========== 新增结束 ==========

            syncToVectorStore(entity);
            return true;
        } catch (Exception e) {
            log.error("模板状态变更失败: {}", id, e);
            return false;
        }
    }
    
    /**
     * 增加使用次数
     */
    public void incrementUseCount(String id) {
        promptTemplateMapper.incrementUseCount(id);
    }

    /**
     * 复制模板（复制时状态为DRAFT，不同步到向量库）
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
        copy.setStatus("DRAFT");  // 复制后的模板默认为草稿，不同步到向量库
        copy.setUseCount(0);
        copy.setCreatedAt(new Date());
        copy.setUpdatedAt(new Date());

        promptTemplateMapper.insert(copy);
        log.info("复制模板成功: {} -> {}", original.getName(), copy.getName());

        // 复制后的模板是DRAFT状态，不同步到向量库

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

    /**
     * 同步模板到向量库（仅当状态为ACTIVE时）
     */
    private void syncToVectorStore(PromptTemplateEntity entity) {
        if (entity == null) {
            return;
        }

        try {
            if ("ACTIVE".equals(entity.getStatus())) {
                // 状态为启用：同步到向量库
                Map<String, Object> request = new HashMap<>();
                request.put("templateId", entity.getId());
                request.put("templateName", entity.getName());

                // 构建用于向量化的内容（结合模板内容和系统提示词）
                String content = buildVectorContent(entity);
                request.put("content", content);

                // 构建元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("type", entity.getType() != null ? entity.getType() : "CUSTOM");
                metadata.put("category", entity.getCategory() != null ? entity.getCategory() : "");
                metadata.put("tags", entity.getTags() != null ? entity.getTags() : "");
                metadata.put("status", entity.getStatus());
                metadata.put("useCount", entity.getUseCount() != null ? entity.getUseCount() : 0);
                metadata.put("description", entity.getDescription() != null ? entity.getDescription() : "");
                request.put("metadata", metadata);

                ragFeignClient.storePromptTemplate(request);
                log.info("✅ 同步模板到向量库成功: {} ({})", entity.getName(), entity.getId());

            } else {
                // 状态为非启用（DRAFT/ARCHIVED）：从向量库删除
                ragFeignClient.deletePromptTemplate(entity.getId());
                log.info("🗑️ 从向量库删除模板: {} ({})", entity.getName(), entity.getId());
            }
        } catch (Exception e) {
            log.error("❌ 同步模板到向量库失败: {} ({})", entity.getName(), entity.getId(), e);
            // 同步失败不影响主流程，只记录日志
        }
    }

    /**
     * 构建用于向量化的内容
     */
    private String buildVectorContent(PromptTemplateEntity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("模板名称：").append(entity.getName()).append("\n");
        sb.append("模板类型：").append(entity.getType() != null ? entity.getType() : "CUSTOM").append("\n");
        sb.append("分类：").append(entity.getCategory() != null ? entity.getCategory() : "").append("\n");
        sb.append("标签：").append(entity.getTags() != null ? entity.getTags() : "").append("\n");
        sb.append("描述：").append(entity.getDescription() != null ? entity.getDescription() : "").append("\n");
        sb.append("模板内容：\n").append(entity.getTemplateContent() != null ? entity.getTemplateContent() : "");

        if (entity.getSystemPrompt() != null && !entity.getSystemPrompt().isEmpty()) {
            sb.append("\n系统提示词：\n").append(entity.getSystemPrompt());
        }
        if (entity.getUserPromptTemplate() != null && !entity.getUserPromptTemplate().isEmpty()) {
            sb.append("\n用户提示词模板：\n").append(entity.getUserPromptTemplate());
        }

        return sb.toString();
    }

    /**
     * 批量同步所有ACTIVE模板到向量库（用于初始化）
     */
    public void syncAllActiveTemplatesToVectorStore() {
        log.info("开始批量同步ACTIVE模板到向量库");

        LambdaQueryWrapper<PromptTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplateEntity::getStatus, "ACTIVE");
        List<PromptTemplateEntity> activeTemplates = promptTemplateMapper.selectList(wrapper);

        if (activeTemplates.isEmpty()) {
            log.info("没有ACTIVE状态的模板需要同步");
            return;
        }

        List<Map<String, Object>> templatesToSync = new ArrayList<>();
        for (PromptTemplateEntity entity : activeTemplates) {
            Map<String, Object> request = new HashMap<>();
            request.put("templateId", entity.getId());
            request.put("templateName", entity.getName());
            request.put("content", buildVectorContent(entity));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", entity.getType() != null ? entity.getType() : "CUSTOM");
            metadata.put("category", entity.getCategory() != null ? entity.getCategory() : "");
            metadata.put("tags", entity.getTags() != null ? entity.getTags() : "");
            metadata.put("status", entity.getStatus());
            metadata.put("useCount", entity.getUseCount() != null ? entity.getUseCount() : 0);
            metadata.put("description", entity.getDescription() != null ? entity.getDescription() : "");
            request.put("metadata", metadata);

            templatesToSync.add(request);
        }

        try {
            ragFeignClient.batchStorePromptTemplates(templatesToSync);
            log.info("✅ 批量同步完成，共同步 {} 个模板", templatesToSync.size());
        } catch (Exception e) {
            log.error("❌ 批量同步模板失败", e);
        }
    }

    /**
     * 智能同步：检查数据库和向量库的差异，只同步有变化的模板
     */
    public void smartSyncToVectorStore() {
        log.info("开始智能同步模板到向量库...");

        try {
            // 1. 获取数据库中所有ACTIVE模板
            List<PromptTemplateEntity> dbActiveTemplates = promptTemplateMapper.selectList(
                    new LambdaQueryWrapper<PromptTemplateEntity>()
                            .eq(PromptTemplateEntity::getStatus, "ACTIVE")
            );

            if (dbActiveTemplates.isEmpty()) {
                log.info("数据库中没有ACTIVE状态的模板");
                return;
            }

            // 2. 获取向量库中已有的模板信息
            Set<String> vectorTemplateIds = new HashSet<>();

            try {
                // 注意：这里需要正确处理返回结构
                Result<List<Map<String, Object>>> vectorResult = ragFeignClient.listAllPromptTemplates();
                log.info("向量库返回结果: code={}, data={}", vectorResult.getCode(), vectorResult.getData());

                if (vectorResult != null && vectorResult.getCode() == 200 && vectorResult.getData() != null) {
                    for (Map<String, Object> item : vectorResult.getData()) {
                        // 根据实际返回的数据结构获取templateId
                        String id = null;

                        // 尝试多种可能的字段名
                        if (item.containsKey("templateId")) {
                            id = (String) item.get("templateId");
                        } else if (item.containsKey("id")) {
                            id = (String) item.get("id");
                        } else if (item.containsKey("documentId")) {
                            id = (String) item.get("documentId");
                        }

                        if (id != null) {
                            vectorTemplateIds.add(id);
                            log.debug("向量库中的模板ID: {}", id);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取向量库模板列表失败，将执行全量同步", e);
            }

            log.info("数据库ACTIVE模板数: {}, 向量库模板数: {}", dbActiveTemplates.size(), vectorTemplateIds.size());

            // 3. 判断是否需要同步
            List<String> toAdd = new ArrayList<>();
            List<String> toDelete = new ArrayList<>();

            // 需要新增的模板（数据库有，向量库没有）
            for (PromptTemplateEntity template : dbActiveTemplates) {
                if (!vectorTemplateIds.contains(template.getId())) {
                    toAdd.add(template.getId());
                    log.debug("需要新增的模板: {} ({})", template.getName(), template.getId());
                }
            }

            // 需要删除的模板（向量库有，数据库中不是ACTIVE）
            Set<String> dbActiveIds = dbActiveTemplates.stream()
                    .map(PromptTemplateEntity::getId)
                    .collect(Collectors.toSet());
            for (String vectorId : vectorTemplateIds) {
                if (!dbActiveIds.contains(vectorId)) {
                    toDelete.add(vectorId);
                    log.debug("需要删除的模板ID: {}", vectorId);
                }
            }

            // 4. 执行同步
            if (toAdd.isEmpty() && toDelete.isEmpty()) {
                log.info("✅ 模板向量库已是最新，无需同步 (数据库: {}, 向量库: {})",
                        dbActiveTemplates.size(), vectorTemplateIds.size());
                return;
            }

            // 删除需要删除的模板
            if (!toDelete.isEmpty()) {
                log.info("删除失效模板: {} 个", toDelete.size());
                try {
                    ragFeignClient.batchDeletePromptTemplates(toDelete);
                } catch (Exception e) {
                    log.error("删除失效模板失败", e);
                }
            }

            // 新增需要同步的模板
            if (!toAdd.isEmpty()) {
                log.info("新增模板: {} 个", toAdd.size());
                List<Map<String, Object>> templatesToSync = new ArrayList<>();
                for (String templateId : toAdd) {
                    PromptTemplateEntity template = promptTemplateMapper.selectById(templateId);
                    if (template != null && "ACTIVE".equals(template.getStatus())) {
                        templatesToSync.add(buildTemplateSyncRequest(template));
                    }
                }

                if (!templatesToSync.isEmpty()) {
                    try {
                        ragFeignClient.batchStorePromptTemplates(templatesToSync);
                        log.info("✅ 智能同步完成 - 新增: {}, 删除: {}", toAdd.size(), toDelete.size());
                    } catch (Exception e) {
                        log.error("批量存储模板失败", e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("智能同步失败", e);
        }
    }

    /**
     * 构建模板同步请求
     */
    private Map<String, Object> buildTemplateSyncRequest(PromptTemplateEntity entity) {
        Map<String, Object> request = new HashMap<>();
        request.put("templateId", entity.getId());
        request.put("templateName", entity.getName());
        request.put("content", buildVectorContent(entity));
        request.put("updatedAt", entity.getUpdatedAt().getTime()); // 传递更新时间用于后续判断

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", entity.getType() != null ? entity.getType() : "CUSTOM");
        metadata.put("category", entity.getCategory() != null ? entity.getCategory() : "");
        metadata.put("tags", entity.getTags() != null ? entity.getTags() : "");
        metadata.put("status", entity.getStatus());
        metadata.put("useCount", entity.getUseCount() != null ? entity.getUseCount() : 0);
        metadata.put("rating", entity.getRating() != null ? entity.getRating() : 0);
        metadata.put("description", entity.getDescription() != null ? entity.getDescription() : "");
        metadata.put("updatedAt", entity.getUpdatedAt().getTime());
        request.put("metadata", metadata);

        return request;
    }
}