package com.washy.dify.rag.service;

import com.washy.dify.rag.config.ChunkerConfig;
import com.washy.dify.rag.domain.DocumentChunk;
import com.washy.dify.rag.domain.DocumentSection;
import com.washy.dify.rag.domain.ImageInfo;
import com.washy.dify.rag.domain.TableInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 智能文档分块服务
 * <p>负责将文档章节内容分割成合适大小的文本块，以便后续向量化存储和检索。</p>
 * <p>分块原则：</p>
 * <ul>
 *   <li>按章节分块，保持章节完整性</li>
 *   <li>表格不能被分割，作为整体处理</li>
 *   <li>过小的块合并到父章节，避免碎片化</li>
 *   <li>不能跨父章节合并，保持文档结构</li>
 * </ul>
 */
@Slf4j
@Service
public class SmartChunkerService {

    /**
     * 分块配置（可选注入）
     */
    @Autowired(required = false)
    private ChunkerConfig config;
    
    /**
     * 块质量过滤器
     */
    @Autowired
    private ChunkQualityFilter qualityFilter;
    
    /**
     * 智能分块主方法
     * <p>将文档章节内容分割成合适大小的文本块，并进行质量过滤。</p>
     * 
     * @param sections    文档章节列表
     * @param documentId  文档ID
     * @param documentName 文档名称
     * @param imageMap    图片映射（图片占位符 -> 图片信息）
     * @param tables      表格列表
     * @return 分块结果列表
     */
    public List<DocumentChunk> chunkSections(List<DocumentSection> sections,
                                             String documentId,
                                             String documentName,
                                             Map<String, ImageInfo> imageMap,
                                             List<TableInfo> tables) {
        
        log.info("开始智能分块: 文档={}, 章节数={}, 图片数={}, 表格数={}", 
            documentName, sections.size(), imageMap.size(), tables.size());
        
        // 步骤1：将内容分配到对应章节
        Map<String, SectionContent> contentMap = buildContentMap(sections, tables);
        
        // 步骤2：对每个章节的内容进行分块
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        
        for (DocumentSection section : sections) {
            SectionContent content = contentMap.get(section.getSectionNumber());
            if (content == null) continue;
            
            List<DocumentChunk> sectionChunks = chunkSectionContent(
                section, content, documentId, documentName, imageMap, chunkIndex
            );
            chunks.addAll(sectionChunks);
            chunkIndex += sectionChunks.size();
        }
        
        // 步骤3：合并过小的块
        chunks = mergeSmallChunks(chunks);
        
        log.info("智能分块完成: 共生成 {} 个块", chunks.size());
        printChunkSummary(chunks);
        chunks = qualityFilter.filterChunks(chunks);
        log.info("过滤低质量块完成: 共生成 {} 个块", chunks.size());
        return chunks;
    }


    /**
     * 构建章节内容映射
     */
    private Map<String, SectionContent> buildContentMap(List<DocumentSection> sections,
                                                         List<TableInfo> tables) {
        Map<String, SectionContent> contentMap = new LinkedHashMap<>();
        
        for (DocumentSection section : sections) {
            SectionContent content = new SectionContent();
            content.section = section;
            content.textContent = new StringBuilder();
            content.images = new ArrayList<>();
            content.tables = new ArrayList<>();
            
            // 处理内容元素
            for (Object element : section.getContentElements()) {
                if (element instanceof String) {
                    String text = (String) element;
                    content.textContent.append(text).append("\n");
                }
            }
            
            contentMap.put(section.getSectionNumber(), content);
        }
        
        return contentMap;
    }
    
