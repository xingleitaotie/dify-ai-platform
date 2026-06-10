package com.washy.dify.provider;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@MapperScan("com.washy.dify.provider.mapper")
@EnableRetry
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.washy.dify.feign.client")
public class ModelProviderApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModelProviderApplication.class, args);
        System.out.println("========================================");
        System.out.println("   Dify Model Provider Service Started   ");
        System.out.println("========================================");
    }
}