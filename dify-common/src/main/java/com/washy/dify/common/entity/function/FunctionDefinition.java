package com.washy.dify.common.entity.function;

import lombok.Data;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 函数注册时使用（内部缓存，不返回前端）
 */
@Data
public class FunctionDefinition {
    private String name;
    private String desc;
    private List<String> params;
    private Object bean;       // Spring Bean
    private Method method;    // 反射方法
}