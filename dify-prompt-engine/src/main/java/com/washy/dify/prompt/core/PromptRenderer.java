package com.washy.dify.prompt.core;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词渲染器
 */
public class PromptRenderer {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    /**
     * 渲染模板字符串
     * @param template 模板
     * @param context 上下文变量
     * @return 渲染结果
     */
    public static String render(String template, Map<String, Object> context) {
        if (template == null || context == null) {
            return template;
        }
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = getNestedValue(context, varName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 获取嵌套属性值 (支持 a.b.c 格式)
     */
    private static Object getNestedValue(Map<String, Object> context, String path) {
        String[] parts = path.split("\\.");
        Object current = context;
        
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}