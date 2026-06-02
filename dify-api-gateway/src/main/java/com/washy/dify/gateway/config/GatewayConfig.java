package com.washy.dify.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关路由配置
 */
@Configuration
public class GatewayConfig {
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("dify-llm-service", r -> r.path("/api/llm/**")
                        .uri("lb://dify-llm-service"))

                .route("dify-rag-service", r -> r.path("/api/rag/**")
                        .uri("lb://dify-rag-service"))

                .route("dify-function-service", r -> r.path("/api/function/**")
                        .uri("lb://dify-function-service"))

                .route("dify-agent-service", r -> r.path("/api/agent/**")
                        .uri("lb://dify-agent-service"))

                .route("dify-prompt-engine", r -> r.path("/api/prompt/**")
                        .uri("lb://dify-prompt-engine"))

                .route("dify-user-service", r -> r.path("/api/user/**")
                        .uri("lb://dify-user-service"))

                .route("dify-workflow-service", r -> r.path("/api/workflow/**")
                        .uri("lb://dify-workflow-service"))
                .build();
    }
}