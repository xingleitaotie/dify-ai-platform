package com.washy.dify.prompt.templates.summary;

import com.washy.dify.prompt.core.ModelParams;
import com.washy.dify.prompt.core.PromptTemplate;
import com.washy.dify.prompt.util.ContentTruncator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文档摘要生成提示词模板
 */
@Component
public class DocumentSummaryPromptTemplate implements PromptTemplate {
    
    private static final String TEMPLATE = 
        "<|im_start|>system\n" +
        "你是文档摘要助手。请对以下文本生成30-50字的摘要。\n\n" +
        "要求：\n" +
        "1. 只输出摘要，不要有任何前缀如\"摘要：\"或\"总结：\"\n" +
        "2. 字数严格控制30-50字\n" +
        "3. 提取核心主题和关键信息\n" +
        "4. 不要评价内容好坏\n\n" +
        "示例：\n" +
        "输入：\"Java是一种面向对象的编程语言，由Sun Microsystems于1995年推出。它具有跨平台特性，通过JVM实现'一次编写，到处运行'。\"\n" +
        "输出：Java是一种面向对象的跨平台编程语言，1995年由Sun推出，通过JVM实现一次编写到处运行。\n" +
        "<|im_end|>\n" +
        "<|im_start|>user\n" +
        "第${chunkIndex}/${totalChunks}块内容：\n" +
        "${content}\n" +
        "<|im_end|>\n" +
        "<|im_start|>assistant\n";
    
    @Override
    public String getName() {
        return "document.summary";
    }
    
    @Override
    public String getVersion() {
        return "v2.1.0";
    }
    
    @Override
    public String getDescription() {
        return "文档块摘要生成模板，用于RAG系统中的文档分块摘要";
    }
    
    @Override
    public String render(Map<String, Object> context) {
        String content = (String) context.getOrDefault("content", "");
        Integer chunkIndex = (Integer) context.getOrDefault("chunkIndex", 0);
        Integer totalChunks = (Integer) context.getOrDefault("totalChunks", 1);
        
        // 截断内容到合理长度
        String truncatedContent = ContentTruncator.truncate(content, 800);
        
        return TEMPLATE
            .replace("${chunkIndex}", String.valueOf(chunkIndex))
            .replace("${totalChunks}", String.valueOf(totalChunks))
            .replace("${content}", truncatedContent);
    }
    
    @Override
    public ModelParams getModelParams() {
        return ModelParams.forSummary();
    }
    
    @Override
    public TemplateType getType() {
        return TemplateType.SUMMARY;
    }
}