package org.ticketing_system.biz.aiservice.agent.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.common.context.AiAuthenticatedUserContext;
import org.ticketing_system.biz.aiservice.remote.OrderRemoteWebClient;
import org.ticketing_system.biz.aiservice.remote.TicketRemoteWebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 响应式的门面模式类，封装了对下游票务和订单服务的调用逻辑
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketQueryFacade {

    private static final long DEFAULT_ORDER_PAGE = 1L;
    private static final long DEFAULT_ORDER_SIZE = 10L;
    private static final Duration STATION_CACHE_TTL = Duration.ofMinutes(30);

    private final TicketRemoteWebClient ticketRemoteWebClient;
    private final OrderRemoteWebClient orderRemoteWebClient;
    private final AiBusinessResultFormatter formatter;
    private final AtomicReference<StationCache> stationCacheRef = new AtomicReference<>();
    private volatile Mono<List<Map<String, Object>>> stationRefreshMono;

    /**
     * 查询车票余票信息
     *
     * @param departure   出发站
     * @param arrival     到达站
     * @param date        出发日期
     * @param trainNumber 可选的车次过滤
     * @return 业务查询结果
     */
    public Mono<BusinessQueryResult> queryTickets(String departure, String arrival, String date, String trainNumber) {
        return Mono.zip(resolveStationCode(departure), resolveStationCode(arrival))
                .flatMap(tuple -> ticketRemoteWebClient.queryTickets(tuple.getT1(), tuple.getT2(), date))
                .map(data -> BusinessQueryResult.builder()
                        .summary(formatter.formatTrainSchedule(data, trainNumber))
                        .componentType("train_card")
                        .componentData(formatter.buildTrainComponent(data, trainNumber, date))
                        .rawData(data)
                        .build());
    }

    /**
     * 根据订单号查询订单详情
     *
     * @param orderSn 订单号
     * @return 业务查询结果
     */
    public Mono<BusinessQueryResult> queryOrder(String orderSn) {
        return orderRemoteWebClient.queryOrderBySn(orderSn)
                .map(data -> BusinessQueryResult.builder()
                        .summary(formatter.formatOrder(data))
                        .componentType("order_card")
                        .componentData(formatter.buildOrderComponent(data))
                        .rawData(data)
                        .build());
    }

    /**
     * 查询当前登录用户的本人订单
     *
     * @param userContext 用户认证上下文
     * @param date        可选的下单日期
     * @param count       可选的返回条数
     * @return 业务查询结果
     */
    public Mono<BusinessQueryResult> pageSelfOrders(AiAuthenticatedUserContext userContext,
                                                    String date,
                                                    Long count) {
        Long actualCount = count == null || count < 1 ? null : count;
        return orderRemoteWebClient.pageSelfOrders(userContext, DEFAULT_ORDER_PAGE, DEFAULT_ORDER_SIZE, date, actualCount)
                .map(data -> BusinessQueryResult.builder()
                        .summary(formatter.formatSelfOrders(data, date, actualCount))
                        .componentType("order_card")
                        .componentData(formatter.buildSelfOrderComponent(data))
                        .rawData(data)
                        .build());
    }

    /**
     * 将站点名称解析为对应的站点代码（支持站名、地区名或直接传代码）
     */
    private Mono<String> resolveStationCode(String stationText) {
        if (stationText == null || stationText.isBlank()) {
            return Mono.just("");
        }
        return getStations()
                .map(stations -> {
                    String normalized = stationText.trim();
                    for (Map<String, Object> station : stations) {
                        String code = read(station, "code");
                        String name = read(station, "name");
                        String regionName = read(station, "regionName");
                        if (normalized.equals(code) || normalized.equals(name) || normalized.equals(regionName)) {
                            return code;
                        }
                    }
                    return normalized;
                });
    }

    /**
     * 获取所有站点信息，并带缓存机制
     */
    private Mono<List<Map<String, Object>>> getStations() {
        StationCache cache = stationCacheRef.get();
        long now = System.currentTimeMillis();
        if (cache != null && cache.expiresAt > now) {
            return Mono.just(cache.stations);
        }
        return refreshStations(now);
    }

    private Mono<List<Map<String, Object>>> refreshStations(long now) {
        Mono<List<Map<String, Object>>> current = stationRefreshMono;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = stationRefreshMono;
            if (current == null) {
                current = ticketRemoteWebClient.listAllStations()
                        .doOnNext(stations -> stationCacheRef.set(new StationCache(stations, now + STATION_CACHE_TTL.toMillis())))
                        .doFinally(signalType -> stationRefreshMono = null)
                        .cache();
                stationRefreshMono = current;
            }
            return current;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmStationCache() {
        refreshStations(System.currentTimeMillis())
                .subscribe(
                        ignored -> log.debug("Station cache warmed"),
                        ex -> log.warn("Station cache warmup failed: {}", ex.getMessage()));
    }

    /**
     * 安全读取 Map 中的字符串
     */
    private String read(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value == null ? "" : value.toString();
    }

    /**
     * 业务查询结果包装类
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BusinessQueryResult {

        /**
         * 结果总结文本
         */
        private String summary;

        /**
         * 组件类型标识
         */
        private String componentType;

        /**
         * 组件渲染数据
         */
        private Object componentData;

        /**
         * 原始响应数据
         */
        private Map<String, Object> rawData;
    }

    /**
     * 站点信息本地缓存记录
     */
    private record StationCache(List<Map<String, Object>> stations, long expiresAt) {
    }
}
