package com.washy.dify.prompt.version;

import com.washy.dify.common.entity.prompt.PromptTemplateVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词版本管理器
 */
@Slf4j
@Service
public class VersionManager {
    
    private final Map<String, List<PromptVersion>> versionStore = new ConcurrentHashMap<>();
    
    /**
     * 创建新版本
     */
    public PromptVersion createVersion(String templateId, PromptTemplateVO content, String changeLog) {
        List<PromptVersion> versions = versionStore.computeIfAbsent(templateId, k -> new ArrayList<>());
        
        int newVersionNum = versions.size() + 1;
        PromptVersion version = new PromptVersion();
        version.setVersionId(UUID.randomUUID().toString());
        version.setTemplateId(templateId);
        version.setVersionNumber(newVersionNum);
        version.setVersionCode("v" + newVersionNum + ".0.0");
        version.setContent(content);
        version.setChangeLog(changeLog);
        version.setCreatedAt(new Date());
        
        versions.add(version);
        log.info("创建新版本: templateId={}, version={}", templateId, version.getVersionCode());
        
        return version;
    }
    
    /**
     * 获取版本列表
     */
    public List<PromptVersion> getVersions(String templateId) {
        return versionStore.getOrDefault(templateId, new ArrayList<>());
    }
    
    /**
     * 获取指定版本
     */
    public PromptVersion getVersion(String templateId, String versionCode) {
        List<PromptVersion> versions = versionStore.get(templateId);
        if (versions == null) return null;
        
        return versions.stream()
            .filter(v -> v.getVersionCode().equals(versionCode))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 回滚到指定版本
     */
    public PromptTemplateVO rollback(String templateId, String versionCode) {
        PromptVersion targetVersion = getVersion(templateId, versionCode);
        if (targetVersion == null) {
            throw new IllegalArgumentException("版本不存在: " + versionCode);
        }
        
        log.info("回滚模板: {} -> {}", templateId, versionCode);
        return targetVersion.getContent();
    }
    
    /**
     * 比较两个版本
     */
    public VersionDiff compare(String templateId, String version1, String version2) {
        PromptVersion v1 = getVersion(templateId, version1);
        PromptVersion v2 = getVersion(templateId, version2);
        
        if (v1 == null || v2 == null) {
            throw new IllegalArgumentException("版本不存在");
        }
        
        VersionDiff diff = new VersionDiff();
        diff.setVersion1(version1);
        diff.setVersion2(version2);
        
        String content1 = v1.getContent().getTemplate();
        String content2 = v2.getContent().getTemplate();
        
        diff.setHasChanges(!content1.equals(content2));
        diff.setLengthDiff(content2.length() - content1.length());
        
        return diff;
    }
    
    @Data
    public static class PromptVersion {
        private String versionId;
        private String templateId;
        private Integer versionNumber;
        private String versionCode;
        private PromptTemplateVO content;
        private String changeLog;
        private Date createdAt;
    }
    
    @Data
    public static class VersionDiff {
        private String version1;
        private String version2;
        private boolean hasChanges;
        private int lengthDiff;
    }
}