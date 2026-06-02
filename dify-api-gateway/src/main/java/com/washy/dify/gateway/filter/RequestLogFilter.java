package com.washy.dify.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全局请求日志过滤器
 */
@Component
@Slf4j
public class RequestLogFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 生成请求ID
        String requestId = UUID.randomUUID().toString().replace("-", "");
        ServerHttpRequest request = exchange.getRequest();

        // 打印请求日志
        log.info("==================== 请求开始 ====================");
        log.info("请求ID: {}", requestId);
        log.info("请求路径: {}", request.getPath());
        log.info("请求方法: {}", request.getMethod());
        log.info("请求地址: {}", request.getRemoteAddress());
        log.info("请求头: {}", request.getHeaders());

        // 传递请求ID
        exchange.getAttributes().put(REQUEST_ID, requestId);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            log.info("==================== 请求结束 ====================\n");
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}