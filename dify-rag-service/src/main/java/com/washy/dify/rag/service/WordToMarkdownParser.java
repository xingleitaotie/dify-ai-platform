package com.washy.dify.rag.service;

import com.washy.dify.rag.domain.DocumentSection;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class WordToMarkdownParser {
    // 缓存：文档分析后建立的样式->级别映射
    private Map<String, Integer> styleLevelMap = new HashMap<>();

    // 缓存：需要忽略的样式
    private Set<String> ignoreStyles = new HashSet<>();

    // 样式层级顺序（按出现顺序）
    private List<String> styleHierarchy = new ArrayList<>();

    /**
     * 解析Word文档 - 修复版
     * 核心改进：不仅识别标题，还要关联正文内容
     */
    public List<DocumentSection> parseViaMarkdown(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {

            // 第一步：分析文档样式
            analyzeDocumentStyles(document);

            // 第二步：解析文档结构（标题+内容）
            List<DocumentSection> sections = parseDocumentStructure(document);

            log.info("解析到 {} 个章节", sections.size());

            // 第三步：后处理 - 修正标题级别
            sections = fixHeadingLevels(sections);

            // 第四步：重新编号
            sections = renumberSections(sections);

            return sections;
        }
    }

    /**
     * 解析文档结构 - 关联标题和内容
     */
    private List<DocumentSection> parseDocumentStructure(XWPFDocument document) {

        List<DocumentSection> sections = new ArrayList<>();
        DocumentSection currentSection = null;

        // 图片计数
        int imageIndex = 0;

        List<IBodyElement> elements = document.getBodyElements();

        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;

                // === 新增：检查段落中的图片 ===
                List<XWPFPictureData> pictures = extractPicturesFromParagraph(paragraph);
                if (!pictures.isEmpty() && currentSection != null) {
                    for (XWPFPictureData picture : pictures) {
                        imageIndex++;
                        String placeholder = String.format("{{IMAGE_%04d}}", imageIndex);
                        currentSection.addText(placeholder);
                        log.debug("段落中发现图片，添加占位符: {}", placeholder);
                    }
                }
                String text = paragraph.getText();
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }

                text = text.trim();
                String styleId = paragraph.getStyle();

                // 判断是否为标题
                Integer headingLevel = getHeadingLevel(styleId, text, paragraph);

                if (headingLevel != null && headingLevel > 0) {
                    // 保存上一个章节
                    if (currentSection != null) {
                        sections.add(currentSection);
                    }

                    // 创建新章节
                    currentSection = new DocumentSection();
                    currentSection.setTitle(cleanTitle(text));
                    currentSection.setFullTitle(text);
                    currentSection.setLevel(headingLevel);

                    log.debug("识别标题: 样式={}, 级别={}, 文本={}",
                            styleId, headingLevel, text.substring(0, Math.min(30, text.length())));

                } else if (currentSection != null) {
                    // 正文内容，添加到当前章节
                    currentSection.addText(text);
                } else {
                    // 文档开头的正文（第一个标题之前的内容）
                    currentSection = new DocumentSection();
                    currentSection.setTitle("文档前言");
                    currentSection.setFullTitle("文档前言");
                    currentSection.setLevel(0);
                    currentSection.addText(text);
                }
            } else if (element instanceof XWPFTable && currentSection != null) {
                // 表格添加到当前章节
                XWPFTable table = (XWPFTable) element;
                String tableText = extractTableText(table);
                currentSection.addText(tableText);
            }
        }

        // 添加最后一个章节
        if (currentSection != null) {
            sections.add(currentSection);
        }

        return sections;
    }

    /**
     * 从段落中提取图片数据
     */
    private List<XWPFPictureData> extractPicturesFromParagraph(XWPFParagraph paragraph) {
        List<XWPFPictureData> pictures = new ArrayList<>();
        try {
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs != null) {
                for (XWPFRun run : runs) {
                    List<XWPFPicture> embeddedPictures = run.getEmbeddedPictures();
                    if (embeddedPictures != null) {
                        for (XWPFPicture pic : embeddedPictures) {
                            XWPFPictureData pictureData = pic.getPictureData();
                            if (pictureData != null) {
                                pictures.add(pictureData);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取段落图片失败: {}", e.getMessage());
        }
        return pictures;
    }

    /**
     * 获取标题级别 - 修复版
     */
    private Integer getHeadingLevel(String styleId, String text, XWPFParagraph paragraph) {
        if (text == null || text.trim().isEmpty()) return null;
        text = text.trim();

        // 1. 优先使用样式映射（从Word样式定义中识别）
        if (styleId != null && styleLevelMap.containsKey(styleId)) {
            return styleLevelMap.get(styleId);
        }

        // 2. 文本模式匹配（作为补充）
        if (text.matches("^第\\s*[一二三四五六七八九十\\d]+\\s*[章节条款].*")) {
            return 1;
        }

        // 3. 大纲级别
        try {
            CTPPr pPr = paragraph.getCTP().getPPr();
            if (pPr != null && pPr.getOutlineLvl() != null) {
                return pPr.getOutlineLvl().getVal().intValue() + 1;
            }
        } catch (Exception e) {}

        return null;
    }

    /**
     * 判断是否为粗体短文本（标题特征）
     */
    private boolean isBoldAndShort(XWPFParagraph paragraph, String text) {
        if (text.length() > 50) return false;

        List<XWPFRun> runs = paragraph.getRuns();
        if (runs != null && !runs.isEmpty()) {
            XWPFRun firstRun = runs.get(0);
            return firstRun.isBold();
        }
        return false;
    }

    /**
     * 修正标题级别 - 确保层级合理
     */
    private List<DocumentSection> fixHeadingLevels(List<DocumentSection> sections) {
        if (sections.isEmpty()) return sections;

        // 找出所有使用的级别
        Set<Integer> usedLevels = new TreeSet<>();
        for (DocumentSection section : sections) {
            if (section.getLevel() > 0) {
                usedLevels.add(section.getLevel());
            }
        }

        // 如果级别不连续（如1,3,4,5缺少2），重新映射
        List<Integer> sortedLevels = new ArrayList<>(usedLevels);

        if (sortedLevels.size() > 1) {
            // 检查是否连续
            boolean continuous = true;
            for (int i = 1; i < sortedLevels.size(); i++) {
                if (sortedLevels.get(i) != sortedLevels.get(i-1) + 1) {
                    continuous = false;
                    break;
                }
            }

            if (!continuous) {
                // 重新映射级别
                Map<Integer, Integer> levelRemap = new HashMap<>();
                for (int i = 0; i < sortedLevels.size(); i++) {
                    levelRemap.put(sortedLevels.get(i), i + 1);
                }

                log.info("级别重映射: {}", levelRemap);

                for (DocumentSection section : sections) {
                    Integer newLevel = levelRemap.get(section.getLevel());
                    if (newLevel != null) {
                        section.setLevel(newLevel);
                    }
                }
            }
        }

        return sections;
    }

    /**
     * 重新生成章节编号
     */
    private List<DocumentSection> renumberSections(List<DocumentSection> sections) {
        Map<Integer, Integer> counters = new TreeMap<>();

        for (DocumentSection section : sections) {
            int level = section.getLevel();

            if (level > 0) {
                // 增加当前级别计数
                counters.merge(level, 1, Integer::sum);
                // 清除更深级别
                counters.keySet().removeIf(l -> l > level);

                // 生成编号
                StringBuilder number = new StringBuilder();
                for (int l = 1; l <= level; l++) {
                    if (number.length() > 0) number.append(".");
                    number.append(counters.getOrDefault(l, 1));
                }

                section.setSectionNumber(number.toString());
                section.setFullTitle(number + " " + section.getTitle());
            }
        }

        return sections;
    }

    /**
     * 清理标题文本
     */
    private String cleanTitle(String text) {
        if (text == null) return "";
        text = text.replaceAll("^第\\s*[一二三四五六七八九十\\d]+\\s*[章节条款]\\s*", "");
        text = text.replaceAll("^[\\d.]+\\s+", "");
        text = text.replaceAll("^[一二三四五六七八九十]+\\s*[、，。]\\s*", "");
        return text.trim();
    }

    /**
     * 提取表格文本
     */
    private String extractTableText(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("[表格开始]\n");

        List<XWPFTableRow> rows = table.getRows();
        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            List<XWPFTableCell> cells = row.getTableCells();
            sb.append("| ");
            for (int j = 0; j < cells.size(); j++) {
                sb.append(cells.get(j).getText().trim());
                if (j < cells.size() - 1) sb.append(" | ");
            }
            sb.append(" |\n");

            // 表头后加分隔线
            if (i == 0 && rows.size() > 1) {
                sb.append("|");
                for (int j = 0; j < cells.size(); j++) {
                    sb.append(" --- |");
                }
                sb.append("\n");
            }
        }
        sb.append("[表格结束]");
        return sb.toString();
    }

    /**
     * 步骤0：分析文档样式，建立映射
     */
    private void analyzeDocumentStyles(XWPFDocument document) {
        styleLevelMap.clear();
        ignoreStyles.clear();

        log.info("开始分析文档样式（通过Word样式定义）...");

        try {
            XWPFStyles styles = document.getStyles();
            if (styles == null) {
                log.warn("文档没有样式定义，使用默认规则");
                return;
            }

            // 方法1：通过反射获取样式列表
            List<XWPFStyle> styleList = null;
            try {
                java.lang.reflect.Field field = XWPFStyles.class.getDeclaredField("listStyle");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<XWPFStyle> tempList = (List<XWPFStyle>) field.get(styles);
                styleList = tempList;
            } catch (Exception e) {
                log.warn("无法通过反射获取样式列表: {}", e.getMessage());
            }

            if (styleList == null || styleList.isEmpty()) {
                log.warn("样式列表为空");
                return;
            }

            log.info("文档共有 {} 个样式定义", styleList.size());

            for (XWPFStyle style : styleList) {
                String styleId = style.getStyleId();
                String styleName = style.getName();

                if (styleId == null) continue;

                log.debug("样式: id='{}', name='{}'", styleId, styleName);

                // 判断是否为标题样式
                int headingLevel = getHeadingLevelFromStyle(style);

                if (headingLevel > 0) {
                    styleLevelMap.put(styleId, headingLevel);
                    log.info("  标题样式: id='{}', name='{}' -> 级别 {}",
                            styleId, styleName, headingLevel);
                } else {
                    log.debug("  非标题样式: id='{}', name='{}'", styleId, styleName);
                }
            }

        } catch (Exception e) {
            log.error("分析样式失败", e);
        }

        log.info("最终样式映射: {}", styleLevelMap);

        // 如果仍然没有映射，使用备用方案
        if (styleLevelMap.isEmpty()) {
            log.warn("未通过样式识别到标题，使用备用文本匹配方案");
            fallbackAnalyze(document);
        }
    }
    /**
     * 从样式定义中判断标题级别
     * 这是最可靠的方法，直接读取Word样式中的标题设置
     */
    private int getHeadingLevelFromStyle(XWPFStyle style) {
        String styleId = style.getStyleId();
        String styleName = style.getName();

        // ===== 方法1：通过样式ID判断 =====

        // 英文标题样式：Heading 1, Heading 2...
        if (styleId != null) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(?i)^heading\\s*(\\d+)$"
            );
            java.util.regex.Matcher m = p.matcher(styleId);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
        }

        // 中文标题样式：标题 1, 标题 2...
        if (styleId != null) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "^标题\\s*(\\d+)$"
            );
            java.util.regex.Matcher m = p.matcher(styleId);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
        }

        // 中文标题样式：标题1, 标题2（无空格）...
        if (styleId != null) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "^标题(\\d+)$"
            );
            java.util.regex.Matcher m = p.matcher(styleId);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
        }

        // ===== 方法2：通过样式名称判断 =====
        if (styleName != null) {
            // "heading 1", "Heading 2"...
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(?i)^heading\\s*(\\d+)$"
            );
            java.util.regex.Matcher m = p.matcher(styleName);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }

            // "标题 1", "标题1"...
            p = java.util.regex.Pattern.compile("^标题\\s*(\\d+)$");
            m = p.matcher(styleName);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }

            // "1级标题", "2级标题"...
            p = java.util.regex.Pattern.compile("^(\\d+)级标题$");
            m = p.matcher(styleName);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
        }

        // ===== 方法3：通过底层CTStyle判断（如果有outlineLvl属性） =====
        try {
            // 使用反射获取CTStyle
            java.lang.reflect.Field ctStyleField = XWPFStyle.class.getDeclaredField("ctStyle");
            ctStyleField.setAccessible(true);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle ctStyle =
                    (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle) ctStyleField.get(style);

            if (ctStyle != null && ctStyle.getPPr() != null) {
                // 检查outlineLvl
                if (ctStyle.getPPr().getOutlineLvl() != null) {
                    int outlineLevel = ctStyle.getPPr().getOutlineLvl().getVal().intValue();
                    // 大纲级别 0-8 对应标题级别 1-9
                    return outlineLevel + 1;
                }
            }
        } catch (Exception e) {
            // 忽略，继续尝试其他方法
        }

        return -1; // 不是标题样式
    }

    /**
     * 备用方案：通过文本内容分析（当样式识别失败时使用）
     */
    private void fallbackAnalyze(XWPFDocument document) {
        log.info("使用备用方案分析文档...");

        Map<String, StyleInfo> styleInfoMap = new LinkedHashMap<>();

        List<IBodyElement> elements = document.getBodyElements();
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                String styleId = paragraph.getStyle();
                String text = paragraph.getText();

                if (text == null || text.trim().isEmpty()) continue;
                if (styleId == null) continue;

                text = text.trim();

                StyleInfo info = styleInfoMap.computeIfAbsent(styleId, k -> new StyleInfo());
                info.count++;

                // 匹配标题模式
                if (text.matches("^第\\s*[一二三四五六七八九十\\d]+\\s*[章节条款].*")) {
                    info.chapterPatternCount++;
                }
                if (text.matches("^\\d+(?:\\.\\d+)+\\s.*")) {
                    info.numberedPatternCount++;
                }
                if (text.length() < 30 && !text.contains("。") && !text.contains("，")) {
                    info.shortTextCount++;
                }
            }
        }

        // 分析各样式
        int level = 1;
        for (Map.Entry<String, StyleInfo> entry : styleInfoMap.entrySet()) {
            String styleId = entry.getKey();
            StyleInfo info = entry.getValue();

            // 判断是否为标题样式
            if (info.chapterPatternCount > 0 ||
                    (info.numberedPatternCount > 0 && info.count <= 30) ||
                    (info.shortTextCount == info.count && info.count <= 30)) {
                styleLevelMap.put(styleId, level);
                log.info("  备用识别: 样式'{}' -> 级别{} (章节模式:{}, 编号模式:{}, 短文本:{})",
                        styleId, level, info.chapterPatternCount,
                        info.numberedPatternCount, info.shortTextCount);
                level++;
            }
        }
    }

    /**
     * 样式信息统计
     */
    // 内部类：样式统计信息
    private static class StyleInfo {
        int count = 0;
        int chapterPatternCount = 0;
        int numberedPatternCount = 0;
        int shortTextCount = 0;
        int boldShortCount = 0;
    }

    /**
     * 判断是否为标题样式
     */
    private boolean isHeadingStyle(String styleId, StyleInfo info, String firstText) {
        // 1. 标准Heading样式
        if (styleId != null && styleId.matches("(?i)heading\\s*\\d+")) {
            return true;
        }
        if (styleId != null && styleId.matches("(?i)标题\\s*\\d+")) {
            return true;
        }

        // 2. 纯数字样式(2,3,4,5,6,7,8) — 您文档中的标题样式
        if (styleId != null && styleId.matches("^\\d+$")) {
            int num = Integer.parseInt(styleId);
            if (num >= 2 && num <= 9) {
                // 放宽条件：只要段落数合理，就认为是标题
                if (info.count >= 1) {
                    return true;
                }
            }
        }

        // 3. 首次文本匹配第X章模式
        if (firstText != null && firstText.matches("^第\\s*[一二三四五六七八九十\\d]+\\s*章.*")) {
            return true;
        }

        // 4. 样式名看起来像标题
        if (firstText != null && firstText.length() < 30 && !firstText.contains("。") && !firstText.contains("，")) {
            // 短文本且不是完整句子，可能是标题
            if (info.count >= 1 && info.count <= 30) { // 标题样式通常不会太多段落
                return true;
            }
        }

        return false;
    }

    /**
     * 从样式名中提取数字
     */
    private int extractStyleNumber(String styleId) {
        if (styleId == null) return 99;
        try {
            return Integer.parseInt(styleId.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 99;
        }
    }

    /**
     * 检查是否像标题
     */
    private boolean isTitleLike(String text) {
        if (text == null || text.isEmpty()) return false;
        // 短文本，且不以句号、逗号结尾
        return text.length() < 40 &&
                !text.endsWith("。") &&
                !text.endsWith("，") &&
                !text.endsWith("；");
    }

    /**
     * 转换为Markdown
     */
    private String convertToMarkdown(XWPFDocument document) {
        StringBuilder md = new StringBuilder();

        List<IBodyElement> elements = document.getBodyElements();
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                String line = convertParagraphToMarkdown(paragraph);
                if (!line.isEmpty()) {
                    md.append(line).append("\n\n");
                }
            } else if (element instanceof XWPFTable) {
                md.append("[表格]\n\n");
            }
        }

        return md.toString();
    }

    /**
     * 段落转Markdown
     */
    private String convertParagraphToMarkdown(XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        text = text.trim();
        String styleId = paragraph.getStyle();

        // 使用分析阶段建立的映射
        if (styleId != null && styleLevelMap.containsKey(styleId)) {
            int level = styleLevelMap.get(styleId);
            String prefix = String.join("", Collections.nCopies(level, "#"));
            log.debug("标题: 样式={}, 级别={}, 文本={}", styleId, level, text.substring(0, Math.min(30, text.length())));
            return prefix + " " + text;
        }

        // 文本模式匹配（作为备用）
        if (text.matches("^第\\s*[一二三四五六七八九十\\d]+\\s*[章节条款]")) {
            return "# " + text;
        }

        // 正文
        return text;
    }

    /**
     * 清洗Markdown标题
     */
    private String cleanMarkdownHeadings(String markdown) {
        String[] lines = markdown.split("\n");
        List<String> cleanedLines = new ArrayList<>();
        int lastHeadingLevel = 0;

        for (String line : lines) {
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }

                String titleText = line.substring(level).trim();

                // "第X章"强制设为1级
                if (titleText.matches("^第\\s*[一二三四五六七八九十\\d]+\\s*章.*")) {
                    level = 1;
                }

                // 确保层级不会跳跃太大（最多+1）
                if (level > lastHeadingLevel + 1) {
                    level = lastHeadingLevel + 1;
                }

                lastHeadingLevel = level;
                cleanedLines.add(String.join("", Collections.nCopies(level, "#")) + " " + titleText);
            } else {
                cleanedLines.add(line);
            }
        }

        return String.join("\n", cleanedLines);
    }

    /**
     * 解析Markdown结构
     */
    private List<DocumentSection> parseMarkdownStructure(String markdown) {
        List<DocumentSection> sections = new ArrayList<>();
        Map<Integer, Integer> counters = new TreeMap<>();

        String[] lines = markdown.split("\n");

        for (String line : lines) {
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }

                String title = line.substring(level).trim();

                // 生成编号
                counters.merge(level, 1, Integer::sum);
                int finalLevel = level;
                counters.keySet().removeIf(l -> l > finalLevel);

                StringBuilder number = new StringBuilder();
                for (int l = 1; l <= level; l++) {
                    if (number.length() > 0) number.append(".");
                    number.append(counters.getOrDefault(l, 1));
                }

                DocumentSection section = new DocumentSection();
                section.setLevel(level);
                section.setSectionNumber(number.toString());
                section.setTitle(title);
                section.setFullTitle(number + " " + title);
                sections.add(section);
            }
        }

        return sections;
    }



}