package com.washy.dify.rag.service;

import com.washy.dify.rag.domain.DocumentSection;
import com.washy.dify.rag.domain.ImageInfo;
import com.washy.dify.rag.domain.TableInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LegacyDocParser {

    /**
     * 解析老式 .doc 文件
     * @param inputStream 文件输入流
     * @param documentName 文档名称
     * @param imageExtractor 图片提取服务（用于保存图片）
     * @return 解析结果
     */
    public ParseResult parse(InputStream inputStream, String documentName, ImageExtractorService imageExtractor) {
        ParseResult result = new ParseResult();
        result.sections = new ArrayList<>();
        result.images = new ArrayList<>();
        result.tables = new ArrayList<>();
        result.imagePlaceholders = new LinkedHashMap<>();

        try (POIFSFileSystem fs = new POIFSFileSystem(inputStream);
             HWPFDocument doc = new HWPFDocument(fs)) {

            // 获取文档范围
            Range range = doc.getRange();

            // 1. 提取并保存图片
            PicturesTable picturesTable = doc.getPicturesTable();
            extractAndSaveImages(range, picturesTable, documentName, imageExtractor, result);

            // 2. 提取表格
            extractTables(range, result);

            // 3. 解析文档结构（标题 + 正文）
            parseDocumentStructure(range, result);

            // 4. 清理和优化章节
            optimizeSections(result.sections);

            log.info("解析.doc完成: 章节={}, 图片={}, 表格={}",
                    result.sections.size(), result.images.size(), result.tables.size());

        } catch (Exception e) {
            log.error("解析.doc文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析.doc文件失败: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 提取并保存图片
     */
    private void extractAndSaveImages(Range range, PicturesTable picturesTable,
                                      String documentName, ImageExtractorService imageExtractor,
                                      ParseResult result) {
        try {
            int imageCount = 0;

            // 通过 PicturesTable 提取所有图片
            List<Picture> pictures = picturesTable.getAllPictures();
            log.info("通过PicturesTable发现 {} 张图片", pictures.size());

            for (int i = 0; i < pictures.size(); i++) {
                Picture picture = pictures.get(i);
                try {
                    byte[] imageData = picture.getContent();
                    String format = detectImageFormatFromPicture(picture);
                    String originalName = String.format("image_%d.%s", i + 1, format);
                    String placeholder = String.format("{{IMAGE_%04d}}", imageCount + 1);

                    // 使用现有的 ImageExtractorService 保存图片
                    ImageInfo imageInfo = imageExtractor.saveImage(
                            imageData, originalName, documentName, placeholder
                    );

                    if (imageInfo != null) {
                        result.images.add(imageInfo);
                        result.imagePlaceholders.put(placeholder, imageInfo);
                        imageCount++;
                    }
                } catch (Exception e) {
                    log.warn("提取图片失败: {}", e.getMessage());
                }
            }

            // 注意：HWPF 的 hasPicture 和 extractPicture 需要 CharacterRun 参数
            // 这里跳过，因为 getAllPictures() 已经获取了所有图片
            // 如果需要从段落中提取内联图片，可以通过遍历 CharacterRun 实现

            log.info("成功提取 {} 张图片", result.images.size());

        } catch (Exception e) {
            log.error("提取图片失败", e);
        }
    }

    /**
     * 从 Picture 对象检测图片格式
     */
    private String detectImageFormatFromPicture(Picture picture) {
        // 方法1：通过文件扩展名推断
        String ext = picture.suggestFileExtension();
        if (ext != null && !ext.isEmpty()) {
            String lowerExt = ext.toLowerCase();
            if (lowerExt.equals("jpg") || lowerExt.equals("jpeg")) return "jpeg";
            if (lowerExt.equals("png")) return "png";
            if (lowerExt.equals("gif")) return "gif";
            if (lowerExt.equals("bmp")) return "bmp";
            if (lowerExt.equals("wmf")) return "wmf";
            if (lowerExt.equals("emf")) return "emf";
            return lowerExt;
        }

        // 方法2：通过图片内容检测
        byte[] content = picture.getContent();
        if (content != null && content.length >= 4) {
            // JPEG
            if (content[0] == (byte)0xFF && content[1] == (byte)0xD8) {
                return "jpeg";
            }
            // PNG
            if (content[0] == (byte)0x89 && content[1] == (byte)0x50 &&
                    content[2] == (byte)0x4E && content[3] == (byte)0x47) {
                return "png";
            }
            // GIF
            if (content[0] == (byte)0x47 && content[1] == (byte)0x49 &&
                    content[2] == (byte)0x46 && content[3] == (byte)0x38) {
                return "gif";
            }
            // BMP
            if (content[0] == (byte)0x42 && content[1] == (byte)0x4D) {
                return "bmp";
            }
        }

        return "jpg";
    }

    /**
     * 提取表格 - HWPF 中表格需要通过遍历段落来识别
     */
    private void extractTables(Range range, ParseResult result) {
        try {
            int tableIndex = 0;
            List<TableInfo> tables = new ArrayList<>();
            List<String[]> currentTable = null;
            int paragraphIndex = 0;
            int totalParagraphs = range.numParagraphs();

            while (paragraphIndex < totalParagraphs) {
                Paragraph paragraph = range.getParagraph(paragraphIndex);
                String text = paragraph.text();

                if (text == null) {
                    paragraphIndex++;
                    continue;
                }

                // 检查是否是表格行（HWPF中表格通常以特定字符分隔）
                // 方法：检测是否包含表格单元格分隔符
                if (isTableRow(text)) {
                    if (currentTable == null) {
                        currentTable = new ArrayList<>();
                    }

                    // 解析表格行
                    String[] rowCells = parseTableRow(text);
                    if (rowCells.length > 0) {
                        currentTable.add(rowCells);
                    }
                    paragraphIndex++;

                } else {
                    // 非表格行，如果当前有表格则保存
                    if (currentTable != null && !currentTable.isEmpty()) {
                        TableInfo tableInfo = convertToTableInfo(currentTable, tableIndex++);
                        if (tableInfo != null) {
                            tables.add(tableInfo);
                        }
                        currentTable = null;
                    }
                    paragraphIndex++;
                }
            }

            // 保存最后一个表格
            if (currentTable != null && !currentTable.isEmpty()) {
                TableInfo tableInfo = convertToTableInfo(currentTable, tableIndex);
                if (tableInfo != null) {
                    tables.add(tableInfo);
                }
            }

            result.tables = tables;
            log.info("成功提取 {} 个表格", result.tables.size());

        } catch (Exception e) {
            log.error("提取表格失败", e);
        }
    }

    /**
     * 判断是否是表格行
     * HWPF 中表格单元格通常用 \u0007 分隔
     */
    private boolean isTableRow(String text) {
        if (text == null) return false;
        // 表格单元格分隔符
        return text.contains("\u0007") || (text.contains("|") && text.split("\\|").length >= 3);
    }

    /**
     * 解析表格行
     */
    private String[] parseTableRow(String text) {
        // 移除表格结束标记
        String cleanText = text.replace("\u0007", "|")
                .replace("\u0003", "")
                .replace("\r", "")
                .replace("\n", "");

        // 按竖线分割
        String[] cells = cleanText.split("\\|");
        List<String> cellList = new ArrayList<>();

        for (String cell : cells) {
            String trimmed = cell.trim();
            if (!trimmed.isEmpty() || cellList.isEmpty()) {
                // 保留空单元格以维持结构
                cellList.add(trimmed);
            }
        }

        // 如果解析结果太少，尝试其他方式
        if (cellList.size() < 2 && text.contains("\t")) {
            // 按制表符分割
            String[] tabCells = text.split("\t");
            for (String cell : tabCells) {
                String trimmed = cell.trim();
                if (!trimmed.isEmpty()) {
                    cellList.add(trimmed);
                }
            }
        }

        return cellList.toArray(new String[0]);
    }

    /**
     * 转换为 TableInfo
     */
    private TableInfo convertToTableInfo(List<String[]> rows, int index) {
        if (rows == null || rows.isEmpty()) return null;

        int maxColCount = 0;
        for (String[] row : rows) {
            maxColCount = Math.max(maxColCount, row.length);
        }

        // 补齐列数
        List<String[]> normalizedRows = new ArrayList<>();
        for (String[] row : rows) {
            if (row.length < maxColCount) {
                String[] newRow = Arrays.copyOf(row, maxColCount);
                for (int i = row.length; i < maxColCount; i++) {
                    newRow[i] = "";
                }
                normalizedRows.add(newRow);
            } else {
                normalizedRows.add(row);
            }
        }

        TableInfo tableInfo = TableInfo.builder()
                .tableId(UUID.randomUUID().toString())
                .caption("表格_" + (index + 1))
                .rowCount(normalizedRows.size())
                .columnCount(maxColCount)
                .hasHeader(normalizedRows.size() > 0)
                .rows(normalizedRows)
                .build();

        // 生成 Markdown 格式的表格内容
        String markdownContent = formatTableAsMarkdown(normalizedRows, maxColCount);
        tableInfo.setMarkdownContent(markdownContent);

        return tableInfo;
    }

    /**
     * 格式化为 Markdown 表格
     */
    private String formatTableAsMarkdown(List<String[]> rows, int maxColCount) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 表头（第一行作为表头）
        String[] headerRow = rows.get(0);
        sb.append("|");
        for (int i = 0; i < maxColCount; i++) {
            String cell = i < headerRow.length ? headerRow[i] : "";
            sb.append(" ").append(escapeMarkdownCell(cell)).append(" |");
        }
        sb.append("\n|");
        for (int i = 0; i < maxColCount; i++) {
            sb.append(" --- |");
        }
        sb.append("\n");

        // 数据行
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            sb.append("|");
            for (int j = 0; j < maxColCount; j++) {
                String cell = j < row.length ? row[j] : "";
                sb.append(" ").append(escapeMarkdownCell(cell)).append(" |");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 转义 Markdown 表格中的特殊字符
     */
    private String escapeMarkdownCell(String cell) {
        if (cell == null) return "";
        return cell.replace("|", "\\|")
                .replace("\n", " ")
                .replace("\r", "");
    }

    /**
     * 解析文档结构（标题 + 正文）
     */
    private void parseDocumentStructure(Range range, ParseResult result) {
        List<DocumentSection> sections = new ArrayList<>();
        DocumentSection currentSection = null;

        // 标题模式识别
        Pattern headingPattern1 = Pattern.compile("^第[一二三四五六七八九十\\d]+[章节条款]\\s*");
        Pattern headingPattern2 = Pattern.compile("^\\d+(\\.\\d+)*\\s+");
        Pattern headingPattern3 = Pattern.compile("^[一二三四五六七八九十]+[、．]\\s*");

        int paragraphCount = range.numParagraphs();

        for (int i = 0; i < paragraphCount; i++) {
            Paragraph paragraph = range.getParagraph(i);
            String text = cleanText(paragraph.text());

            if (text == null || text.isEmpty()) {
                continue;
            }

            // 检查是否是标题
            Integer headingLevel = detectHeadingLevel(paragraph, text, headingPattern1, headingPattern2, headingPattern3);

            if (headingLevel != null && headingLevel > 0) {
                // 保存上一个章节
                if (currentSection != null) {
                    sections.add(currentSection);
                }

                // 创建新章节
                currentSection = new DocumentSection();
                currentSection.setTitle(text);
                currentSection.setFullTitle(text);
                currentSection.setLevel(headingLevel);

                log.debug("识别标题: 级别={}, 文本={}", headingLevel, truncate(text, 30));

            } else if (currentSection != null) {
                // 正文内容
                if (!text.isEmpty()) {
                    currentSection.addText(text);
                }
            } else {
                // 文档开头（第一个标题之前）
                currentSection = new DocumentSection();
                currentSection.setTitle("文档前言");
                currentSection.setFullTitle("文档前言");
                currentSection.setLevel(0);
                if (!text.isEmpty()) {
                    currentSection.addText(text);
                }
            }
        }

        // 添加最后一个章节
        if (currentSection != null) {
            sections.add(currentSection);
        }

        result.sections = sections;
    }

    /**
     * 检测标题级别
     */
    private Integer detectHeadingLevel(Paragraph paragraph, String text,
                                       Pattern pattern1, Pattern pattern2, Pattern pattern3) {
        // 1. 检查样式索引
        int styleIndex = paragraph.getStyleIndex();
        if (styleIndex >= 1 && styleIndex <= 9) {
            return styleIndex;
        }

        // 2. 检查字符样式（粗体、字号等）- 注意 HWPF 的 API 不同
        try {
            // 获取第一个字符运行
            if (paragraph.numCharacterRuns() > 0) {
                CharacterRun run = paragraph.getCharacterRun(0);
                if (run != null) {
                    boolean isBold = run.isBold();
                    int fontSize = run.getFontSize();

                    if (isBold && fontSize >= 16 && text.length() < 50) {
                        return 1;
                    } else if (isBold && text.length() < 40) {
                        return 2;
                    } else if (fontSize >= 18 && text.length() < 50) {
                        return 1;
                    } else if (fontSize >= 14 && text.length() < 50) {
                        return 2;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("获取字符样式失败: {}", e.getMessage());
        }

        // 3. 文本模式匹配
        if (pattern1.matcher(text).find()) {
            return 1;
        }
        if (pattern2.matcher(text).find() && text.length() < 50) {
            int dotCount = countOccurrences(text, '.');
            return Math.min(dotCount + 1, 6);
        }
        if (pattern3.matcher(text).find() && text.length() < 30) {
            return 2;
        }

        // 4. 其他标题特征
        if (text.length() < 30 && !text.contains("。") && !text.contains("，") && !text.contains("？")) {
            return 3;
        }

        return null;
    }

    /**
     * 优化章节结构
     */
    private void optimizeSections(List<DocumentSection> sections) {
        if (sections.isEmpty()) return;

        // 合并连续的正文到前一个章节
        List<DocumentSection> optimized = new ArrayList<>();
        DocumentSection prev = null;

        for (DocumentSection section : sections) {
            if (prev != null && prev.getLevel() == 0 && section.getLevel() == 0) {
                prev.getContentElements().addAll(section.getContentElements());
            } else {
                optimized.add(section);
                prev = section;
            }
        }

        sections.clear();
        sections.addAll(optimized);

        // 生成章节编号
        Map<Integer, Integer> counters = new TreeMap<>();
        for (DocumentSection section : sections) {
            int level = section.getLevel();
            if (level > 0) {
                counters.merge(level, 1, Integer::sum);
                counters.keySet().removeIf(l -> l > level);

                StringBuilder number = new StringBuilder();
                for (int l = 1; l <= level; l++) {
                    if (number.length() > 0) number.append(".");
                    number.append(counters.getOrDefault(l, 1));
                }
                section.setSectionNumber(number.toString());
                section.setFullTitle(number + " " + section.getTitle());
            }
        }
    }

    /**
     * 清理文本（去除特殊字符）
     */
    private String cleanText(String text) {
        if (text == null) return "";
        // 移除 Word 中的特殊标记
        text = text.replaceAll("[\u0000-\u001F\u007F-\u009F]", "");
        // 移除表格分隔符
        text = text.replace("\u0007", " ");
        text = text.replace("\u0003", "");
        // 合并多余空格
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    /**
     * 统计字符出现次数
     */
    private int countOccurrences(String text, char ch) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ch) count++;
        }
        return count;
    }

    /**
     * 截断文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 解析结果内部类
     */
    public static class ParseResult {
        public List<DocumentSection> sections = new ArrayList<>();
        public List<ImageInfo> images = new ArrayList<>();
        public List<TableInfo> tables = new ArrayList<>();
        public Map<String, ImageInfo> imagePlaceholders = new LinkedHashMap<>();
    }
}