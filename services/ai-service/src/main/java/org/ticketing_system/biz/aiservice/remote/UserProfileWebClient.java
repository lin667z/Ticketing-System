package org.ticketing_system.biz.aiservice.remote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.remote.dto.UserQueryActualRespDTO;
import org.ticketing_system.framework.starter.convention.exception.RemoteException;
import org.ticketing_system.framework.starter.convention.exception.ServiceException;
import org.ticketing_system.framework.starter.convention.result.Result;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * 用户服务远程调用组件，基于 WebClient 提供非阻塞异步接口。
 */
@Slf4j
@Component
public class UserProfileWebClient {

    /** 连接超时时间（秒） */
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    /** 响应超时时间（秒） */
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 5000;
    /** 重试最大次数 */
    private static final int DEFAULT_RETRY_ATTEMPTS = 1;
    /** 重试间隔（毫秒） */
    private static final long DEFAULT_RETRY_BACKOFF_MS = 200L;
    /** 用户查询接口路径 */
    private static final String USER_SERVICE_QUERY_PATH = "/api/user-service/actual/query";

    private final WebClient webClient;
    private final AiProperties aiProperties;

    public UserProfileWebClient(AiProperties aiProperties, WebClient.Builder builder) {
        this.aiProperties = aiProperties;
        // 从配置中解析并设置 user-service 基础地址
        String baseUrl = resolveUserServiceBaseUrl(aiProperties);
        log.info("初始化 UserProfileWebClient: baseUrl={}", baseUrl);
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 根据用户名查询用户真实信息。
     */
    public Mono<UserQueryActualRespDTO> queryUserByUsername(String username, String token) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(USER_SERVICE_QUERY_PATH)
                        .queryParam("username", username)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, buildBearerToken(token))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response -> {
                    log.warn("查询用户服务返回 4xx: username={}, status={}", username, response.statusCode());
                    return Mono.error(new RemoteException("查询用户服务失败，客户端错误: " + response.statusCode()));
                })
                .onStatus(status -> status.is5xxServerError(), response -> {
                    log.error("查询用户服务返回 5xx: username={}, status={}", username, response.statusCode());
                    return Mono.error(new RemoteException("查询用户服务失败，服务端错误: " + response.statusCode()));
                })
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), response -> {
                    log.error("查询用户服务返回非 2xx: username={}, status={}", username, response.statusCode());
                    return Mono.error(new RemoteException("查询用户服务失败: " + response.statusCode()));
                })
                .bodyToMono(new ParameterizedTypeReference<Result<UserQueryActualRespDTO>>() {})
                .flatMap(result -> {
                    if (result == null || !result.isSuccess() || result.getData() == null) {
                        log.warn("查询用户服务返回空数据: username={}, result={}", username, result);
                        return Mono.error(new RemoteException("查询用户服务失败：用户数据为空"));
                    }
                    return Mono.just(result.getData());
                })
                .timeout(Duration.ofMillis(getResponseTimeoutMs()))
                .retryWhen(Retry.backoff(getProfileRetryAttempts(), Duration.ofMillis(getProfileRetryBackoffMs()))
                        .filter(throwable -> throwable instanceof RemoteException)
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            log.error("查询用户服务重试耗尽: username={}, totalRetries={}",
                                    username, retrySignal.totalRetries());
                            return new ServiceException("查询用户服务失败，已重试 " + getProfileRetryAttempts() + " 次");
                        }));
    }

    /**
     * 查询用户下的乘车人列表（待实现）。
     */
    public Mono<Object> queryPassengers(String userId, String token) {
        // TODO: Phase 3 实现 - 调用 /api/user-service/passenger/list
        return Mono.error(new UnsupportedOperationException("queryPassengers 尚未实现"));
    }

    /**
     * 构建 Bearer 认证 Token。
     */
    private String buildBearerToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        if (token.startsWith("Bearer ")) {
            return token;
        }
        return "Bearer " + token;
    }

    /**
     * 解析用户服务基础请求地址。
     */
    private String resolveUserServiceBaseUrl(AiProperties aiProperties) {
        if (aiProperties != null && aiProperties.getChannels() != null) {
            for (AiProperties.Channel channel : aiProperties.getChannels()) {
                if ("user-service".equals(channel.getCode()) && channel.getBaseUrl() != null) {
                    return channel.getBaseUrl();
                }
            }
        }
        return "http://ticketing-system-user-service";
    }

    private int getResponseTimeoutMs() {
        AiProperties.Remote remote = aiProperties.getRemote();
        return remote == null ? DEFAULT_RESPONSE_TIMEOUT_MS : remote.getResponseTimeoutMs();
    }

    private int getProfileRetryAttempts() {
        AiProperties.Remote remote = aiProperties.getRemote();
        return remote == null ? DEFAULT_RETRY_ATTEMPTS : remote.getProfileRetryAttempts();
    }

    private long getProfileRetryBackoffMs() {
        AiProperties.Remote remote = aiProperties.getRemote();
        return remote == null ? DEFAULT_RETRY_BACKOFF_MS : remote.getProfileRetryBackoffMs();
    }
}
