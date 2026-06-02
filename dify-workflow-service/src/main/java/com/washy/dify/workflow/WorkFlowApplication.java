package com.washy.dify.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.washy.dify.feign.client")
@MapperScan("com.washy.dify.workflow.mapper")
public class WorkFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkFlowApplication.class, args);
    }
}