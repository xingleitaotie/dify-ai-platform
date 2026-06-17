package com.washy.dify.common.entity.function;

/**
 * 函数常量
 * @author Day7
 */
public class FunctionConstant {

    private FunctionConstant() {}

    /**
     * 函数执行状态
     */
    public static final Boolean SUCCESS = true;
    public static final Boolean FAIL = false;

    public static final Integer DEFAULT_EXECUTE_TIMEOUT = 100;

    /**
     * 错误信息
     */
    public static final String EXECUTE_ERROR = "函数执行失败：";

    public static final String FUNCTION_NOT_FOUND = "函数不存在：";
    public static final String PARAM_ERROR = "参数解析错误：";
}