    /**
     * 对单个章节内容进行分块
     * <p>将章节内容按段落分割，若超过最大块大小则进行分段处理，保留重叠部分。</p>
     * 
     * @param section      章节信息
     * @param content      章节内容
     * @param documentId   文档ID
     * @param documentName 文档名称
     * @param imageMap     图片映射
     * @param startIndex   块起始索引
     * @return 分块结果列表
     */
    private List<DocumentChunk> chunkSectionContent(DocumentSection section,
                                                     SectionContent content,
                                                     String documentId,
                                                     String documentName,
                                                     Map<String, ImageInfo> imageMap,
                                                     int startIndex) {

        String fullContent = content.textContent.toString().trim();
        List<DocumentChunk> chunks = new ArrayList<>();
        // 如果内容为空，跳过这个章节
        if (fullContent.isEmpty()) {
            log.debug("章节 {} 内容为空，跳过", section.getSectionNumber());
            return chunks;
        }


        // 替换图片占位符
        fullContent = replaceImagePlaceholders(fullContent, imageMap, documentName);
        
        // 如果内容不超过最大块大小，创建一个块
        if (fullContent.length() <= getMaxChunkSize()) {
            DocumentChunk chunk = createChunk(section, fullContent, content, 
                documentId, documentName, startIndex);
            chunks.add(chunk);
            return chunks;
        }
        
        // 按段落分割
        String[] paragraphs = fullContent.split("\n");
        StringBuilder currentChunkText = new StringBuilder();
        List<ImageInfo> currentImages = new ArrayList<>();
        List<TableInfo> currentTables = new ArrayList<>();
        int chunkIndex = startIndex;
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            boolean isTableContent = paragraph.contains("[表格开始]");
            boolean isImageContent = paragraph.contains("[图片:");
            
            // 如果是表格开始，需要保证表格完整性
            if (isTableContent) {
                // 如果当前块已有内容，先保存
                if (currentChunkText.length() > getMinChunkSize()) {
                    DocumentChunk chunk = createChunk(section, currentChunkText.toString(),
                        new SectionContentWithMedia(currentImages, currentTables),
                        documentId, documentName, chunkIndex++);
                    chunks.add(chunk);
                    currentChunkText = new StringBuilder();
                    currentImages = new ArrayList<>();
                    currentTables = new ArrayList<>();
                }
                
                // 收集完整的表格内容
                StringBuilder tableBlock = new StringBuilder();
                tableBlock.append(paragraph).append("\n");
                while (i + 1 < paragraphs.length && !paragraphs[i + 1].contains("[表格结束]")) {
                    i++;
                    tableBlock.append(paragraphs[i]).append("\n");
                }
                if (i + 1 < paragraphs.length) {
                    i++;
                    tableBlock.append(paragraphs[i]).append("\n"); // 表格结束
                }
                
                currentChunkText.append(tableBlock);
            } 
            // 如果是图片，记录图片信息
            else if (isImageContent) {
                currentChunkText.append(paragraph).append("\n");
                // 提取图片路径
                List<ImageInfo> paraImages = extractImagesFromText(paragraph, imageMap);
                currentImages.addAll(paraImages);
            }
            // 普通文本
            else {
                // 如果加入该段落后超过最大大小，保存当前块
                if (currentChunkText.length() + paragraph.length() > getMaxChunkSize()
                    && currentChunkText.length() > getMinChunkSize()) {
                    
                    DocumentChunk chunk = createChunk(section, currentChunkText.toString(),
                        new SectionContentWithMedia(currentImages, currentTables),
                        documentId, documentName, chunkIndex++);
                    chunks.add(chunk);
                    
                    // 添加重叠部分
                    currentChunkText = new StringBuilder();
                    if (getOverlap() > 0 && i > 0) {
                        String overlap = paragraphs[i - 1];
                        if (overlap.length() > getOverlap()) {
                            overlap = overlap.substring(
                                overlap.length() - getOverlap());
                        }
                        currentChunkText.append("[上文续接] ").append(overlap).append("\n");
                    }
                    
                    currentImages = new ArrayList<>();
                    currentTables = new ArrayList<>();
                }
                
                currentChunkText.append(paragraph).append("\n");
            }
        }
        
