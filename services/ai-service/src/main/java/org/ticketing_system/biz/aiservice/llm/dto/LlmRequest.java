package org.ticketing_system.biz.aiservice.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;

import java.util.List;
import java.util.Map;

/**
 * 统一的 LLM 请求 DTO，封装系统提示词、对话历史、参数等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {

    /** 系统提示词 */
    private String systemPrompt;

    /** 当前用户输入的消息文本 */
    private String userMessage;

    /** 对话历史消息列表（按时间升序） */
    private List<Message> messages;

    /** 用户 ID */
    private Long userId;

    /** 会话 ID */
    private Long sessionId;

    /** 模型温度参数（0.0 ~ 2.0，null 则使用默认值） */
    private Double temperature;

    /** 最大生成 Token 数（null 则使用默认值） */
    private Integer maxTokens;

    /** 请求元数据，用于编排层内传递数据 */
    private Map<String, Object> metadata;

    private TimeoutConfig timeoutConfig;

    @Data
    public static class TimeoutConfig {
        private Long gapTimeoutMs;
        private Long maxTimeoutMs;
    }

    /**
     * 对话消息内部类，封装角色、内容及时序元信息
     */
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {

        /** 消息角色，如 "user"、"assistant"、"system"、"tool" */
        private String role;

        /** 消息文本内容 */
        private String content;

        /** 所属对话轮次序号（第几轮，从 1 开始；null 表示未知） */
        private Integer turnIndex;

        /** 消息产生时间戳（epoch 毫秒；null 表示未知） */
        private Long timestamp;

        /** 消息唯一标识（来自持久层 messageUid，可空） */
        private String messageUid;

        /** 产生该消息的 Agent 类型名（可空，主要用于 assistant 消息溯源） */
        private String agentType;

        /** 仅承载 role + content 的轻量构造（兼容历史调用点） */
        public static Message of(String role, String content) {
            return Message.builder().role(role).content(content).build();
        }
    }

    /**
     * 从上下文构建请求 DTO，仅预填充基础字段
     */
    public static LlmRequestBuilder from(AiChatRequestContext ctx) {
        LlmRequestBuilder builder = LlmRequest.builder();
        if (ctx != null) {
            builder.userId(ctx.getUserId());
            builder.sessionId(ctx.getSessionId());
            if (ctx.getCurrentMessage() != null) {
                builder.userMessage(ctx.getCurrentMessage().getContent());
            }
        }
        return builder;
    }
}
