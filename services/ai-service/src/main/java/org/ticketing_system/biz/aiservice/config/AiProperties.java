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

    /** 缓存策略配置 */
    private Cache cache = new Cache();

    /** 聊天编排配置 */
    private Chat chat = new Chat();

    /** Agent 编排配置 */
    private Agent agent = new Agent();

    private Remote remote = new Remote();

    /** L1 工作记忆配置 */
    private WorkingMemory workingMemory = new WorkingMemory();

    /** L3 情景摘要配置 */
    private Episodic episodic = new Episodic();

    /** L4 长期记忆配置 */
    private LongTermMemory longTermMemory = new LongTermMemory();

    /** L5 知识/RAG 检索配置（铁路规则，预留） */
    private Knowledge knowledge = new Knowledge();

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
        private long gapTimeoutMs = 15000;
        private boolean enabled = true;
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
        /** 单个 Worker Agent 任务超时时间（毫秒），需大于 LLM 调用超时 */
        private int taskTimeoutMs = 30000;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkingMemory {
        private int ttlSeconds = 86400;
        private int maxRecentMessages = 10;
        private int maxClarificationCount = 3;
        /** 保留的完整对话轮数（合并自 Chat.lastTurns） */
        private int lastTurns = 2;
        /** 超过该轮数后开始提取会话摘要（合并自 Chat.summaryStartTurn） */
        private int summaryStartTurn = 3;
        /** 对话轮数不超过该阈值时保留全量历史，超过后才截断到 maxRecentMessages */
        private int fullHistoryMaxTurns = 10;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Episodic {
        private boolean autoFinalize = true;
        private int maxEpisodesPerUser = 20;
        private int finalizeMinTurns = 4;
        private boolean crossSessionInject = false;
        private boolean llmSummaryEnabled = false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LongTermMemory {
        private boolean autoDigestEnabled = true;
        private int maxPerUser = 50;
        private int retrievalTopK = 8;
        private double decayFactor = 0.95;
        private int digestMinEpisodeTurns = 4;
    }

    /**
     * L5 知识/RAG 检索配置（铁路规则检索，当前为预留占位）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Knowledge {
        /** 是否启用规则知识检索；关闭时检索器返回空，不影响主流程 */
        private boolean enabled = false;
        /** 单次注入的规则片段上限 */
        private int topK = 3;
        /** 检索模式：KEYWORD（关键词，降级）/ EMBEDDING（向量，后期） */
        private String mode = "KEYWORD";
    }
}
