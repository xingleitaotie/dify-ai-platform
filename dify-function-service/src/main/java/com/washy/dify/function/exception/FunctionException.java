package com.washy.dify.function.exception;

import com.washy.dify.common.result.ResultCode;
import lombok.Getter;

/**
 * 函数调用业务异常
 * 优化点：精细化异常分类，区别于系统异常
 */
@Getter
public class FunctionException extends RuntimeException {

    private final Integer code;
    private final String message;

    public FunctionException(String message) {
        super(message);
        this.code = ResultCode.FAILED.getCode();
        this.message = message;
    }

    public FunctionException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public FunctionException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }
}