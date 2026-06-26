package com.washy.dify.prompt.exception;

import com.washy.dify.common.exception.AppException;
import com.washy.dify.common.result.ResultCode;

/**
 * 提示词模块异常类
 * <p>用于提示词引擎模块的业务异常，继承自 {@link AppException}，
 * 可被全局异常处理器 {@link com.washy.dify.common.exception.GlobalExceptionAdvice} 统一处理。</p>
 */
public class PromptException extends AppException {

    /**
     * 错误码（用于业务层面的错误标识）
     */
    private String errorCode;

    /**
     * 构造函数：仅包含错误消息
     *
     * @param message 错误消息
     */
    public PromptException(String message) {
        super(message);
    }

    /**
     * 构造函数：包含错误消息和原始异常
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public PromptException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数：包含业务错误码和错误消息
     *
     * @param errorCode 业务错误码
     * @param message   错误消息
     */
    public PromptException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造函数：包含业务错误码、错误消息和原始异常
     *
     * @param errorCode 业务错误码
     * @param message   错误消息
     * @param cause     原始异常
     */
    public PromptException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取业务错误码
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }
}