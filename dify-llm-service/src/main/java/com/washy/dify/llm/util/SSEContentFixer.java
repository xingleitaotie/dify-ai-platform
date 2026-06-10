package com.washy.dify.llm.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * SSE 内容修复工具类
 */
public class SSEContentFixer {
    
    /**
     * 修复 SSE 流式传输导致的代码碎片化
     */
    public static String fixFragmentedContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // 1. 修复代码块标记
        content = fixCodeBlockMarkers(content);
        
        // 2. 修复代码块内的碎片化
        content = fixCodeBlockContent(content);
        
        // 3. 修复文本内容
        content = fixTextContent(content);
        
        // 4. 最终格式整理
        content = finalFormatFix(content);
        
        return content;
    }
    
    /**
     * 修复代码块标记
     */
    private static String fixCodeBlockMarkers(String content) {
        // 修复被拆散的 ``` 符号
        content = content.replaceAll("`\\s*``\\s*(\\w*)", "```$1");
        content = content.replaceAll("``\\s*`\\s*(\\w*)", "```$1");
        content = content.replaceAll("```\\s*(\\w+)\\s*", "```$1\n");
        
        // 确保代码块标记前后有换行
        content = content.replaceAll("([^\\n])```", "$1\n```");
        content = content.replaceAll("```(\\w+)([^\\n])", "```$1\n$2");
        
        return content;
    }
    
    /**
     * 修复代码块内容
     */
    private static String fixCodeBlockContent(String content) {
        // 使用正则表达式找到所有代码块
        Pattern pattern = Pattern.compile("```(\\w+)\\n([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String language = matcher.group(1);
            String code = matcher.group(2);
            
            // 修复代码块内的常见问题
            String fixedCode = fixPythonCode(code);
            
            // 替换为修复后的代码
            matcher.appendReplacement(sb, "```" + language + "\n" + fixedCode + "\n```");
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * 修复 Python 代码的常见碎片问题
     */
    private static String fixPythonCode(String code) {
        if (!code.contains("import") && !code.contains("def") && !code.contains("class")) {
            return code;
        }
        
        // 修复 import 语句
        code = code.replaceAll("import\\s+(\\w+)(\\d+)\\s+import\\s+(\\w+)", 
            "import $1\nfrom bs$2 import $3");
        code = code.replaceAll("(\\w+)import\\s+(\\w+)", "$1\nimport $2");
        
        // 修复方法调用断行
        code = code.replaceAll("\\.\\s*\\n\\s*(\\w+)\\s*\\(", ".$1(");
        code = code.replaceAll("\\.\\s*\\n\\s*(\\w+)", ".$1");
        
        // 修复字符串断行
        code = code.replaceAll("([\"'])\\s*\\n\\s*([^\"']*[\"'])", "$1$2");
        
        // 修复变量赋值断行
        code = code.replaceAll("(\\w+)\\s*=\\s*\\n\\s*", "$1 = ");
        
        // 修复注释断行
        code = code.replaceAll("#\\s*\\n\\s*([^#\\n]+)", "# $1");
        
        // 修复 URL 断行
        code = code.replaceAll("(\\w+)\\.\\s*\\n\\s*(\\w+)\\.", "$1.$2.");
        
        // 清理多余的空行
        code = code.replaceAll("\\n{3,}", "\n\n");
        code = code.replaceAll("[ \\t]+$", "");
        
        return code.trim();
    }
    
    /**
     * 修复文本内容
     */
    private static String fixTextContent(String content) {
        // 修复被拆散的 Markdown 语法
        content = content.replaceAll("\\*\\*\\s*\\n\\s*", "**");
        content = content.replaceAll("\\n\\s*\\*\\*", "**");
        content = content.replaceAll("`\\s*\\n\\s*", "`");
        content = content.replaceAll("\\n\\s*`", "`");
        
        // 修复标点符号后的异常换行
        content = content.replaceAll("([。，；：])\\s*\\n\\s*", "$1");
        content = content.replaceAll("([.!?])\\s*\\n\\s*", "$1 ");
        
        return content;
    }
    
    /**
     * 最终格式修复
     */
    private static String finalFormatFix(String content) {
        // 确保代码块格式正确
        content = content.replaceAll("```(\\w+)\\s*\\n", "\n\n```$1\n");
        content = content.replaceAll("```\\s*\\n", "\n```\n");
        
        // 确保代码块前后有适当的空行
        content = content.replaceAll("([^\\n])```", "$1\n\n```");
        
        // 清理多余的空行
        content = content.replaceAll("\\n{3,}", "\n\n");
        
        return content;
    }
}