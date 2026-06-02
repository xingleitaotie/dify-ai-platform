package com.washy.dify.function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;


/**
 * 函数调用服务启动类
 */
@EnableDiscoveryClient
@SpringBootApplication
public class FunctionApplication {
    public static void main(String[] args) {
        SpringApplication.run(FunctionApplication.class, args);
    }
}
