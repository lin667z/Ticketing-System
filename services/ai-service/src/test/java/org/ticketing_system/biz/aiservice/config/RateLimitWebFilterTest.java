package org.ticketing_system.biz.aiservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitWebFilterTest {

    @Test
    @SuppressWarnings("unchecked")
    void allowedRequestDoesNotReadCounterAfterLuaExecution() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        RateLimitLuaScript script = mock(RateLimitLuaScript.class);
        AiProperties properties = new AiProperties();
        RateLimitWebFilter filter = new RateLimitWebFilter(redisTemplate, properties, script);
        WebFilterChain chain = exchange -> Mono.empty();
        RedisScript<Long> redisScript = mock(RedisScript.class);
        when(script.getSlidingWindowScript()).thenReturn(redisScript);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(3L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/ai-service/chat/stream")
                .header("userId", "1"));

        filter.filter(exchange, chain).block();

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), anyString(), anyString());
        verify(valueOperations, never()).get(anyString());
    }
}
