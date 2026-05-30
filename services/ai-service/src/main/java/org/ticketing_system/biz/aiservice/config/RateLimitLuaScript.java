package org.ticketing_system.biz.aiservice.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Lua 滑动窗口限流脚本配置，确保计数与过期操作的原子性
 */
@Slf4j
@Component
public class RateLimitLuaScript {

    /** 外部 Lua 脚本路径 */
    private static final String LUA_SCRIPT_PATH = "lua/rate_limit_sliding_window.lua";

    /**
     * 内置默认 Lua 脚本（滑动窗口计数器）
     */
    private static final String DEFAULT_LUA_SCRIPT = ""
            + "local key = KEYS[1]\n"
            + "local window = tonumber(ARGV[1])\n"
            + "local limit = tonumber(ARGV[2])\n"
            + "local current = redis.call('INCR', key)\n"
            + "if current == 1 then\n"
            + "    redis.call('EXPIRE', key, window)\n"
            + "end\n"
            + "if current > limit then\n"
            + "    return -current\n"
            + "end\n"
            + "return current";

    /** 滑动窗口脚本实例 */
    @Getter
    private RedisScript<Long> slidingWindowScript;

    /**
     * 初始化脚本，优先加载外部文件
     */
    @PostConstruct
    public void init() {
        // 优先加载外部 Lua 脚本，若不存在则使用内置默认脚本
        ClassPathResource resource = new ClassPathResource(LUA_SCRIPT_PATH);
        String scriptContent;
        if (resource.exists()) {
            scriptContent = loadExternalScript(resource);
            log.info("加载外部限流 Lua 脚本: {}", LUA_SCRIPT_PATH);
        } else {
            scriptContent = DEFAULT_LUA_SCRIPT;
            log.info("使用内置默认限流 Lua 脚本");
        }
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(scriptContent);
        redisScript.setResultType(Long.class);
        this.slidingWindowScript = redisScript;
    }

    /**
     * 加载外部 Lua 脚本内容
     */
    private String loadExternalScript(ClassPathResource resource) {
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("加载外部 Lua 脚本失败，回退到内置默认脚本: {}", e.getMessage());
            return DEFAULT_LUA_SCRIPT;
        }
    }
}
