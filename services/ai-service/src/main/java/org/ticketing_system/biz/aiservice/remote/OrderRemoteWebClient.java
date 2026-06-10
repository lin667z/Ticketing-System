package org.ticketing_system.biz.aiservice.remote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.framework.starter.bases.constant.UserConstant;
import org.ticketing_system.framework.starter.convention.exception.RemoteException;
import org.ticketing_system.framework.starter.convention.result.Result;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单服务远程调用组件，基于 WebClient 提供非阻塞异步接口。
 */
@Slf4j
@Component
public class OrderRemoteWebClient {

    // 响应超时时间（秒）
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 5000;
    // 订单查询接口路径
    private static final String ORDER_QUERY_PATH = "/api/order-service/order/ticket/query";
    // 本人订单查询接口路径（非分页，30天硬上限，LIMIT 模式）
    private static final String ORDER_SELF_QUERY_PATH = "/api/order-service/order/ticket/self/query";

    private final WebClient webClient;
    private final AiProperties aiProperties;

    public OrderRemoteWebClient(WebClient.Builder builder, AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        // 使用 Nacos 服务发现并设置默认请求头
        this.webClient = builder
                .baseUrl("http://ticketing-system-order-service")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 根据订单号查询订单详情
     */
    public Mono<Map<String, Object>> queryOrderBySn(String orderSn) {
        log.info("调用 order-service 查询订单: orderSn={}", orderSn);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(ORDER_QUERY_PATH)
                        .queryParam("orderSn", orderSn)
                        .build())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> {
                    log.error("order-service 返回非 2xx: status={}, orderSn={}",
                            response.statusCode(), orderSn);
                    return Mono.error(new RemoteException("订单查询服务暂不可用"));
                })
                .bodyToMono(new ParameterizedTypeReference<Result<Map<String, Object>>>() {})
                .flatMap(result -> {
                    if (result == null || !result.isSuccess() || result.getData() == null) {
                        log.warn("order-service 返回空数据: orderSn={}", orderSn);
                        return Mono.just(Map.<String, Object>of());
                    }
                    return Mono.just(result.getData());
                })
                .timeout(Duration.ofMillis(getResponseTimeoutMs()))
                .onErrorMap(ex -> {
                    log.error("order-service 调用失败: orderSn={}, error={}", orderSn, ex.getMessage());
                    return new RemoteException("订单查询服务暂不可用，请稍后重试");
                });
    }

    /**
     * 查询当前登录用户的订单（非分页，30天硬上限，LIMIT 模式）
     */
    public Mono<Map<String, Object>> querySelfOrders(AiAuthenticatedUserContext userContext, String date, Long count) {
        log.info("调用 order-service 查询本人订单: userId={}, date={}, count={}",
                userContext == null ? null : userContext.getUserId(), date, count);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(ORDER_SELF_QUERY_PATH)
                        .queryParamIfPresent("date", optionalText(date))
                        .queryParamIfPresent("count", java.util.Optional.ofNullable(count))
                        .build())
                .headers(headers -> fillUserHeaders(headers, userContext))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> {
                    log.error("order-service 本人订单返回非 2xx: status={}", response.statusCode());
                    return Mono.error(new RemoteException("本人订单查询服务暂不可用"));
                })
                .bodyToMono(new ParameterizedTypeReference<Result<Map<String, Object>>>() {})
                .flatMap(result -> {
                    if (result == null || !result.isSuccess() || result.getData() == null) {
                        return Mono.just(Map.<String, Object>of("records", List.of()));
                    }
                    return Mono.just(result.getData());
                })
                .timeout(Duration.ofMillis(getResponseTimeoutMs()))
                .onErrorMap(ex -> {
                    log.error("order-service 本人订单调用失败: error={}", ex.getMessage());
                    return new RemoteException("本人订单查询服务暂不可用，请稍后重试");
                });
    }

    /**
     * 将用户信息填充至请求头
     */
    private void fillUserHeaders(HttpHeaders headers, AiAuthenticatedUserContext userContext) {
        if (userContext == null) {
            return;
        }
        if (userContext.getUserId() != null) {
            headers.set(UserConstant.USER_ID_KEY, String.valueOf(userContext.getUserId()));
        }
        if (userContext.getUsername() != null) {
            headers.set(UserConstant.USER_NAME_KEY, userContext.getUsername());
        }
        if (userContext.getRealName() != null) {
            headers.set(UserConstant.REAL_NAME_KEY, URLEncoder.encode(userContext.getRealName(), StandardCharsets.UTF_8));
        }
        if (userContext.getToken() != null) {
            headers.set(UserConstant.USER_TOKEN_KEY, userContext.getToken());
            headers.set(HttpHeaders.AUTHORIZATION, userContext.getToken());
        }
    }

    /**
     * 将空字符串转换为缺省查询参数
     */
    private java.util.Optional<String> optionalText(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private int getResponseTimeoutMs() {
        AiProperties.Remote remote = aiProperties.getRemote();
        return remote == null ? DEFAULT_RESPONSE_TIMEOUT_MS : remote.getResponseTimeoutMs();
    }
}
