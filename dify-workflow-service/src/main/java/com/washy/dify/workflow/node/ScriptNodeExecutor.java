package com.washy.dify.workflow.node;

import com.washy.dify.workflow.config.WorkflowContext;
import com.washy.dify.workflow.util.WorkflowVariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptNodeExecutor implements NodeExecutor {

    private final WorkflowVariableResolver resolver;

    @Override
    public String getNodeType() {
        return "CODE";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeInput, WorkflowContext context) {
        Map<String, Object> config = (Map<String, Object>) nodeInput.get("config");
        if (config == null) {
            throw new RuntimeException("代码节点配置不能为空");
        }

        String language = (String) config.getOrDefault("language", "js");
        if (!"js".equals(language) && !"javascript".equals(language)) {
            throw new RuntimeException("当前仅支持 JavaScript");
        }

        String code = (String) config.get("code");
        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("代码内容不能为空");
        }

        // ========== 解析输入变量（支持 config.inputs + 自动 Fallback） ==========
        List<Map<String, String>> inputs = (List<Map<String, String>>) config.get("inputs");
        Map<String, Object> inputMap = new HashMap<>();

        // 获取 runtime 数据（用于自动 Fallback）
        Map<String, Object> runtime = (Map<String, Object>) nodeInput.get("runtime");
        Map<String, Object> runtimeInput = runtime != null ? (Map<String, Object>) runtime.get("input") : null;
        Map<String, Object> runtimeVars = runtime != null ? (Map<String, Object>) runtime.get("vars") : null;

        if (inputs != null && !inputs.isEmpty()) {
            // 正常解析 config.inputs
            log.info("使用 config.inputs 解析输入变量，共 {} 个", inputs.size());
            for (Map<String, String> def : inputs) {
                String name = def.get("name");
                String valueExpr = def.get("value");
                if (name != null && !name.isEmpty()) {
                    Object resolved = resolver.resolveObject(valueExpr, context);
                    inputMap.put(name, resolved);
                }
            }
        } else {
            // ✅ Fallback：自动从 runtime 构建输入变量
            log.warn("代码节点未配置 inputs，自动从 runtime 构建输入");
            // 1. 从 runtime.input 中添加
            if (runtimeInput != null) {
                for (Map.Entry<String, Object> entry : runtimeInput.entrySet()) {
                    inputMap.put(entry.getKey(), entry.getValue());
                }
            }

            // 2. 从 runtime.vars 中添加（不覆盖已有键）
            if (runtimeVars != null) {
                for (Map.Entry<String, Object> entry : runtimeVars.entrySet()) {
                    if (!inputMap.containsKey(entry.getKey())) {
                        inputMap.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            log.info("自动构建的输入变量: {}", inputMap.keySet());
        }

        // 解析输出变量定义（用于校验）
        List<Map<String, String>> outputDefs = (List<Map<String, String>>) config.get("outputs");

        // 执行代码
        Map<String, Object> rawResult;
        try (Context polyglotContext = Context.newBuilder()
                .allowIO(false)
                .allowHostAccess(HostAccess.ALL)   // 允许使用 Java.from
                .allowHostClassLookup(className -> false) // 禁止任意类加载
                .allowExperimentalOptions(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {

            // 注入全局变量
            for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
                polyglotContext.getBindings("js").putMember(entry.getKey(), entry.getValue());
            }
            polyglotContext.getBindings("js").putMember("params", inputMap);

            log.info("【ScriptNodeExecutor】inputMap 内容: {}", inputMap);
            log.info("【ScriptNodeExecutor】inputMap.query 类型: {}", inputMap.get("query") != null ? inputMap.get("query").getClass() : "null");
            log.info("【ScriptNodeExecutor】inputMap.query 值: {}", inputMap.get("query"));

            // 执行代码
            polyglotContext.eval("js", code);
            Value mainFunc = polyglotContext.getBindings("js").getMember("main");
            if (mainFunc == null || !mainFunc.canExecute()) {
                throw new RuntimeException("未找到 main 函数");
            }

            Value resultValue = mainFunc.execute(inputMap);

            log.info("【ScriptNodeExecutor】resultValue 类型: {}", resultValue.getClass());
            log.info("【ScriptNodeExecutor】resultValue 内容: {}", resultValue);
            Map<String, Object> rawMap = convertGraalValueToJavaMap(resultValue);
            log.info("【ScriptNodeExecutor】rawMap: {}", rawMap);
            rawResult = sanitizeMap(rawMap);
            log.info("【ScriptNodeExecutor】rawResult: {}", rawResult);

        } catch (Exception e) {
            log.error("代码执行失败", e);
            throw new RuntimeException("代码执行失败: " + e.getMessage());
        }

        // 校验输出变量
        if (outputDefs != null && !outputDefs.isEmpty()) {
            Map<String, Object> validatedResult = new HashMap<>();
            for (Map<String, String> def : outputDefs) {
                String name = def.get("name");
                if (rawResult.containsKey(name)) {
                    validatedResult.put(name, rawResult.get(name));
                } else {
                    log.warn("输出变量 {} 未在代码返回值中找到", name);
                }
            }
            return validatedResult;
        }

        return rawResult;
    }

    private Object resolveVariableDirectly(String expression, WorkflowContext context) {
        if (expression == null || !expression.startsWith("{{") || !expression.endsWith("}}")) {
            return expression;
        }
        String inner = expression.substring(2, expression.length() - 2).trim();
        if (inner.startsWith("var.")) {
            String varName = inner.substring(4);
            return context.getVariables().get(varName);
        } else if (inner.startsWith("input.")) {
            String varName = inner.substring(6);
            return context.getInputs().get(varName);
        }
        return expression; // 保留原样
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> map) {
        if (map == null) return new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Value) {
                result.put(entry.getKey(), convertGraalValueToJavaObject((Value) value));
            } else if (value instanceof Map) {
                result.put(entry.getKey(), sanitizeMap((Map<String, Object>) value));
            } else if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                List<Object> newList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Value) {
                        newList.add(convertGraalValueToJavaObject((Value) item));
                    } else if (item instanceof Map) {
                        newList.add(sanitizeMap((Map<String, Object>) item));
                    } else {
                        newList.add(item);
                    }
                }
                result.put(entry.getKey(), newList);
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * 递归地将 GraalVM Value 转换为 Java Map 或 List（纯 Java 对象）
     */
    private Map<String, Object> convertGraalValueToJavaMap(Value value) {
        if (value == null || value.isNull()) {
            return new HashMap<>();
        }
        if (value.hasMember("values")) {
            // 如果是 JS 对象（Map 风格），转换为 Map
            Map<String, Object> result = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                Value member = value.getMember(key);
                // 递归转换每个成员
                Object converted = convertGraalValueToJavaObject(member);
                result.put(key, converted);
            }
            return result;
        } else if (value.isString()) {
            // 单个字符串，包装为 map
            Map<String, Object> map = new HashMap<>();
            map.put("result", value.asString());
            return map;
        } else if (value.isNumber() || value.isBoolean()) {
            Map<String, Object> map = new HashMap<>();
            map.put("result", value.as(Object.class));
            return map;
        } else {
            // 其他情况尝试 as Map
            try {
                return value.as(Map.class);
            } catch (Exception e) {
                // 若无法转换，返回空 Map
                return new HashMap<>();
            }
        }
    }

    private Object convertGraalValueToJavaObject(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.hasArrayElements()) {
            // 数组/List
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < value.getArraySize(); i++) {
                Object item = convertGraalValueToJavaObject(value.getArrayElement(i));
                list.add(item);
            }
            return list;
        } else if (value.hasMember("values")) {
            // 对象（Map）
            Map<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                Object val = convertGraalValueToJavaObject(value.getMember(key));
                map.put(key, val);
            }
            return map;
        } else if (value.isString()) {
            return value.asString();
        } else if (value.isNumber()) {
            return value.as(Number.class);
        } else if (value.isBoolean()) {
            return value.asBoolean();
        } else {
            // 尝试直接转
            try {
                return value.as(Object.class);
            } catch (Exception e) {
                return null;
            }
        }
    }
}