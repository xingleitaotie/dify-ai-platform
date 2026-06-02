package com.washy.dify.prompt;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@SpringBootApplication
@EnableFeignClients(basePackages = "com.washy.dify.feign.client")
public class PromptApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromptApplication.class, args);
    }
}