package org.ticketing_system.biz.aiservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.ticketing_system.framework.starter.bases.constant.UserConstant;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 基于 Redis 的分布式限流过滤器，保护 AI 端点免受过度调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitWebFilter implements WebFilter {

    /** 聊天端点路径 */
    private static final String CHAT_STREAM_PATH = "/api/ai-service/chat/stream";

    /** 限流 Redis Key 前缀 */
    private static final String RATE_LIMIT_KEY_PREFIX = "ai:rate_limit:";

    /** 限流窗口标识 */
    private static final String RATE_LIMIT_WINDOW_SUFFIX = ":minute";

    /** 限流窗口大小（秒） */
    private static final long RATE_LIMIT_WINDOW_SECONDS = 60L;

    /** 响应头：剩余请求数 */
    private static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";

    /** 响应头：限流重置时间（Unix 秒） */
    private static final String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";

    /** 响应头：重试间隔（秒） */
    private static final String HEADER_RETRY_AFTER = "Retry-After";

    /** 限流提示消息 */
    private static final String RATE_LIMIT_MESSAGE = "请求过于频繁，请稍后再试";

    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties aiProperties;
    private final RateLimitLuaScript rateLimitLuaScript;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 只对聊天端点限流，其他路径直接放行
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(CHAT_STREAM_PATH)) {
            return chain.filter(exchange);
        }

        // 检查限流开关
        AiProperties.RateLimit rateLimitConfig = aiProperties.getRateLimit();
        if (rateLimitConfig == null || !rateLimitConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        // 提取用户标识并获取对应限制阈值
        String userId = resolveUserId(exchange);
        int maxRequests = resolveMaxRequests(userId, rateLimitConfig);
        String rateLimitKey = buildRateLimitKey(userId);

        // 调用 Lua 脚本进行原子限流判断
        RedisScript<Long> script = rateLimitLuaScript.getSlidingWindowScript();
        Long limitResult = stringRedisTemplate.execute(
                script,
                List.of(rateLimitKey),
                String.valueOf(RATE_LIMIT_WINDOW_SECONDS),
                String.valueOf(maxRequests));

        // 计算剩余请求次数并设置响应头
        long currentCount = limitResult == null ? 0L : Math.abs(limitResult);
        long remaining = Math.max(0, maxRequests - currentCount);

        exchange.getResponse().getHeaders().add(HEADER_RATE_LIMIT_REMAINING, String.valueOf(remaining));
        exchange.getResponse().getHeaders().add(HEADER_RATE_LIMIT_RESET,
                String.valueOf(System.currentTimeMillis() / 1000 + RATE_LIMIT_WINDOW_SECONDS));

        boolean isAllowed = limitResult != null && limitResult > 0L;
        if (!isAllowed) {
            log.warn("限流触发: userId={}, path={}, currentCount={}, maxRequests={}",
                    userId, path, currentCount, maxRequests);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add(HEADER_RETRY_AFTER,
                    String.valueOf(RATE_LIMIT_WINDOW_SECONDS));
            exchange.getResponse().getHeaders().setContentType(
                    org.springframework.http.MediaType.APPLICATION_JSON);
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory()
                            .wrap(("{\"code\":\"A0001\",\"message\":\"" + RATE_LIMIT_MESSAGE
                                    + "\",\"success\":false}").getBytes())));
        }

        return chain.filter(exchange);
    }

    /**
     * 解析用户标识，优先使用用户 ID，回退到 IP 地址
     */
    private String resolveUserId(ServerWebExchange exchange) {
        // 尝试从请求头获取网关注入的用户 ID
        String headerUserId = exchange.getRequest().getHeaders()
                .getFirst(UserConstant.USER_ID_KEY);
        if (StringUtils.hasText(headerUserId)) {
            return "user:" + headerUserId;
        }

        // 回退到 IP（匿名用户）
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return "ip:" + remoteAddress.getAddress().getHostAddress();
        }

        return "ip:unknown";
    }

    /**
     * 根据用户标识前缀确定最大请求数
     */
    private int resolveMaxRequests(String userId, AiProperties.RateLimit config) {
        if (userId.startsWith("ip:")) {
            return config.getMaxRequestsPerMinuteAnonymous();
        }
        return config.getMaxRequestsPerMinute();
    }

    /**
     * 构建限流 Redis Key
     */
    private String buildRateLimitKey(String userId) {
        return RATE_LIMIT_KEY_PREFIX + userId + RATE_LIMIT_WINDOW_SUFFIX;
    }
}
