package com.washy.dify.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置
 */
@Configuration
@Slf4j
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:8080,http://localhost:3000}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:Content-Type,Authorization,token,X-User-Id,X-User-Name,Accept,Origin}")
    private String allowedHeaders;

    @Value("${cors.exposed-headers:Content-Type,Cache-Control,X-Accel-Buffering}")
    private String exposedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @PostConstruct
    public void init() {
        log.info("CORS配置 - 允许的域名: {}", allowedOrigins);
        log.info("CORS配置 - 允许的方法: {}", allowedMethods);
        log.info("CORS配置 - 允许携带凭证: {}", allowCredentials);
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> originList = Arrays.asList(allowedOrigins.split(","));
        for (String origin : originList) {
            config.addAllowedOriginPattern(origin.trim());
        }

        List<String> methodList = Arrays.asList(allowedMethods.split(","));
        for (String method : methodList) {
            config.addAllowedMethod(method.trim());
        }

        List<String> headerList = Arrays.asList(allowedHeaders.split(","));
        for (String header : headerList) {
            config.addAllowedHeader(header.trim());
        }

        List<String> exposedList = Arrays.asList(exposedHeaders.split(","));
        for (String header : exposedList) {
            config.addExposedHeader(header.trim());
        }

        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}