package org.ticketing_system.biz.aiservice.agent.core;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.llm.dto.LlmStreamResponse;
import org.ticketing_system.biz.aiservice.common.enums.LlmStreamResponseType;
import org.ticketing_system.biz.aiservice.common.exception.AiErrorType;
import org.ticketing_system.biz.aiservice.common.exception.AiServiceException;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一的故障转移路由管理器，负责渠道路由、熔断保护和全局兜底
 */
@Slf4j
@Component
public class FailoverRouteManager {

    // 熔断阈值：连续失败 N 次后暂时跳过该渠道
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;
    // 熔断恢复时间（毫秒）：30 秒后半开探测
    private static final long CIRCUIT_BREAKER_RECOVERY_MS = 30_000L;
    // 全局兜底路由 ID
    private static final String GLOBAL_FALLBACK_ID = "global-fallback";
    // 全局兜底超时毫秒
    private static final long GLOBAL_FALLBACK_TIMEOUT_MS = 30_000L;

    private final AiProperties aiProperties;

    // OpenAI 异步客户端缓存，按渠道 code 索引
    private final Map<String, OpenAIClientAsync> clientCache = new ConcurrentHashMap<>();
    // OpenAI 同步客户端缓存，用于流式调用
    private final Map<String, OpenAIClient> syncClientCache = new ConcurrentHashMap<>();
    // 每个渠道的连续失败计数
    private final Map<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
    // 每个渠道最后一次失败的时间戳
    private final Map<String, Long> breakerTimestamps = new ConcurrentHashMap<>();

