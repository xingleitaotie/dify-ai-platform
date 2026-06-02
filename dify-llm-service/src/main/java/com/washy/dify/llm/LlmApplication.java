package com.washy.dify.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
@EnableDiscoveryClient
@SpringBootApplication
@EnableFeignClients(basePackages = "com.washy.dify.feign.client")
public class LlmApplication {
    public static void main(String[] args) {
        SpringApplication.run(LlmApplication.class, args);
    }
}