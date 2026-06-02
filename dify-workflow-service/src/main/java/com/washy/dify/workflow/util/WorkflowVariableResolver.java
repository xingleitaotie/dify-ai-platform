package com.washy.dify.workflow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.workflow.config.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流变量解析器
 * 支持复杂数据类型的变量替换
 */
@Slf4j
@Component
public class WorkflowVariableResolver {

    // 修改：支持嵌套路径，如 {{var.llm_output}} 或 {{var.llm_output.content}}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\.([\\w.]+)\\}\\}");
    private static final Pattern SIMPLE_VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析字符串中的变量，保持原始类型
     */
    public Object resolve(String text, WorkflowContext context) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        log.debug("解析变量 - 原始文本: {}", text);

        // 如果不包含变量语法，直接返回原文本
        if (!text.contains("{{")) {
            return text;
        }

        // 检查整个字符串是否就是一个嵌套变量引用 {{scope.path}}
        Matcher fullMatcher = VARIABLE_PATTERN.matcher(text);
        if (fullMatcher.matches() && text.equals(fullMatcher.group(0))) {
            String scope = fullMatcher.group(1);
            String path = fullMatcher.group(2);  // 现在支持点号路径
            return getVariableValueByPath(scope, path, context);
        }

        // 检查简单变量格式 {{xxx}}
        Matcher simpleMatcher = SIMPLE_VARIABLE_PATTERN.matcher(text);
        if (simpleMatcher.matches() && text.equals(simpleMatcher.group(0))) {
            String varName = simpleMatcher.group(1);
            Object value = context.getVariable(varName);
            if (value == null) {
                value = context.getInputs().get(varName);
            }
            return value != null ? value : "";
        }

        // 字符串中有多个变量，进行字符串替换
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            String scope = matcher.group(1);
            String path = matcher.group(2);
            Object value = getVariableValueByPath(scope, path, context);
            String stringValue = convertToString(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(stringValue));
        }
        matcher.appendTail(result);

        // 处理简单变量
        Matcher simpleMatcher2 = SIMPLE_VARIABLE_PATTERN.matcher(result.toString());
        StringBuffer finalResult = new StringBuffer();
        while (simpleMatcher2.find()) {
            String varName = simpleMatcher2.group(1);
            Object value = context.getVariable(varName);
            if (value == null) {
                value = context.getInputs().get(varName);
            }
            String stringValue = convertToString(value);
            simpleMatcher2.appendReplacement(finalResult, Matcher.quoteReplacement(stringValue));
        }
        simpleMatcher2.appendTail(finalResult);

        // 尝试将结果解析为 JSON
        return tryParseJson(finalResult.toString());
    }

    /**
     * 根据路径获取变量值（支持嵌套路径）
     * @param scope input 或 var
     * @param path 路径，支持点号分隔，如 "llm_output" 或 "llm_output.content"
     */
    private Object getVariableValueByPath(String scope, String path, WorkflowContext context) {
        Map<String, Object> sourceMap;

        if ("input".equals(scope)) {
            sourceMap = context.getInputs();
        } else if ("var".equals(scope)) {
            sourceMap = context.getVariables();
        } else {
            log.warn("未知的作用域: {}", scope);
            return null;
        }

        if (sourceMap == null) {
            return null;
        }

        // 按点号分割路径
        String[] parts = path.split("\\.");
        Object current = sourceMap.get(parts[0]);

        // 逐级访问嵌套属性
        for (int i = 1; i < parts.length && current != null; i++) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(parts[i]);
            } else {
                log.debug("无法继续解析路径 {}，当前值类型: {}", path, current.getClass().getSimpleName());
                return null;
            }
        }

        log.debug("路径解析: scope={}, path={}, value={}", scope, path, current);
        return current;
    }

    /**
     * 解析整数配置（支持变量）
     */
    public Integer resolveInteger(Object config, WorkflowContext context, Integer defaultValue) {
        if (config == null) return defaultValue;

        Object resolved = config;
        if (config instanceof String) {
            resolved = resolve((String) config, context);
        }

        if (resolved instanceof Number) {
            return ((Number) resolved).intValue();
        }
        if (resolved instanceof String) {
            try {
                return Integer.parseInt((String) resolved);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 解析Double配置（支持变量）
     */
    public Double resolveDouble(Object config, WorkflowContext context, Double defaultValue) {
        if (config == null) return defaultValue;

        Object resolved = config;
        if (config instanceof String) {
            resolved = resolve((String) config, context);
        }

        if (resolved instanceof Number) {
            return ((Number) resolved).doubleValue();
        }
        if (resolved instanceof String) {
            try {
                return Double.parseDouble((String) resolved);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 解析Map中的所有变量
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveMap(Map<String, Object> map, WorkflowContext context) {
        if (map == null) return new HashMap<>();

        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), resolveObject(entry.getValue(), context));
        }
        return result;
    }

    /**
     * 解析List中的所有变量
     */
    @SuppressWarnings("unchecked")
    public List<Object> resolveList(List<?> list, WorkflowContext context) {
        if (list == null) return new ArrayList<>();

        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            result.add(resolveObject(item, context));
        }
        return result;
    }

    /**
     * 递归解析对象
     */
    @SuppressWarnings("unchecked")
    public Object resolveObject(Object obj, WorkflowContext context) {
        if (obj == null) return null;
        if (obj instanceof String) {
            return resolve((String) obj, context);
        }
        if (obj instanceof Map) {
            return resolveMap((Map<String, Object>) obj, context);
        }
        if (obj instanceof List) {
            return resolveList((List<?>) obj, context);
        }
        return obj;
    }

    /**
     * 将对象转换为字符串
     */
    private String convertToString(Object value) {
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                log.warn("转换对象为JSON失败", e);
                return value.toString();
            }
        }
        return value.toString();
    }

    /**
     * 尝试解析JSON字符串
     */
    private Object tryParseJson(String str) {
        if (str == null || str.isEmpty()) return str;

        str = str.trim();
        if ((str.startsWith("{") && str.endsWith("}")) || (str.startsWith("[") && str.endsWith("]"))) {
            try {
                JsonNode node = objectMapper.readTree(str);
                return parseJsonNode(node);
            } catch (JsonProcessingException e) {
                return str;
            }
        }
        return str;
    }

    /**
     * 递归解析JSON节点
     */
    private Object parseJsonNode(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isTextual()) return node.asText();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(parseJsonNode(item));
            }
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(entry -> {
                map.put(entry.getKey(), parseJsonNode(entry.getValue()));
            });
            return map;
        }
        return node.toString();
    }
}