    // 定时调度器，用于熔断半开探测
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "failover-circuit-breaker");
        thread.setDaemon(true);
        return thread;
    });

    public FailoverRouteManager(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        // 定期重置超过恢复时间的熔断渠道
        scheduler.scheduleWithFixedDelay(this::halfOpenProbe, CIRCUIT_BREAKER_RECOVERY_MS,
                CIRCUIT_BREAKER_RECOVERY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取当前按优先级排序的可用路由节点列表
     */
    public List<AiProperties.RouteNode> resolveActiveRoutes() {
        List<AiProperties.RouteNode> activeRoutes = new ArrayList<>();

        AiProperties.Failover failover = aiProperties.getFailover();
        if (failover != null && failover.getRoutes() != null) {
            activeRoutes = failover.getRoutes().values().stream()
                    .flatMap(List::stream)
                    .filter(AiProperties.RouteNode::isEnabled)
                    .filter(this::isCircuitClosed)
                    .sorted(Comparator.comparingInt(AiProperties.RouteNode::getPriority))
                    .collect(Collectors.toList());
        }

        // 全局兜底始终在最后，不参与熔断
        if (failover != null && failover.getGlobalFallback() != null) {
            AiProperties.GlobalFallback fallback = failover.getGlobalFallback();
            activeRoutes.add(new AiProperties.RouteNode(
                    GLOBAL_FALLBACK_ID,
                    fallback.getProvider(),
                    fallback.getRealModel(),
                    Integer.MAX_VALUE,
                    GLOBAL_FALLBACK_TIMEOUT_MS,
                    GLOBAL_FALLBACK_TIMEOUT_MS,
                    true));
        }

        if (activeRoutes.isEmpty()) {
            log.warn("故障转移路由列表为空，请检查 ai.failover 配置");
        }
        return activeRoutes;
    }

    /**
     * 从可用路由中选择首个可用节点
     */
    public AiProperties.RouteNode selectRoute(LlmRequest request) {
        List<AiProperties.RouteNode> routes = resolveActiveRoutes();
        if (routes.isEmpty()) {
            throw new AiServiceException(AiErrorType.CHANNEL_UNAVAILABLE, "无可用 AI 渠道，请检查 ai.failover 配置");
        }
        AiProperties.RouteNode selected = routes.get(0);
        log.info("故障转移路由选择: routeId={}, provider={}, model={}, priority={}",
                selected.getId(), selected.getProvider(), selected.getRealModel(), selected.getPriority());
        return selected;
    }

    /**
     * 执行带故障转移机制的流式 LLM 调用
     */
    public Flux<LlmStreamResponse> executeWithFailover(
            LlmRequest request,
            Function<AiProperties.RouteNode, Flux<LlmStreamResponse>> callFunc) {
        List<AiProperties.RouteNode> routes = resolveActiveRoutes();
        if (routes.isEmpty()) {
            return Flux.error(new AiServiceException(AiErrorType.CHANNEL_UNAVAILABLE, "无可用 AI 渠道"));
        }
        return executeWithFailoverRecursive(routes, 0, callFunc);
    }

    /**
     * 递归执行故障转移逻辑：当前渠道失败则尝试下一个，若已开始输出则不再切换
     */
    private Flux<LlmStreamResponse> executeWithFailoverRecursive(
            List<AiProperties.RouteNode> routes,
            int index,
            Function<AiProperties.RouteNode, Flux<LlmStreamResponse>> callFunc) {
        if (index >= routes.size()) {
            return Flux.error(new AiServiceException(AiErrorType.FAILOVER_EXHAUSTED,
                    "所有 AI 渠道均调用失败，已尝试 " + routes.size() + " 个路由节点"));
        }

        AiProperties.RouteNode routeNode = routes.get(index);
        // 使用 AtomicBoolean 追踪是否已发出过内容，防止部分输出后切换渠道
        AtomicBoolean emitted = new AtomicBoolean(false);

        return Flux.defer(() -> callFunc.apply(routeNode)
                .doOnNext(ignored -> emitted.set(true))
                .onErrorResume(ex -> {
                    log.warn("AI 渠道调用失败: routeId={}, provider={}, model={}, error={}",
                            routeNode.getId(), routeNode.getProvider(), routeNode.getRealModel(), ex.getMessage());
                    if (emitted.get()) {
                        // 已发出部分内容，不允许切换渠道
                        log.error("渠道 {} 在发出部分内容后失败，无法故障转移", routeNode.getId());
                        return Flux.error(new AiServiceException(AiErrorType.PARTIAL_OUTPUT_FAILED,
                                "AI 渠道 " + routeNode.getId() + " 在部分输出后失败，无法自动切换", ex));
                    }
                    // 记录失败并尝试下一个渠道
                    recordFailure(routeNode.getProvider());
                    if (index + 1 < routes.size()) {
                        LlmStreamResponse retryingSignal = LlmStreamResponse.builder()
                                .type(LlmStreamResponseType.RETRYING)
                                .error("当前线路繁忙，正在为您切换备用节点...").build();
                        return Flux.just(retryingSignal)
                                .concatWith(executeWithFailoverRecursive(routes, index + 1, callFunc));
                    }
                    return executeWithFailoverRecursive(routes, index + 1, callFunc);
                })
                .doOnComplete(() -> recordSuccess(routeNode.getProvider())));
    }

    /**
     * 获取或创建 OpenAI 异步客户端。
     */
    public OpenAIClientAsync getOrCreateClient(String channelCode) {
        return clientCache.computeIfAbsent(channelCode, code -> {
            AiProperties.Channel channel = resolveChannel(code);
            log.info("初始化 AI 渠道客户端: code={}, baseUrl={}", code, channel.getBaseUrl());
            return OpenAIOkHttpClientAsync.builder()
                    .baseUrl(trimTrailingSlash(channel.getBaseUrl()))
                    .apiKey(channel.getApiKey())
                    .maxRetries(0)
                    .build();
        });
    }

    /**
     * 获取或创建 OpenAI 同步客户端。
     */
    public OpenAIClient getOrCreateSyncClient(String channelCode) {
        return syncClientCache.computeIfAbsent(channelCode, code -> {
            AiProperties.Channel channel = resolveChannel(code);
            log.info("初始化 AI 渠道同步客户端: code={}, baseUrl={}", code, channel.getBaseUrl());
            return OpenAIOkHttpClient.builder()
                    .baseUrl(trimTrailingSlash(channel.getBaseUrl()))
                    .apiKey(channel.getApiKey())
                    .maxRetries(0)
                    .build();
        });
    }

    /**
     * 记录渠道调用成功，重置失败计数器。
     */
    public void recordSuccess(String provider) {
        AtomicInteger counter = failureCounters.get(provider);
        if (counter != null) {
            counter.set(0);
        }
    }

    /**
     * 记录渠道调用失败，并触发熔断。
     */
    public void recordFailure(String provider) {
        AtomicInteger counter = failureCounters.computeIfAbsent(provider, k -> new AtomicInteger());
        int failures = counter.incrementAndGet();
        breakerTimestamps.put(provider, System.currentTimeMillis());
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            log.warn("渠道 {} 连续失败 {} 次，熔断打开，{}ms 后半开探测",
                    provider, failures, CIRCUIT_BREAKER_RECOVERY_MS);
        }
    }

    /**
     * 判断渠道是否处于可用（非熔断）状态。
     */
    private boolean isCircuitClosed(AiProperties.RouteNode routeNode) {
        AtomicInteger counter = failureCounters.get(routeNode.getProvider());
        if (counter == null || counter.get() < CIRCUIT_BREAKER_THRESHOLD) {
            return true;
        }
        Long lastFailureTime = breakerTimestamps.get(routeNode.getProvider());
        if (lastFailureTime != null
            && System.currentTimeMillis() - lastFailureTime > CIRCUIT_BREAKER_RECOVERY_MS) {
            // 超过恢复时间，重置计数器，允许半开探测
            counter.set(0);
            log.info("渠道 {} 熔断恢复时间已到，重置计数器进入半开状态", routeNode.getProvider());
            return true;
        }
        log.debug("渠道 {} 处于熔断状态，跳过路由", routeNode.getProvider());
        return false;
    }

    /**
     * 定时执行半开探测，检查熔断是否已过期
     */
    private void halfOpenProbe() {
        long now = System.currentTimeMillis();
        breakerTimestamps.forEach((provider, timestamp) -> {
            if (now - timestamp > CIRCUIT_BREAKER_RECOVERY_MS) {
                AtomicInteger counter = failureCounters.get(provider);
                if (counter != null && counter.get() >= CIRCUIT_BREAKER_THRESHOLD) {
                    counter.set(0);
                    log.info("半开探测：渠道 {} 熔断已过期，重置计数器", provider);
                }
            }
        });
    }

    /**
     * 查找对应的渠道配置
     */
    private AiProperties.Channel resolveChannel(String provider) {
        return aiProperties.getChannels().stream()
                .filter(channel -> provider.equals(channel.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("AI provider 未找到: " + provider));
    }

    /**
     * 移除 URL 末尾的斜杠
     */
    private String trimTrailingSlash(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 应用关闭时清理资源。
     */
    @PreDestroy
    public void destroy() {
        scheduler.shutdownNow();
        clientCache.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception ex) {
                log.debug("关闭 OpenAI 异步客户端时出错", ex);
            }
        });
        clientCache.clear();
        syncClientCache.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception ex) {
                log.debug("关闭 OpenAI 同步客户端时出错", ex);
            }
        });
        syncClientCache.clear();
    }
}
