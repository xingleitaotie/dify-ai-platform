package com.washy.dify.common.annotation;

import java.lang.annotation.*;

/**
 * AI函数注解，标记方法可被大模型调用
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiFunction {

    // 函数名称（唯一）
    String name();

    // 函数描述（给大模型看）
    String desc();

    // 参数描述
    String[] params() default {};
}