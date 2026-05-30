package org.ticketing_system.biz.aiservice.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 业务全量配置属性类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Validated
@RefreshScope
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** AI 渠道列表 */
    @NotEmpty(message = "AI 渠道不能为空")
    private List<Channel> channels = new ArrayList<>();

    /** 故障转移配置 */
    @NotNull(message = "故障转移配置不能为空")
    private Failover failover = new Failover();

    /** 记忆管理配置 */
    private Memory memory = new Memory();

    /** 分布式限流配置 */
    private RateLimit rateLimit = new RateLimit();

    /** 缓存策略配置 */
    private Cache cache = new Cache();

    /** 聊天编排配置 */
    private Chat chat = new Chat();

    /** Agent 编排配置 */
    private Agent agent = new Agent();

    private Remote remote = new Remote();

    /**
     * AI 渠道详细配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Channel {
        private String code;
        private String protocol;
        private String baseUrl;
        private String chatCompletionsUrl;
        private String apiKey;

        public Channel(String code, String protocol, String baseUrl, String apiKey) {
            this.code = code;
            this.protocol = protocol;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
        }
    }

    /**
     * 故障转移策略配置，定义全局兜底和特定路由
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Failover {
        private GlobalFallback globalFallback;
        private Map<String, List<RouteNode>> routes = new HashMap<>();
    }

    /**
     * 记忆管理配置，控制对话历史存储限制
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Memory {
        private int maxHistoryMessages = 20;
        private int maxHistoryChars = 24000;
    }

    /**
     * 全局兜底模型配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlobalFallback {
        private String provider;
        private String realModel;
    }

    /**
     * 具体路由节点配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteNode {
        private String id;
        private String provider;
        private String realModel;
        private int priority;
        private long timeoutMs = 30000;
        private boolean enabled = true;
    }

    /**
     * AI 分布式限流配置，区分认证与匿名用户
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimit {
        /** 是否启用分布式限流 */
        private boolean enabled = true;
        /** 认证用户每分钟最大请求数 */
        private int maxRequestsPerMinute = 10;
        /** 匿名用户每分钟最大请求数 */
        private int maxRequestsPerMinuteAnonymous = 3;
    }

    /**
     * AI 业务缓存策略配置，包括 TTL 和防穿透开关
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cache {
        /** 用户画像缓存 TTL（秒） */
        private int userProfileTtlSeconds = 300;
        /** 是否启用布隆过滤器 */
        private boolean bloomFilterEnabled = true;
    }

    /**
     * 聊天对话编排参数配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Chat {
        /** 最大历史消息数 */
        private int maxHistory = 20;
        /** 工具结果回注最大字符数 */
        private int toolResultMaxChars = 4000;
        /** 会话双轨上下文缓存 TTL */
        private int contextTtlSeconds = 86400;
        /** 保留的完整对话轮数 */
        private int lastTurns = 2;
        /** 超过该轮数后开始提取 Session Context Window */
        private int summaryStartTurn = 3;
    }

    /**
     * 多 Agent 协同编排配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Agent {
        /** 是否启用多 Agent 编排 */
        private boolean enabled = true;
        /** Master Agent 生成的最大子任务数 */
        private int maxTasks = 5;
        /** 单个 Worker Agent 任务超时时间 */
        private int taskTimeoutMs = 10000;
        /** 是否允许聚合器调用模型润色结果 */
        private boolean aggregatorLlmEnabled = true;
        /** Master Agent 输出 Token 限制 */
        private int masterMaxTokens = 512;
        /** 聚合器输出 Token 限制 */
        private int aggregatorMaxTokens = 700;
        /** 通用聊天输出 Token 限制 */
        private int generalMaxTokens = 900;
        /** 业务摘要最大字符数 */
        private int resultMaxChars = 500;
        /** 答案中显示的最大车次行数 */
        private int maxDisplayCount = 5;
        /** 答案中显示的最大订单行数 */
        private int orderDisplayCount = 3;
        private boolean traceEnabled = false;
        private boolean fastGeneralRouteEnabled = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Remote {
        private int connectTimeoutMs = 3000;
        private int responseTimeoutMs = 5000;
        private int profileRetryAttempts = 1;
        private long profileRetryBackoffMs = 200L;
        private int maxConnections = 200;
    }
}
