package com.washy.dify.common.entity.function;

import lombok.Data;

/**
 * 函数调用请求DTO（全局通用）
 */
@Data
public class FunctionCallRequest {
    // 函数名
    private String functionName;

    // 调用参数
    private Object parameters;

}