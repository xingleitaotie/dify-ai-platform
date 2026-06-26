package com.washy.dify.llm.exception;

import com.washy.dify.common.exception.AppException;

/**
 * LLM服务异常类
 * <p>用于LLM服务模块的业务异常，继承自 {@link AppException}，
 * 可被全局异常处理器 {@link com.washy.dify.common.exception.GlobalExceptionAdvice} 统一处理。</p>
 * <p>主要用于封装大语言模型调用、流式响应、会话管理等相关的业务异常。</p>
 */
public class LlmException extends AppException {

    /**
     * 构造函数：仅包含错误消息
     *
     * @param message 错误消息
     */
    public LlmException(String message) {
        super(message);
    }

    /**
     * 构造函数：包含错误消息和原始异常
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数：包含错误码和错误消息
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public LlmException(Integer code, String message) {
        super(code, message);
    }

    /**
     * 构造函数：包含错误码、错误消息和原始异常
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   原始异常
     */
    public LlmException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }
}