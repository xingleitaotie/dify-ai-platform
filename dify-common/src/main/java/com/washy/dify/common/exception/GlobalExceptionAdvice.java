package com.washy.dify.common.exception;

import com.washy.dify.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常捕获处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvice {

    /**
     * 捕获业务异常
     */
    @ExceptionHandler(GlobalExceptionHandler.class)
    public Result<?> handleGlobalException(GlobalExceptionHandler e) {
        log.error("业务异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    /**
     * 捕获其他所有异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("服务器繁忙，请稍后再试");
    }
}