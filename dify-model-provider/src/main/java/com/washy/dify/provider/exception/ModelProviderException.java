package com.washy.dify.provider.exception;

import com.washy.dify.common.exception.AppException;

/**
 * 模型提供者异常类
 * <p>用于模型提供者服务模块的业务异常，继承自 {@link AppException}，
 * 可被全局异常处理器 {@link com.washy.dify.common.exception.GlobalExceptionAdvice} 统一处理。</p>
 * <p>主要用于封装模型调用、API请求、认证等相关的业务异常。</p>
 */
public class ModelProviderException extends AppException {

    /**
     * 构造函数：仅包含错误消息
     *
     * @param message 错误消息
     */
    public ModelProviderException(String message) {
        super(message);
    }

    /**
     * 构造函数：包含错误消息和原始异常
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public ModelProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数：包含错误码和错误消息
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public ModelProviderException(Integer code, String message) {
        super(code, message);
    }

    /**
     * 构造函数：包含错误码、错误消息和原始异常
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   原始异常
     */
    public ModelProviderException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }
}