package org.ticketing_system.biz.aiservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * AI 服务基础配置类
 */
@Configuration
public class AiConfiguration {

    /**
     * 配置支持负载均衡的 WebClient.Builder，供非阻塞 HTTP 客户端使用
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder(AiProperties aiProperties) {
        AiProperties.Remote remote = aiProperties.getRemote();
        int connectTimeoutMs = remote == null ? 3000 : remote.getConnectTimeoutMs();
        int responseTimeoutMs = remote == null ? 5000 : remote.getResponseTimeoutMs();
        int maxConnections = remote == null ? 200 : remote.getMaxConnections();
        ConnectionProvider provider = ConnectionProvider.builder("ai-service-http")
                .maxConnections(maxConnections)
                .pendingAcquireTimeout(Duration.ofMillis(responseTimeoutMs))
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(responseTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(responseTimeoutMs, TimeUnit.MILLISECONDS)));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
