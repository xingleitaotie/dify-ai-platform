package com.washy.dify.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.washy.dify.common.result.Result;
import com.washy.dify.common.result.ResultCode;
import com.washy.dify.gateway.config.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AuthFilter implements GlobalFilter, Ordered {

    @Resource
    private ObjectMapper objectMapper;

    @Value("${auth.whitelist:/api/user/register,/api/user/login}")
    private String whitelistConfig;

    private List<String> whiteList;

    @PostConstruct
    public void init() {
        whiteList = Arrays.asList(whitelistConfig.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        log.info("认证白名单配置: {}", whiteList);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        if (isWhiteList(path)) {
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", "anonymous")
                    .header("X-User-Name", "anonymous")
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        String token = getTokenFromRequest(request);
        ServerHttpResponse response = exchange.getResponse();

        if (token == null || !JwtUtil.validateToken(token)) {
            return unauthorized(response);
        }

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
        String token = request.getHeaders().getFirst("token");
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        String queryToken = request.getQueryParams().getFirst("token");
        if (queryToken != null && !queryToken.trim().isEmpty()) {
            log.warn("Token 通过 URL 参数传递，建议使用 Authorization 头");
            return queryToken.trim();
        }

        return null;
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        response.getHeaders().add("Cache-Control", "no-cache");

        Result<Void> result = Result.failed(ResultCode.UNAUTHORIZED);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\":401,\"msg\":\"未授权\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private boolean isWhiteList(String path) {
        for (String white : whiteList) {
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