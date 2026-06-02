package com.washy.dify.rag;

import com.washy.dify.rag.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author Washy
 * @date 2026-04-09 14:04
 * @description
 */
@EnableDiscoveryClient
@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
@EnableFeignClients(basePackages = "com.washy.dify.feign.client")
public class RagApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
