package com.washy.dify.rag.service;

import com.washy.dify.common.entity.llm.ChatRequestDTO;
import com.washy.dify.common.result.Result;
import com.washy.dify.feign.client.LlmFeignClient;
import com.washy.dify.rag.util.ChatRequestConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SmartTextChunker {

    @Resource
    private LlmFeignClient llmFeignClient;

    @Resource
    private ChatRequestConverter requestConverter;

    // 配置常量
    private static final int DEFAULT_MAX_CHUNK_SIZE = 2000;
    private static final int DEFAULT_OVERLAP_SIZE = 200;
    private static final int AI_MAX_CHUNK_SIZE = 3000;  // AI处理的块可以更大

    /**
     * 主入口：智能选择分块策略
     */
    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_MAX_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    public List<String> chunk(String text, int maxSize, int overlapSize) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 分析文本特征
        TextAnalysis analysis = analyzeText(text);
        log.info("文本分析: {}", analysis);

        // 2. 根据特征选择策略
        List<String> chunks;
        if (analysis.isHighlyStructured) {
            // 结构清晰：直接用规则
            chunks = chunkByStructure(text, maxSize);
        } else if (analysis.needsAI) {
            // 需要AI且AI可用
            chunks = chunkWithAI(text, maxSize);
        } else if (analysis.isSemiStructured) {
            // 半结构化：规则 + 段落合并
            chunks = chunkByParagraphs(text, maxSize, overlapSize);
        } else {
            // 无结构且无AI：降级为段落合并
            chunks = chunkByParagraphs(text, maxSize, overlapSize);
        }

        log.info("分块完成: {} 字 -> {} 块", text.length(), chunks.size());
        return chunks;
    }

    /**
     * 分析文本特征
     */
    private TextAnalysis analyzeText(String text) {
        TextAnalysis analysis = new TextAnalysis();
        
        // 检测标题模式
        Pattern headingPattern = Pattern.compile(
            "^第[一二三四五六七八九十\\d]+[章节条款]|^\\d+(\\.\\d+)*\\s+|^[一二三四五六七八九十]+[、，。]",
            Pattern.MULTILINE
        );
        long headingCount = countMatches(headingPattern, text);
        
        // 检测段落数量
        String[] paragraphs = text.split("\n\n|\n(?=[^\\s])");
        long paragraphCount = Arrays.stream(paragraphs)
            .filter(p -> !p.trim().isEmpty())
            .count();
        
        // 检测平均段落长度
        double avgParagraphLength = paragraphCount > 0 
            ? (double) text.length() / paragraphCount 
            : text.length();
        
        // 判断
        analysis.isHighlyStructured = headingCount >= 3 && avgParagraphLength < 500;
        analysis.isSemiStructured = headingCount >= 1 || avgParagraphLength < 1000;
        analysis.avgParagraphLength = avgParagraphLength;
        analysis.headingCount = headingCount;
        analysis.paragraphCount = paragraphCount;
        
        // 需要AI的场景：
        // 1. 段落很少但文本很长（长篇叙述）
        // 2. 段落很长（可能包含多个主题）
        analysis.needsAI = (paragraphCount < 5 && text.length() > 3000) 
                        || avgParagraphLength > 800;
        
        return analysis;
    }

    private long countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        long count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    // ==================== 策略1：结构化文本 ====================
    
    private List<String> chunkByStructure(String text, int maxSize) {
        List<String> chunks = new ArrayList<>();
        
        // 按标题模式分割
        String[] sections = text.split(
            "(?=(?:^|\\n)(?:第[一二三四五六七八九十\\d]+[章节条款]|\\d+(\\.\\d+)*\\s+|(?:[（(]?[一二三四五六七八九十]+[）)]?[、，。])))"
        );
        
        for (String section : sections) {
            if (section.trim().isEmpty()) continue;
            
            if (section.length() <= maxSize) {
                chunks.add(section.trim());
            } else {
                // 过长的节再按段落切
                chunks.addAll(chunkByParagraphs(section, maxSize, 100));
            }
        }
        
        return chunks;
    }

    // ==================== 策略2：段落合并（不用AI）====================
    
    private List<String> chunkByParagraphs(String text, int maxSize, int overlapSize) {
        String[] paragraphs = text.split("\n\n");
        List<String> nonEmpty = Arrays.stream(paragraphs)
            .map(String::trim)
            .filter(p -> !p.isEmpty())
            .collect(Collectors.toList());
        
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < nonEmpty.size(); i++) {
            String para = nonEmpty.get(i);
            
            if (current.length() + para.length() > maxSize && current.length() > 200) {
                chunks.add(current.toString().trim());
                
                // 重叠
                if (overlapSize > 0 && i > 0) {
                    String overlap = nonEmpty.get(i - 1);
                    if (overlap.length() > overlapSize) {
                        overlap = overlap.substring(overlap.length() - overlapSize);
                    }
                    current = new StringBuilder(overlap).append("\n\n");
                } else {
                    current = new StringBuilder();
                }
            }
            
            current.append(para).append("\n\n");
        }
        
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        
        return chunks;
    }

    // ==================== 策略3：AI辅助（需要时使用）====================
    
    private List<String> chunkWithAI(String text, int maxSize) {
        log.info("使用AI辅助分块...");
        
        // 1. 先用段落粗略分块
        List<String> rawChunks = chunkByParagraphs(text, maxSize * 2, 0);
        
        // 2. 对过长的块用AI找语义边界
        List<String> finalChunks = new ArrayList<>();
        
        for (String rawChunk : rawChunks) {
            if (rawChunk.length() <= maxSize) {
                finalChunks.add(rawChunk);
            } else {
                List<String> aiChunks = splitByAI(rawChunk, maxSize);
                finalChunks.addAll(aiChunks);
            }
        }
        
        return finalChunks;
    }
    
    private List<String> splitByAI(String text, int maxSize) {

        try {
            // 1. 构建用户 prompt
            String userPrompt = buildAISytemtemPrompt(maxSize);

            // 2. 构建 system prompt
            String systemPrompt = buildAIUserPrompt(text);
            // 3. 转换为请求DTO
            ChatRequestDTO request = requestConverter.toRequest(
                    systemPrompt,
                    userPrompt,
                    0.3,  // temperature
                    null  // configId，使用默认
            );


            Result<String> response = llmFeignClient.chat(request);
            String result = "";
            if(response.getCode() == 200){
                result = response.getData().trim();
            }
            return parseAIResponse(result);
        } catch (Exception e) {
            log.warn("AI分块失败，降级为规则分块: {}", e.getMessage());
            return fallbackSplit(text, maxSize);
        }
    }
    
    private String buildAISytemtemPrompt(int maxSize) {
        return String.format(
            "你是一个文本分割专家。请分析以下文本，在语义完整的地方分割。" +
            "每个分割后的段落不超过%d字。\n\n" +
            "规则：\n" +
            "1. 在主题转换处分割\n" +
            "2. 在段落自然结束处分割\n" +
            "3. 不要切断句子\n" +
            "4. 分割后的每段应该语义完整\n\n" +
            "输出格式：用 ===SPLIT=== 分隔每段，只输出分割后的文本，不要添加任何说明。",
            maxSize
        );
    }

    private String buildAIUserPrompt(String text) {
        return String.format("文本：\n%s",text.length() > 4000 ? text.substring(0, 4000) : text
        );
    }
    
    private List<String> parseAIResponse(String response) {
        return Arrays.stream(response.split("===SPLIT==="))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
    
    private List<String> fallbackSplit(String text, int maxSize) {
        List<String> chunks = new ArrayList<>();
        
        // 尝试在句号处切分
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxSize, text.length());
            
            // 回退到最近的句号
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf("。", end);
                int lastNewline = text.lastIndexOf("\n", end);
                int breakPoint = Math.max(lastPeriod, lastNewline);
                
                if (breakPoint > start + maxSize / 2) {
                    end = breakPoint + 1;
                }
            }
            
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        
        return chunks;
    }

    // ==================== 内部类 ====================
    
    private static class TextAnalysis {
        boolean isHighlyStructured;
        boolean isSemiStructured;
        boolean needsAI;
        double avgParagraphLength;
        long headingCount;
        long paragraphCount;
        
        @Override
        public String toString() {
            return String.format(
                "结构化=%s, 半结构化=%s, 需要AI=%s, 段落数=%d, 标题数=%d, 平均段落长度=%.0f",
                isHighlyStructured, isSemiStructured, needsAI,
                paragraphCount, headingCount, avgParagraphLength
            );
        }
    }
}