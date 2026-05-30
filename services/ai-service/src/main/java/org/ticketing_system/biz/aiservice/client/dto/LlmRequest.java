package org.ticketing_system.biz.aiservice.client.dto;

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

    /**
     * 对话消息内部类，封装角色和内容
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {

        /** 消息角色，如 "user"、"assistant"、"system"、"tool" */
        private String role;

        /** 消息文本内容 */
        private String content;
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
