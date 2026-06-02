package com.washy.dify.common.entity.function;

import lombok.Data;

/**
 * 函数执行结果DTO
 * @author Day7
 */
@Data
public class FunctionExecuteResult {

    /**
     * 执行是否成功
     */
    private Boolean success;

    /**
     * 函数名称
     */
    private String functionName;

    /**
     * 执行结果数据
     */
    private Object data;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 耗时
     */
    private Long costTime;
}