        // 保存最后一个块
        if (currentChunkText.length() > 0) {
            DocumentChunk chunk = createChunk(section, currentChunkText.toString(),
                new SectionContentWithMedia(currentImages, currentTables),
                documentId, documentName, chunkIndex);
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * 合并过小的块
     * 规则：只能合并到同一父章节下的前一个块
     */
    private List<DocumentChunk> mergeSmallChunks(List<DocumentChunk> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        
        List<DocumentChunk> merged = new ArrayList<>();
        DocumentChunk previous = null;
        
        for (DocumentChunk current : chunks) {
            if (previous == null) {
                previous = current;
                continue;
            }
            
            // 检查是否可以合并
            boolean canMerge = canMergeChunks(previous, current);
            boolean isTooSmall = previous.getContent().length() < config.getChunk().getMinSize();
            
            if (canMerge && isTooSmall) {
                // 合并到前一个块
                log.debug("合并小块: {} ({}字符) -> {} ({}字符)", 
                    previous.getChunkId(), previous.getContent().length(),
                    current.getChunkId(), current.getContent().length());
                
                String mergedContent = previous.getContent() + "\n\n" + current.getContent();
                
                // 合并图片和表格
                List<ImageInfo> mergedImages = new ArrayList<>(previous.getImages());
                mergedImages.addAll(current.getImages());
                
                List<TableInfo> mergedTables = new ArrayList<>(previous.getTables());
                mergedTables.addAll(current.getTables());
                
                previous = DocumentChunk.builder()
                    .chunkId(previous.getChunkId())
                    .documentId(previous.getDocumentId())
                    .sectionNumber(previous.getSectionNumber())
                    .sectionTitle(previous.getSectionTitle())
                    .sectionLevel(previous.getSectionLevel())
                    .content(mergedContent)
                    .images(mergedImages)
                    .tables(mergedTables)
                    .metadata(buildMetadata(previous, mergedContent, mergedImages, mergedTables))
                    .build();
            } else {
                merged.add(previous);
                previous = current;
            }
        }
        
        if (previous != null) {
            merged.add(previous);
        }
        
        log.info("合并前后块数: {} -> {}", chunks.size(), merged.size());
        return merged;
    }
    
    /**
     * 判断两个块是否可以合并
     * 必须是同一父章节下的块
     */
    private boolean canMergeChunks(DocumentChunk chunk1, DocumentChunk chunk2) {
        String parent1 = getParentSection(chunk1.getSectionNumber());
        String parent2 = getParentSection(chunk2.getSectionNumber());
        return parent1.equals(parent2);
    }
    
    /**
     * 获取父章节编号
     */
    private String getParentSection(String sectionNumber) {
        if (sectionNumber == null || !sectionNumber.contains(".")) {
            return sectionNumber == null ? "" : sectionNumber;
        }
        return sectionNumber.substring(0, sectionNumber.lastIndexOf("."));
    }
    
    /**
     * 创建文档块
     */
    private DocumentChunk createChunk(DocumentSection section, String content,
                                       SectionContent contentObj,
                                       String documentId, String documentName,
                                       int index) {
        return createChunk(section, content, 
            new SectionContentWithMedia(contentObj.images, contentObj.tables),
            documentId, documentName, index);
    }
    
    private DocumentChunk createChunk(DocumentSection section, String content,
                                       SectionContentWithMedia media,
                                       String documentId, String documentName,
                                       int index) {
        String chunkId = String.format("%s_%04d", documentId, index);
        
        List<ImageInfo> images = media != null ? media.images : new ArrayList<>();
        List<TableInfo> tables = media != null ? media.tables : new ArrayList<>();
        
        return DocumentChunk.builder()
            .chunkId(chunkId)
            .documentId(documentId)
            .sectionNumber(section.getSectionNumber())
            .sectionTitle(section.getTitle())
            .sectionLevel(section.getLevel())
            .content(content.trim())
            .images(images)
            .tables(tables)
            .metadata(buildMetadata(section, content, images, tables))
            .build();
    }
    
    /**
     * 构建元数据
     */
    private Map<String, Object> buildMetadata(DocumentSection section, String content,
                                               List<ImageInfo> images, List<TableInfo> tables) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sectionNumber", section.getSectionNumber());
        metadata.put("sectionTitle", section.getTitle());
        metadata.put("sectionLevel", section.getLevel());
        metadata.put("contentLength", content.length());
        metadata.put("imageCount", images.size());
        metadata.put("tableCount", tables.size());
        metadata.put("hasImages", !images.isEmpty());
        metadata.put("hasTables", !tables.isEmpty());
        metadata.put("createdAt", new Date().toString());
        return metadata;
    }
    
