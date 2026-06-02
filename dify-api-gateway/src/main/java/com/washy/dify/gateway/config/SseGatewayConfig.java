package com.washy.dify.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.http.codec.multipart.SynchronossPartHttpMessageReader;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

import java.util.concurrent.TimeUnit;

/**
 * SSE流式输出透传配置
 */
@Configuration
public class SseGatewayConfig implements WebFluxConfigurer {

    /**
     * 配置长连接，支持SSE流式输出
     */
    @Bean
    public ReactorClientHttpConnector reactorClientHttpConnector() {
        TcpClient tcpClient = TcpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000)
                .doOnConnected(connection ->
                        connection.addHandlerLast(new ReadTimeoutHandler(300, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(300, TimeUnit.SECONDS)));

        HttpClient httpClient = HttpClient.from(tcpClient)
                .keepAlive(true)
                .responseTimeout(java.time.Duration.ofMinutes(5));

        return new ReactorClientHttpConnector(httpClient);
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        SynchronossPartHttpMessageReader partReader = new SynchronossPartHttpMessageReader();
        partReader.setMaxParts(10);
        partReader.setMaxDiskUsagePerPart(-1);
        MultipartHttpMessageReader multipartReader = new MultipartHttpMessageReader(partReader);
        configurer.defaultCodecs().multipartReader(multipartReader);
        configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 10);
    }
}