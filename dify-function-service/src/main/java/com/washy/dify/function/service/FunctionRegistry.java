package com.washy.dify.function.service;

import cn.hutool.core.util.StrUtil;
import com.washy.dify.common.annotation.AiFunction;
import com.washy.dify.common.entity.function.FunctionInfo;
import com.washy.dify.common.result.ResultCode;
import com.washy.dify.function.exception.FunctionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 函数注册中心：项目启动时自动扫描所有@AiFunction方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionRegistry implements ApplicationListener<ContextRefreshedEvent> {

    /**
     * 函数元数据缓存：key=函数名，value=函数信息
     */
    private static final Map<String, FunctionInfo> FUNCTION_MAP = new ConcurrentHashMap<>();

    /**
     * 函数bean缓存
     */
    private static final Map<String, Object> FUNCTION_BEAN_MAP = new ConcurrentHashMap<>();

    /**
     * 函数方法缓存
     */
    private static final Map<String, Method> FUNCTION_METHOD_MAP = new ConcurrentHashMap<>();

    private ApplicationContext applicationContext;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        this.applicationContext = event.getApplicationContext();
        scanAiFunctions();
        log.info("=== AI函数注册完成，共注册函数：{}个 ===", FUNCTION_MAP.size());
    }

    /**
     * 扫描@AiFunction注解（方法级别）
     */
    private void scanAiFunctions() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();
                for (Method method : beanClass.getDeclaredMethods()) {
                    AiFunction aiFunction = AnnotationUtils.findAnnotation(method, AiFunction.class);
                    if (aiFunction == null) {
                        continue;
                    }

                    String functionName = aiFunction.name();
                    if (StrUtil.isBlank(functionName)) {
                        log.warn("函数名称不能为空，跳过注册：{}", method.getName());
                        continue;
                    }

                    // 重复函数判断
                    if (FUNCTION_MAP.containsKey(functionName)) {
                        log.warn("函数名称重复，已跳过：{}", functionName);
                        continue;
                    }

                    // 构建函数信息
                    FunctionInfo functionInfo = new FunctionInfo();
                    functionInfo.setName(aiFunction.name());
                    functionInfo.setDesc(aiFunction.desc());
                    functionInfo.setParamsSchema(aiFunction.params());  // 保存完整 Schema
                    List<String> paramNames = getMethodParamNames(method);
                    functionInfo.setParams(paramNames);

                    FUNCTION_MAP.put(functionName, functionInfo);
                    FUNCTION_BEAN_MAP.put(functionName, bean);
                    FUNCTION_METHOD_MAP.put(functionName, method);
                    log.info("注册AI函数：{} | {}", functionName, aiFunction.desc());
                }
            } catch (Exception e) {
                log.error("扫描Bean异常：{}", beanName, e);
            }
        }
    }

    /**
     * 获取大模型格式的函数列表（OpenAI Function Calling 标准格式）
     */
    public List<Map<String, Object>> getFunctionListForLLM() {
        List<Map<String, Object>> toolList = new ArrayList<>();

        for (FunctionInfo info : FUNCTION_MAP.values()) {
            Map<String, Object> tool = new HashMap<>();
            tool.put("type", "function");

            Map<String, Object> function = new HashMap<>();
            function.put("name", info.getName());
            function.put("description", info.getDesc());

            // 解析参数 Schema
            if (info.getParamsSchema() != null && !info.getParamsSchema().isEmpty()) {
                try {
                    com.alibaba.fastjson2.JSONObject paramsSchema =
                            com.alibaba.fastjson2.JSONObject.parseObject(info.getParamsSchema());
                    function.put("parameters", paramsSchema);
                } catch (Exception e) {
                    log.warn("解析参数 Schema 失败: {}", info.getName(), e);
                    function.put("parameters", createDefaultParameters(info.getParams()));
                }
            } else {
                function.put("parameters", createDefaultParameters(info.getParams()));
            }

            tool.put("function", function);
            toolList.add(tool);
        }

        return toolList;
    }

    /**
     * 创建默认参数定义
     */
    private Map<String, Object> createDefaultParameters(List<String> paramNames) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        for (String paramName : paramNames) {
            Map<String, Object> paramDef = new HashMap<>();
            paramDef.put("type", "string");
            paramDef.put("description", "参数：" + paramName);
            properties.put(paramName, paramDef);
            required.add(paramName);
        }

        parameters.put("properties", properties);
        parameters.put("required", required);

        return parameters;
    }

    /**
     * 获取方法参数名（兼容增强）
     */
    private List<String> getMethodParamNames(Method method) {
        List<String> paramNames = new ArrayList<>();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (java.lang.reflect.Parameter param : parameters) {
            if (StrUtil.isNotBlank(param.getName())) {
                paramNames.add(param.getName());
            }
        }
        return paramNames;
    }

    // ====================== 提供给外部调用的方法（带异常防护） ======================

    public FunctionInfo getFunctionInfo(String functionName) {
        FunctionInfo info = FUNCTION_MAP.get(functionName);
        if (info == null) {
            log.error("函数不存在：{}", functionName);
            throw new FunctionException(ResultCode.FUNCTION_NOT_FOUND);
        }
        return info;
    }

    public List<FunctionInfo> getFunctionList() {
        return new ArrayList<>(FUNCTION_MAP.values());
    }

    public Object getFunctionBean(String functionName) {
        Object bean = FUNCTION_BEAN_MAP.get(functionName);
        if (bean == null) {
            throw new FunctionException(ResultCode.FUNCTION_NOT_FOUND);
        }
        return bean;
    }

    public Method getFunctionMethod(String functionName) {
        Method method = FUNCTION_METHOD_MAP.get(functionName);
        if (method == null) {
            throw new FunctionException(ResultCode.FUNCTION_NOT_FOUND);
        }
        return method;
    }

    public Map<String, FunctionInfo> getFunctionMap() {
        return FUNCTION_MAP;
    }
}