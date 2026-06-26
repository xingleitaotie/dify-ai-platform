package com.washy.dify.function.exception;

import com.washy.dify.common.exception.AppException;
import com.washy.dify.common.result.ResultCode;
import lombok.Getter;

/**
 * 函数调用业务异常类
 * <p>用于函数服务模块的业务异常，继承自 {@link AppException}，
 * 可被全局异常处理器 {@link com.washy.dify.common.exception.GlobalExceptionAdvice} 统一处理。</p>
 * <p>优化点：精细化异常分类，区别于系统异常，便于问题定位和处理。</p>
 */
@Getter
public class FunctionException extends AppException {

    /**
     * 构造函数：仅包含错误消息
     *
     * @param message 错误消息
     */
    public FunctionException(String message) {
        super(message);
    }

    /**
     * 构造函数：包含错误码和错误消息
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public FunctionException(Integer code, String message) {
        super(code, message);
    }

    /**
     * 构造函数：包含结果码
     *
     * @param resultCode 结果码
     */
    public FunctionException(ResultCode resultCode) {
        super(resultCode);
    }

    /**
     * 构造函数：包含错误消息和原始异常
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public FunctionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数：包含错误码、错误消息和原始异常
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   原始异常
     */
    public FunctionException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }
}