package com.washy.dify.provider.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 重试配置
 * @author washy
 */
@Configuration
@EnableRetry
public class RetryConfig {
}