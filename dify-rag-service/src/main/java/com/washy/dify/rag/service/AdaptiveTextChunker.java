package com.washy.dify.rag.service;

import com.washy.dify.rag.domain.DocumentChunk;
import com.washy.dify.rag.domain.DocumentSection;
import com.washy.dify.rag.domain.ImageInfo;
import com.washy.dify.rag.domain.TableInfo;
import com.washy.dify.rag.domain.dto.ChunkResult;
import com.washy.dify.rag.factory.VectorStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class AdaptiveTextChunker {

    @Autowired
    private WordToMarkdownParser wordParser;

    @Autowired
    private ImageExtractorService imageExtractor;

    @Autowired
    private TableExtractorService tableExtractor;

    @Autowired
    private SmartChunkerService smartChunker;

    @Autowired
    private SmartTextChunker textChunker;

    @Autowired
    private VectorStoreFactory factory;
    @Autowired
    private HybridStoreService hybridStoreService;

    @Autowired
    private PdfDocumentParser pdfParser;

    @Autowired
    private PdfImageExtractor pdfImageExtractor;

    @Autowired
    private LegacyDocParser legacyDocParser;

    @PostConstruct
    public void init() {
        // 在 Bean 初始化时设置 POI 的 Zip 解压比例限制
        // 默认是 0.01 (1%)，调整为 0.001 (0.1%) 让压缩比更高的文件也能通过
        ZipSecureFile.setMinInflateRatio(0.001);
        log.info("POI ZipSecureFile 解压比例限制已调整为: {}", ZipSecureFile.getMinInflateRatio());
    }

    /**
     * 统一入口：根据文件类型自动选择分块策略
     */
    public ChunkResult processDocument(MultipartFile file, String collectionName) {
        String fileName = file.getOriginalFilename();
        String documentId = UUID.randomUUID().toString();

        if (fileName == null) {
            return ChunkResult.fail("文件名为空");
        }

        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        switch (ext) {
            case "docx":
            case "doc":
                return processWordDocument(file, documentId, fileName, collectionName);
            case "pdf":
                return processPdfDocument(file, documentId, fileName, collectionName);
            case "txt":
            case "md":
            case "text":
                return processPlainText(file, documentId, fileName, collectionName);
            default:
                return ChunkResult.fail("不支持的文件格式: " + ext);
        }
    }

    private ChunkResult processPlainText(MultipartFile file, String documentId, String fileName, String collectionName) {
        try {
            String content = new String(file.getBytes(), "UTF-8");
            List<String> textChunks = textChunker.chunk(content);

            List<DocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < textChunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("type", "api_text");
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", textChunks.size());
                DocumentChunk chunk = DocumentChunk.builder()
                        .chunkId(documentId + "_" + String.format("%04d", i))
                        .documentId(documentId)
                        .sectionTitle("文本_" + (i + 1))
                        .sectionNumber(String.valueOf(i + 1))
                        .sectionLevel(1)
                        .content(textChunks.get(i))
                        .images(new ArrayList<>())
                        .tables(new ArrayList<>())
                        .metadata(metadata)
                        .build();
                chunks.add(chunk);
            }

            int stored = factory.getVectorStoreService().storeDocumentChunks(collectionName, chunks, documentId, fileName);
            return ChunkResult.success(documentId, fileName, chunks, new HashMap<>(), new ArrayList<>(), stored);

        } catch (Exception e) {
            log.error("文本处理失败: {}", fileName, e);
            return ChunkResult.fail(e.getMessage());
        }
    }

    private ChunkResult processPdfDocument(MultipartFile file, String documentId, String fileName, String collectionName) {
        try {
            byte[] fileBytes = file.getBytes();

            Map<String, ImageInfo> imageMap;
            try (PDDocument pdDoc = PDDocument.load(new ByteArrayInputStream(fileBytes))) {
                imageMap = pdfImageExtractor.extract(pdDoc, fileName);
            }

            List<DocumentSection> sections;
            try (InputStream is = new ByteArrayInputStream(fileBytes)) {
                sections = pdfParser.parse(is);
            }

            List<DocumentChunk> chunks = smartChunker.chunkSections(
                    sections, documentId, fileName, imageMap, new ArrayList<>());

            int stored = factory.getVectorStoreService().storeDocumentChunks(collectionName, chunks, documentId, fileName);
            return ChunkResult.success(documentId, fileName, chunks, imageMap, new ArrayList<>(), stored);

        } catch (Exception e) {
            log.error("PDF处理失败: {}", fileName, e);
            return ChunkResult.fail(e.getMessage());
        }
    }

    private ChunkResult processWordDocument(MultipartFile file, String documentId, String fileName, String collectionName) {
        try {
            String ext = getFileExtension(fileName);

            Map<String, ImageInfo> imageMap = new HashMap<>();
            List<TableInfo> tables = new ArrayList<>();
            List<DocumentSection> sections = new ArrayList<>();

            // ===== 根据文件类型选择解析器 =====
            if ("docx".equals(ext)) {
                // 处理 docx 文件（现有逻辑）
                try (InputStream is = file.getInputStream();
                     XWPFDocument xwpfDocument = new XWPFDocument(is)) {

                    // 提取图片
                    imageMap = imageExtractor.extractImages(xwpfDocument, fileName);

                    // 提取表格
                    tables = tableExtractor.extractTables(xwpfDocument);

                    // 解析文档结构
                    try (InputStream is2 = file.getInputStream()) {
                        sections = wordParser.parseViaMarkdown(is2);
                    }
                }

            } else if ("doc".equals(ext)) {
                // ===== 处理老式 .doc 文件 =====
                log.info("处理老式 .doc 文件: {}", fileName);

                try (InputStream is = file.getInputStream()) {
                    LegacyDocParser.ParseResult parseResult = legacyDocParser.parse(is, fileName, imageExtractor);

                    // 转换图片 Map（使用占位符作为key）
                    imageMap = parseResult.imagePlaceholders;

                    // 获取表格
                    tables = parseResult.tables;

                    // 获取章节
                    sections = parseResult.sections;
                }
            }

            log.info("提取完成: 图片={} 张, 表格={} 个, 章节={} 个",
                    imageMap.size(), tables.size(), sections.size());

            // ===== 后续处理（与现有逻辑相同）=====
            List<DocumentChunk> chunks = new ArrayList<>();

            // 处理表格的 Markdown 内容
            for (TableInfo table : tables) {
                if (table.getMarkdownContent() == null && table.getRows() != null) {
                    // 生成 Markdown 格式的表格
                    String markdownTable = convertToMarkdownTable(table);
                    table.setMarkdownContent(markdownTable);
                }
            }

            // 根据内容类型选择处理策略
            if (sections != null && !sections.isEmpty()) {
                if (sections.size() == 1 && sections.get(0).getLevel() == 0) {
                    // 没有识别到标题，使用智能分块
                    String fullText = extractFullTextWithTablesAndImages(sections, tables, imageMap);
                    List<String> textChunks = textChunker.chunk(fullText);

                    for (int i = 0; i < textChunks.size(); i++) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("type", "plain_text");
                        metadata.put("chunkIndex", i);
                        metadata.put("totalChunks", textChunks.size());
                        metadata.put("hasImages", !imageMap.isEmpty());
                        metadata.put("hasTables", tables.size());

                        DocumentChunk chunk = DocumentChunk.builder()
                                .chunkId(documentId + "_" + String.format("%04d", i))
                                .documentId(documentId)
                                .sectionTitle("文档内容")
                                .sectionNumber(String.valueOf(i + 1))
                                .sectionLevel(1)
                                .content(textChunks.get(i))
                                .images(new ArrayList<>(imageMap.values()))
                                .tables(tables)
                                .metadata(metadata)
                                .build();
                        chunks.add(chunk);
                    }
                } else {
                    // 有标题结构，按章节分块
                    chunks = smartChunker.chunkSections(sections, documentId, fileName, imageMap, tables);
                }
            } else if (!tables.isEmpty()) {
                // 只有表格
                chunks = processTablesOnly(tables, documentId, fileName);
            } else if (!imageMap.isEmpty()) {
                // 只有图片
                chunks = processImagesOnly(imageMap, documentId, fileName);
            } else {
                log.warn("文档无任何内容: {}", fileName);
                return ChunkResult.success(documentId, fileName, new ArrayList<>(), imageMap, tables, 0);
            }

            // 存入向量库
            if (!chunks.isEmpty()) {
                // 将图片和表格信息合并到内容中
                for (DocumentChunk chunk : chunks) {
                    String enhancedContent = enhanceContentWithImagesAndTables(
                            chunk.getContent(), chunk.getImages(), chunk.getTables()
                    );
                    chunk.setContent(enhancedContent);
                }

                hybridStoreService.storeWithHybridStrategy(chunks, documentId, fileName, collectionName);
                log.info("存入向量库: {} 个块", chunks.size());
            }

            return ChunkResult.success(documentId, fileName, chunks, imageMap, tables, chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: {}", fileName, e);
            return ChunkResult.fail(e.getMessage());
        }
    }

    /**
     * 增强内容：将图片和表格信息整合到文本中
     */
    private String enhanceContentWithImagesAndTables(String content, List<ImageInfo> images, List<TableInfo> tables) {
        StringBuilder sb = new StringBuilder(content);

        // 添加图片描述
        if (images != null && !images.isEmpty()) {
            sb.append("\n\n## 图片信息\n");
            for (ImageInfo img : images) {
                sb.append("\n- ").append(img.getOriginalName())
                        .append(" (").append(img.getWidth()).append("x").append(img.getHeight()).append(")")
                        .append("\n  ").append(img.getPlaceholder());
            }
        }

        // 添加表格内容（Markdown格式）
        if (tables != null && !tables.isEmpty()) {
            sb.append("\n\n## 表格内容\n");
            for (TableInfo table : tables) {
                if (table.getCaption() != null && !table.getCaption().isEmpty()) {
                    sb.append("\n### ").append(table.getCaption()).append("\n");
                }
                sb.append("\n").append(table.getFormattedContent()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 转换为 Markdown 表格
     */
    private String convertToMarkdownTable(TableInfo table) {
        if (table.getRows() == null || table.getRows().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        List<String[]> rows = table.getRows();
        int colCount = table.getColumnCount();

        // 表头
        String[] header = rows.get(0);
        sb.append("|");
        for (int i = 0; i < colCount; i++) {
            String cell = i < header.length ? header[i] : "";
            sb.append(" ").append(cell).append(" |");
        }
        sb.append("\n|");
        for (int i = 0; i < colCount; i++) {
            sb.append(" --- |");
        }
        sb.append("\n");

        // 数据行
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            sb.append("|");
            for (int j = 0; j < colCount; j++) {
                String cell = j < row.length ? row[j] : "";
                sb.append(" ").append(cell).append(" |");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 从章节和表格中提取完整文本
     */
    private String extractFullTextWithTablesAndImages(List<DocumentSection> sections,
                                                      List<TableInfo> tables,
                                                      Map<String, ImageInfo> images) {
        StringBuilder fullText = new StringBuilder();

        // 添加章节内容
        for (DocumentSection section : sections) {
            if (section.getFullTitle() != null) {
                fullText.append(section.getFullTitle()).append("\n");
            }
            for (Object element : section.getContentElements()) {
                if (element instanceof String) {
                    fullText.append((String) element).append("\n");
                }
            }
            fullText.append("\n");
        }

        // 添加表格
        for (TableInfo table : tables) {
            fullText.append(table.getFormattedContent()).append("\n\n");
        }

        // 添加图片描述
        for (ImageInfo image : images.values()) {
            fullText.append(image.getPlaceholder()).append("\n");
        }

        return fullText.toString();
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 处理纯表格文档（没有章节，只有表格）
     */
    private List<DocumentChunk> processTablesOnly(List<TableInfo> tables, String documentId, String fileName) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (TableInfo table : tables) {
            // 将表格内容转为文本
            String tableContent = formatTableAsText(table);

            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "table");
            metadata.put("documentType", "table_only");
            metadata.put("rowCount", table.getRowCount());
            metadata.put("columnCount", table.getColumnCount());
            metadata.put("hasHeader", table.isHasHeader());

            // 创建分块
            DocumentChunk chunk = DocumentChunk.builder()
                    .chunkId(documentId + "_table_" + String.format("%04d", chunkIndex))
                    .documentId(documentId)
                    .sectionTitle(getTableTitle(table, chunkIndex))
                    .sectionNumber(String.valueOf(chunkIndex + 1))
                    .sectionLevel(1)
                    .content(tableContent)
                    .images(new ArrayList<>())
                    .tables(Collections.singletonList(table))
                    .metadata(metadata)
                    .build();
            chunks.add(chunk);
            chunkIndex++;
        }

        return chunks;
    }

    /**
     * 处理纯图片文档（没有章节，只有图片）
     */
    private List<DocumentChunk> processImagesOnly(Map<String, ImageInfo> imageMap, String documentId, String fileName) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Map.Entry<String, ImageInfo> entry : imageMap.entrySet()) {
            ImageInfo image = entry.getValue();

            // 构建图片描述内容
            String imageContent = buildImageDescription(image);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "image");
            metadata.put("imageId", image.getImageId());
            metadata.put("format", image.getFormat());
            metadata.put("documentType", "image_only");

            DocumentChunk chunk = DocumentChunk.builder()
                    .chunkId(documentId + "_img_" + String.format("%04d", chunkIndex))
                    .documentId(documentId)
                    .sectionTitle(getImageTitle(image, chunkIndex))
                    .sectionNumber(String.valueOf(chunkIndex + 1))
                    .sectionLevel(1)
                    .content(imageContent)
                    .images(Collections.singletonList(image))
                    .tables(new ArrayList<>())
                    .metadata(metadata)
                    .build();
            chunks.add(chunk);
            chunkIndex++;
        }

        return chunks;
    }

    /**
     * 获取表格标题
     */
    private String getTableTitle(TableInfo table, int index) {
        if (table.getCaption() != null && !table.getCaption().isEmpty()) {
            return "表格_" + (index + 1) + "_" + table.getCaption();
        }
        return "表格_" + (index + 1);
    }

    /**
     * 获取图片标题
     */
    private String getImageTitle(ImageInfo image, int index) {
        if (image.getOriginalName() != null && !image.getOriginalName().isEmpty()) {
            return "图片_" + (index + 1) + "_" + image.getOriginalName();
        }
        return "图片_" + (index + 1);
    }

    /**
     * 将表格格式化为文本（基于你的 TableInfo 定义）
     */
    private String formatTableAsText(TableInfo table) {
        StringBuilder sb = new StringBuilder();

        // 添加表格标题
        if (table.getCaption() != null && !table.getCaption().isEmpty()) {
            sb.append("【").append(table.getCaption()).append("】\n\n");
        }

        // 获取行数据
        List<String[]> rows = table.getRows();
        if (rows == null || rows.isEmpty()) {
            return sb.toString();
        }

        int columnCount = table.getColumnCount();
        boolean hasHeader = table.isHasHeader();

        // 输出表头（如果有）
        if (hasHeader && !rows.isEmpty()) {
            String[] headerRow = rows.get(0);
            sb.append("|");
            for (int i = 0; i < headerRow.length; i++) {
                String cell = headerRow[i] != null ? headerRow[i] : "";
                sb.append(" ").append(cell).append(" |");
            }
            sb.append("\n|");
            for (int i = 0; i < headerRow.length; i++) {
                sb.append(" --- |");
            }
            sb.append("\n");

            // 数据行
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                sb.append("|");
                for (int j = 0; j < columnCount; j++) {
                    String cell = (j < row.length && row[j] != null) ? row[j] : "";
                    sb.append(" ").append(cell).append(" |");
                }
                sb.append("\n");
            }
        } else {
            // 无表头，直接输出所有行
            for (String[] row : rows) {
                sb.append("|");
                for (int i = 0; i < row.length; i++) {
                    String cell = row[i] != null ? row[i] : "";
                    sb.append(" ").append(cell).append(" |");
                }
                sb.append("\n");
            }
        }

        sb.append("\n（共 ").append(table.getRowCount()).append(" 行，").append(table.getColumnCount()).append(" 列）");

        return sb.toString();
    }

    /**
     * 构建图片描述
     */
    private String buildImageDescription(ImageInfo image) {
        StringBuilder sb = new StringBuilder();

        sb.append("📷 图片信息\n");
        sb.append("- 文件名: ").append(image.getOriginalName() != null ? image.getOriginalName() : "未知").append("\n");
        sb.append("- 格式: ").append(image.getFormat() != null ? image.getFormat() : "未知").append("\n");
        sb.append("- 尺寸: ").append(image.getWidth()).append(" x ").append(image.getHeight()).append("\n");
        sb.append("- 大小: ").append(formatFileSize(image.getSize())).append("\n");

        if (image.getLocalPath() != null && !image.getLocalPath().isEmpty()) {
            sb.append("- 本地路径: ").append(image.getLocalPath()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }
}