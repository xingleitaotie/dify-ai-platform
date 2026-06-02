package com.washy.dify.rag.service;

import com.washy.dify.rag.domain.DocumentSection;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PdfDocumentParser {

    // 标题识别模式
    private static final Pattern CHAPTER_PATTERN = 
        Pattern.compile("^第\\s*[一二三四五六七八九十\\d]+\\s*[章节条款]\\s*(.*)");
    private static final Pattern NUMBERED_PATTERN = 
        Pattern.compile("^(\\d+(?:\\.\\d+)*)\\s+(.*)");
    private static final Pattern CHINESE_LIST_PATTERN = 
        Pattern.compile("^([一二三四五六七八九十]+)\\s*[、，。]\\s*(.*)");
    private static final Pattern BRACKET_PATTERN = 
        Pattern.compile("^[（(]([一二三四五六七八九十\\d]+)[）)]\\s*(.*)");

    /**
     * 解析 PDF 文档，返回章节列表
     */
    public List<DocumentSection> parse(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            // 提取文本
            String text = extractText(document);
            
            // 解析结构
            return parseStructure(text);
        }
    }

    /**
     * 从 PDF 中提取纯文本（按行）
     */
    private String extractText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setAddMoreFormatting(false);
        return stripper.getText(document);
    }

    /**
     * 解析文本结构，识别标题和内容
     */
    private List<DocumentSection> parseStructure(String text) {
        List<DocumentSection> sections = new ArrayList<>();
        DocumentSection currentSection = null;

        String[] lines = text.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 判断是否为标题
            HeadingInfo heading = detectHeading(trimmed);

            if (heading != null) {
                // 保存上一个章节
                if (currentSection != null) {
                    sections.add(currentSection);
                }
                // 创建新章节
                currentSection = new DocumentSection();
                currentSection.setTitle(heading.title);
                currentSection.setFullTitle(trimmed);
                currentSection.setLevel(heading.level);
                currentSection.setSectionNumber(heading.number);
            } else if (currentSection != null) {
                // 正文内容
                currentSection.addText(trimmed);
            } else {
                // 文档开头的正文
                currentSection = new DocumentSection();
                currentSection.setTitle("文档前言");
                currentSection.setLevel(1);
                currentSection.setSectionNumber("0");
                currentSection.addText(trimmed);
            }
        }

        // 保存最后一个章节
        if (currentSection != null) {
            sections.add(currentSection);
        }

        // 重新编号
        renumberSections(sections);

        log.info("PDF 解析完成：{} 个章节", sections.size());
        return sections;
    }

    /**
     * 检测标题
     */
    private HeadingInfo detectHeading(String line) {
        Matcher m;
        
        // 第X章 / 第X节
        m = CHAPTER_PATTERN.matcher(line);
        if (m.matches()) {
            HeadingInfo info = new HeadingInfo();
            info.level = line.contains("节") ? 2 : 1;
            info.title = m.group(1).trim();
            info.number = extractChapterNumber(line);
            return info;
        }

        // 数字编号：1.1 / 1.1.1
        m = NUMBERED_PATTERN.matcher(line);
        if (m.matches() && isLikelyHeading(line)) {
            HeadingInfo info = new HeadingInfo();
            info.level = m.group(1).split("\\.").length;
            info.title = m.group(2).trim();
            info.number = m.group(1);
            return info;
        }

        // 中文列表：一、 / 二、
        m = CHINESE_LIST_PATTERN.matcher(line);
        if (m.matches() && isLikelyHeading(line)) {
            HeadingInfo info = new HeadingInfo();
            info.level = 1;
            info.title = m.group(2).trim();
            info.number = chineseToNumber(m.group(1));
            return info;
        }

        // 括号编号：(一) / (1)
        m = BRACKET_PATTERN.matcher(line);
        if (m.matches() && isLikelyHeading(line)) {
            HeadingInfo info = new HeadingInfo();
            info.level = 2;
            info.title = m.group(2).trim();
            String num = m.group(1);
            info.number = num.matches("\\d+") ? num : chineseToNumber(num);
            return info;
        }

        return null;
    }

    /**
     * 判断是否可能是标题（短文本、不含句号）
     */
    private boolean isLikelyHeading(String line) {
        return line.length() < 50 && !line.contains("。") && !line.contains("，") && !line.contains("；");
    }

    /**
     * 提取章节数字编号
     */
    private String extractChapterNumber(String line) {
        Pattern p = Pattern.compile("第\\s*([一二三四五六七八九十\\d]+)\\s*[章节条款]");
        Matcher m = p.matcher(line);
        if (m.find()) {
            String num = m.group(1);
            return num.matches("\\d+") ? num : chineseToNumber(num);
        }
        return "1";
    }

    /**
     * 中文数字转阿拉伯数字
     */
    private String chineseToNumber(String chinese) {
        Map<Character, Integer> map = new HashMap<>();
        map.put('一', 1); map.put('二', 2); map.put('三', 3);
        map.put('四', 4); map.put('五', 5); map.put('六', 6);
        map.put('七', 7); map.put('八', 8); map.put('九', 9);
        map.put('十', 10);

        if (chinese.length() == 1 && map.containsKey(chinese.charAt(0))) {
            return String.valueOf(map.get(chinese.charAt(0)));
        }
        if (chinese.equals("十")) return "10";
        if (chinese.startsWith("十")) return "1" + chinese.substring(1);
        if (chinese.endsWith("十")) return chinese.charAt(0) + "0";
        
        return chinese;
    }

    /**
     * 重新编号章节
     */
    private void renumberSections(List<DocumentSection> sections) {
        Map<Integer, Integer> counters = new TreeMap<>();

        for (DocumentSection section : sections) {
            int level = section.getLevel();
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

    private static class HeadingInfo {
        int level;
        String title;
        String number;
    }
}