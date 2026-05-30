package org.ticketing_system.biz.aiservice.remote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.framework.starter.convention.exception.RemoteException;
import org.ticketing_system.framework.starter.convention.result.Result;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 购票服务远程调用组件，基于 WebClient 提供非阻塞异步接口。
 */
@Slf4j
@Component
public class TicketRemoteWebClient {

    /** 连接超时时间（秒） */
    private static final int CONNECT_TIMEOUT_SECONDS = 5;

    /** 响应超时时间（秒） */
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 5000;

    /** 车票查询接口路径 */
    private static final String TICKET_QUERY_PATH = "/api/ticket-service/ticket/query";

    /** 全部站点查询接口路径 */
    private static final String STATION_ALL_PATH = "/api/ticket-service/station/all";

    private final WebClient webClient;
    private final AiProperties aiProperties;

    public TicketRemoteWebClient(WebClient.Builder builder, AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        // 使用 Nacos 服务发现并设置默认请求头
        this.webClient = builder
                .baseUrl("http://ticketing-system-ticket-service")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 查询车票（列车时刻 + 余票）。
     */
    public Mono<Map<String, Object>> queryTickets(String fromStation, String toStation, String departureDate) {
        log.info("调用 ticket-service 查询车票: from={}, to={}, date={}", fromStation, toStation, departureDate);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(TICKET_QUERY_PATH)
                        .queryParam("fromStation", fromStation)
                        .queryParam("toStation", toStation)
                        .queryParam("departureDate", departureDate)
                        .build())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> {
                    log.error("ticket-service 返回非 2xx: status={}, from={}, to={}",
                            response.statusCode(), fromStation, toStation);
                    return Mono.error(new RemoteException("票务查询服务暂不可用"));
                })
                .bodyToMono(new ParameterizedTypeReference<Result<Map<String, Object>>>() {})
                .flatMap(result -> {
                    if (result == null || !result.isSuccess() || result.getData() == null) {
                        log.warn("ticket-service 返回空数据: from={}, to={}, date={}",
                                fromStation, toStation, departureDate);
                        return Mono.just(Map.<String, Object>of());
                    }
                    return Mono.just(result.getData());
                })
                .timeout(Duration.ofMillis(getResponseTimeoutMs()))
                .onErrorMap(ex -> {
                    log.error("ticket-service 调用失败: from={}, to={}, error={}",
                            fromStation, toStation, ex.getMessage());
                    return new RemoteException("票务查询服务暂不可用，请稍后重试");
                });
    }

    /**
     * 获取所有站点信息，用于名称与编码转换。
     */
    public Mono<List<Map<String, Object>>> listAllStations() {
        return webClient.get()
                .uri(STATION_ALL_PATH)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> {
                    log.error("ticket-service station all 返回非 2xx: status={}", response.statusCode());
                    return Mono.error(new RemoteException("站点查询服务暂不可用"));
                })
                .bodyToMono(new ParameterizedTypeReference<Result<List<Map<String, Object>>>>() {})
                .flatMap(result -> {
                    if (result == null || !result.isSuccess() || result.getData() == null) {
                        return Mono.just(List.<Map<String, Object>>of());
                    }
                    return Mono.just(result.getData());
                })
                .timeout(Duration.ofMillis(getResponseTimeoutMs()))
                .onErrorMap(ex -> {
                    log.error("ticket-service station all 调用失败: error={}", ex.getMessage());
                    return new RemoteException("站点查询服务暂不可用，请稍后重试");
                });
    }

    private int getResponseTimeoutMs() {
        AiProperties.Remote remote = aiProperties.getRemote();
        return remote == null ? DEFAULT_RESPONSE_TIMEOUT_MS : remote.getResponseTimeoutMs();
    }
}
