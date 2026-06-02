package com.washy.dify.function.executor;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.washy.dify.common.entity.function.FunctionCallRequest;
import com.washy.dify.common.entity.function.FunctionConstant;
import com.washy.dify.common.entity.function.FunctionExecuteResult;
import com.washy.dify.common.entity.function.FunctionInfo;
import com.washy.dify.common.exception.GlobalExceptionHandler;
import com.washy.dify.function.exception.FunctionException;
import com.washy.dify.function.service.FunctionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 函数执行器（反射执行函数）
 * @author Day7
 */
/**
 * 函数执行器（反射执行函数）
 * @author Day7
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionExecutor {

    private final FunctionRegistry functionRegistry;

    /**
     * 执行函数调用
     */
    public FunctionExecuteResult execute(FunctionCallRequest callDTO) {
        FunctionExecuteResult result = new FunctionExecuteResult();
        long startTime = System.currentTimeMillis();
        String functionName = callDTO.getFunctionName();
        result.setFunctionName(functionName);

        try {
            log.info("=== 开始执行AI函数：{} ===", functionName);
            // 1. 获取函数元数据
            FunctionInfo functionInfo = functionRegistry.getFunctionInfo(functionName);

            // 2. 获取bean和方法
            Object bean = functionRegistry.getFunctionBean(functionName);
            Method targetMethod = functionRegistry.getFunctionMethod(functionName);

            // 3. 解析参数
            Object[] params = buildMethodParams(targetMethod, callDTO.getParameters());

            // 4. 反射执行方法
            Object executeResult = targetMethod.invoke(bean, params);

            // 5. 封装成功结果
            result.setSuccess(FunctionConstant.SUCCESS);
            result.setData(executeResult);
            result.setCostTime(System.currentTimeMillis() - startTime);
            log.info("函数[{}]执行成功，耗时：{}ms", functionName, result.getCostTime());

        } catch (FunctionException e) {
            // 业务异常（函数不存在、参数错误等）
            result.setSuccess(FunctionConstant.FAIL);
            result.setErrorMsg(e.getMessage());
            log.error("函数[{}]执行业务异常：{}", functionName, e.getMessage(), e);

        } catch (Exception e) {
            // 系统异常
            result.setSuccess(FunctionConstant.FAIL);
            result.setErrorMsg(FunctionConstant.EXECUTE_ERROR + e.getMessage());
            log.error("函数[{}]执行系统异常：{}", functionName, e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析参数
     */
    private Object[] parseParams(Method method, Object paramMap) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] params = new Object[paramTypes.length];

        try {
            // 统一转换Map参数
            for (int i = 0; i < paramTypes.length; i++) {
                params[i] = Convert.convert(paramTypes[i], paramMap);
            }
        } catch (Exception e) {
            throw new GlobalExceptionHandler(FunctionConstant.PARAM_ERROR + e.getMessage());
        }

        return params;
    }

    /**
     * 【真正安全版】构建方法参数
     * 规则：
     * 1. 无参 → 传空
     * 2. 单参 → 把AI返回的原始对象 直接传过去（Map/字符串/数组/数字 完全不变）
     * 3. 多参 → 按参数名从对象中提取
     */
    private Object[] buildMethodParams(Method method, Object originalParam) {
        Class<?>[] paramTypes = method.getParameterTypes();
        int paramCount = paramTypes.length;

        // 1. 无参
        if (paramCount == 0) {
            return new Object[0];
        }

        // 2. 单参 - 智能提取
        if (paramCount == 1) {
            try {
                Class<?> targetType = paramTypes[0];
                Object convertedValue = originalParam;

                // 特殊处理：目标类型是 String，但原始参数是 Map
                if (targetType == String.class && originalParam instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) originalParam;
                    // 尝试提取常见的字段名
                    String[] commonKeys = {"city", "query", "text", "content", "input"};
                    for (String key : commonKeys) {
                        if (map.containsKey(key)) {
                            convertedValue = String.valueOf(map.get(key));
                            log.info("从Map中提取参数值: key={}, value={}", key, convertedValue);
                            break;
                        }
                    }
                    // 如果都没有，取第一个值
                    if (convertedValue == originalParam && !map.isEmpty()) {
                        convertedValue = String.valueOf(map.values().iterator().next());
                        log.info("从Map中提取第一个值: {}", convertedValue);
                    }
                }
                // 特殊处理：目标类型是 String，但原始参数是 JSON 字符串
                else if (targetType == String.class && originalParam instanceof String) {
                    String str = (String) originalParam;
                    if (str.trim().startsWith("{") && str.contains("city")) {
                        // 尝试从 JSON 字符串中提取 city
                        try {
                            JSONObject json = JSON.parseObject(str);
                            if (json.containsKey("city")) {
                                convertedValue = json.getString("city");
                                log.info("从JSON字符串中提取city: {}", convertedValue);
                            }
                        } catch (Exception e) {
                            log.warn("解析JSON失败: {}", e.getMessage());
                        }
                    }
                }

                return new Object[]{Convert.convert(targetType, convertedValue)};
            } catch (Exception e) {
                throw new FunctionException(FunctionConstant.PARAM_ERROR + "参数转换失败：" + e.getMessage());
            }
        }

        // 3. 多参方法
        Object[] result = new Object[paramCount];
        try {
            for (int i = 0; i < paramCount; i++) {
                String paramName = method.getParameters()[i].getName();
                Object value = getValueFromParam(originalParam, paramName);
                result[i] = Convert.convert(paramTypes[i], value);
            }
        } catch (Exception e) {
            throw new FunctionException(FunctionConstant.PARAM_ERROR + "多参数解析失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 从参数中获取指定名称的值（兼容Map/JSON字符串/普通对象）
     */
    private Object getValueFromParam(Object param, String key) {
        if (param == null || StrUtil.isBlank(key)) {
            return null;
        }
        try {
            if (param instanceof Map) {
                return ((Map<?, ?>) param).get(key);
            }
            if (param instanceof String && JSONUtil.isTypeJSON((String) param)) {
                return JSONUtil.parseObj(param).get(key);
            }
        } catch (Exception ignored) {}
        return null;
    }
}