    private Map<String, Object> buildMetadata(DocumentChunk chunk, String content,
                                               List<ImageInfo> images, List<TableInfo> tables) {
        Map<String, Object> metadata = new LinkedHashMap<>(chunk.getMetadata());
        metadata.put("contentLength", content.length());
        metadata.put("imageCount", images.size());
        metadata.put("tableCount", tables.size());
        metadata.put("mergedAt", new Date().toString());
        return metadata;
    }
    
    /**
     * 替换图片占位符
     */
    private String replaceImagePlaceholders(String text, Map<String, ImageInfo> imageMap, 
                                            String documentName) {
        if (imageMap == null || imageMap.isEmpty()) return text;
        
        String result = text;
        for (Map.Entry<String, ImageInfo> entry : imageMap.entrySet()) {
            result = result.replace(entry.getKey(), 
                "[图片: " + entry.getValue().getLocalPath() + "]");
        }
        return result;
    }
    
    /**
     * 从文本中提取图片信息
     */
    private List<ImageInfo> extractImagesFromText(String text, Map<String, ImageInfo> imageMap) {
        List<ImageInfo> images = new ArrayList<>();
        for (Map.Entry<String, ImageInfo> entry : imageMap.entrySet()) {
            if (text.contains(entry.getValue().getLocalPath())) {
                images.add(entry.getValue());
            }
        }
        return images;
    }
    

    /**
     * 打印分块摘要
     */
    private void printChunkSummary(List<DocumentChunk> chunks) {
        log.info("========== 分块摘要 ==========");
        int totalChars = 0;
        int totalImages = 0;
        int totalTables = 0;
        
        for (DocumentChunk chunk : chunks) {
            int charLen = chunk.getContent().length();
            int imgCount = chunk.getImages() != null ? chunk.getImages().size() : 0;
            int tblCount = chunk.getTables() != null ? chunk.getTables().size() : 0;
            
            log.info("  {}: 章节={}, 字符数={}, 图片={}, 表格={}", 
                chunk.getChunkId(), chunk.getSectionNumber(), charLen, imgCount, tblCount);
            
            totalChars += charLen;
            totalImages += imgCount;
            totalTables += tblCount;
        }
        
        log.info("总计: {}个块, {}字符, {}图片, {}表格", 
            chunks.size(), totalChars, totalImages, totalTables);
        log.info("==============================");
    }
    
    // 内部类
    private static class SectionContent {
        DocumentSection section;
        StringBuilder textContent;
        List<ImageInfo> images;
        List<TableInfo> tables;
    }
    
    private static class SectionContentWithMedia {
        List<ImageInfo> images;
        List<TableInfo> tables;
        
        SectionContentWithMedia(List<ImageInfo> images, List<TableInfo> tables) {
            this.images = images;
            this.tables = tables;
        }
    }

    // 添加获取配置的辅助方法
    private int getMaxChunkSize() {
        if (config != null && config.getChunk() != null) {
            return config.getChunk().getMaxSize();
        }
        return 2000; // 默认值
    }

    private int getMinChunkSize() {
        if (config != null && config.getChunk() != null) {
            return config.getChunk().getMinSize();
        }
        return 200; // 默认值
    }

    private int getOverlap() {
        if (config != null && config.getChunk() != null) {
            return config.getChunk().getOverlap();
        }
        return 100; // 默认值
    }
}