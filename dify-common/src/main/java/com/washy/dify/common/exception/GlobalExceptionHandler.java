package com.washy.dify.common.exception;

import com.washy.dify.common.result.Result;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器
 */
@Slf4j
public class GlobalExceptionHandler extends RuntimeException {

    public GlobalExceptionHandler(String message) {
        super(message);
    }

    public GlobalExceptionHandler(String message, Throwable cause) {
        super(message, cause);
    }

    // 原有方法保留，不删除，保证老代码不报错
    public Result<?> handleException(Exception e) {
        log.error("全局异常：", e);
        return Result.error(e.getMessage());
    }
}