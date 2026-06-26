package com.washy.dify.common.exception;

import com.washy.dify.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常捕获处理器
 * <p>统一处理应用中抛出的各类异常，将异常转换为统一的响应格式返回给客户端。</p>
 * <p>异常处理策略：</p>
 * <ul>
 *   <li>{@link AppException} 及其子类：业务异常，直接返回错误信息</li>
 *   <li>{@link IllegalArgumentException}：参数错误，返回参数错误提示</li>
 *   <li>{@link Exception}：系统异常，记录日志并返回通用错误提示</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvice {

    /**
     * 捕获业务异常（基础异常类）
     * <p>处理 {@link AppException} 及其所有子类异常，包括：</p>
     * <ul>
     *   <li>{@link BusinessException}：通用业务异常</li>
     *   <li>{@link com.washy.dify.provider.exception.ModelProviderException}：模型提供者异常</li>
     *   <li>{@link com.washy.dify.llm.exception.LlmException}：LLM服务异常</li>
     *   <li>{@link com.washy.dify.prompt.exception.PromptException}：提示词模块异常</li>
     *   <li>{@link com.washy.dify.function.exception.FunctionException}：函数调用异常</li>
     * </ul>
     *
     * @param e 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(AppException.class)
    public Result<?> handleAppException(AppException e) {
        log.error("业务异常: code={}, message={}", e.getCode(), e.getMessage(), e);
        Result<Object> result = new Result<>();
        result.setCode(e.getCode());
        result.setMsg(e.getMessage());
        result.setData(null);
        return result;
    }


    /**
     * 捕获非法参数异常
     * <p>处理参数校验失败、非法输入等情况。</p>
     *
     * @param e 参数异常
     * @return 参数错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error("参数错误: " + e.getMessage());
    }

    /**
     * 捕获运行时异常
     * <p>处理未预期的运行时异常，如空指针、数组越界等。</p>
     *
     * @param e 运行时异常
     * @return 系统错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return Result.error("服务器繁忙，请稍后再试");
    }

    /**
     * 捕获其他所有未处理异常
     * <p>兜底处理，确保任何异常都能被捕获并返回统一格式的响应。</p>
     *
     * @param e 异常
     * @return 系统错误响应
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("服务器繁忙，请稍后再试");
    }
}