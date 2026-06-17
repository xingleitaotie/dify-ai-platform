package com.washy.dify.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.common.result.Result;
import com.washy.dify.common.result.ResultCode;
import com.washy.dify.gateway.config.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class AuthFilter implements GlobalFilter, Ordered {

    @Resource
    private ObjectMapper objectMapper;

    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/user/register",
            "/api/user/login",
            "/api/llm/chat",
            "/api/llm/stream/chat",
            "/api/rag/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        // 放行 OPTIONS 预检请求
        if (method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // 白名单直接放行（但依然注入匿名用户，保证下游 Header 不为空）
        if (isWhiteList(path)) {
            // ✅ 白名单路径也注入匿名用户，确保下游始终能拿到 X-User-Id
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", "anonymous")
                    .header("X-User-Name", "anonymous")
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // 非白名单：校验 Token
        String token = getTokenFromRequest(request);
        ServerHttpResponse response = exchange.getResponse();

        if (token == null || !JwtUtil.validateToken(token)) {
            return unauthorized(response);
        }

        // ✅ ===== 核心：Token 有效，解析用户信息并注入 Header =====
        try {
            Long userIdLong = JwtUtil.getUserId(token);
            String username = JwtUtil.getUsername(token);

            String userId = userIdLong != null ? userIdLong.toString() : "anonymous";
            String userName = (username != null && !username.isEmpty()) ? username : "anonymous";

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Name", userName)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.error("解析 Token 用户信息失败", e);
            return unauthorized(response);
        }
    }

    private String getTokenFromRequest(ServerHttpRequest request) {
        // 1. 尝试从 token 头获取
        String token = request.getHeaders().getFirst("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        // 2. 尝试从 Authorization 头获取
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 3. 尝试从 Query 参数获取（WebSocket 等场景）
        String queryToken = request.getQueryParams().getFirst("token");
        if (queryToken != null && !queryToken.isEmpty()) {
            return queryToken;
        }

        return null;
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        Result<Void> result = Result.failed(ResultCode.UNAUTHORIZED);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            bytes = new byte[0];
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private boolean isWhiteList(String path) {
        for (String white : WHITE_LIST) {
            if (white.endsWith("/**")) {
                String prefix = white.substring(0, white.length() - 3);
                if (path.startsWith(prefix)) {
                    return true;
                }
            } else if (white.equals(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}