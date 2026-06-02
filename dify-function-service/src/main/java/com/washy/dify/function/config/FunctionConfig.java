package com.washy.dify.function.config;

import com.washy.dify.common.entity.function.FunctionConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 函数调用配置中心
 * 优化点：配置化管理、异步执行池、超时控制
 */
@Configuration
public class FunctionConfig {

    /**
     * 函数执行线程池
     * 优化点：隔离函数执行，防止阻塞主线程，控制并发
     */
    @Bean(name = "functionExecutorPool")
    public Executor functionExecutorPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("function-executor-");
        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 函数执行超时时间（单位：秒）
     */
    @Bean
    public Integer functionExecuteTimeout() {
        return FunctionConstant.DEFAULT_EXECUTE_TIMEOUT;
    }
}