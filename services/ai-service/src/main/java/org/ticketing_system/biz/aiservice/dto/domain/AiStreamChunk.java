package org.ticketing_system.biz.aiservice.dto.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.common.enums.AiMessageType;
import org.ticketing_system.biz.aiservice.common.enums.AiStreamEventType;
import org.ticketing_system.biz.aiservice.common.enums.ContentStyle;

import java.util.Map;

/**
 * AI 流式响应分片
 * 包含每一块增量文本内容或工具调用状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStreamChunk {

    /**
     * 增量内容 (Delta)
     */
    private String delta;

    /**
     * 推理增量内容
     */
    private String reasoningDelta;

    /**
     * 完整回答 (仅在 DONE 阶段返回)
     */
    private String answer;

    /**
     * 消息类型 (user, assistant 等)
     */
    private AiMessageType messageType;

    /**
     * 事件类型 (CHAT_CHUNK, COMPONENT, DONE, ERROR 等)
     */
    private AiStreamEventType eventType;

    /**
     * 会话 ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    /**
     * 使用的模型名称
     */
    private String modelName;

    /**
     * 模型返回的完成原因
     */
    private String finishReason;

    /**
     * 模型返回的令牌使用情况
     */
    private Map<String, Object> usage;

    /**
     * 是否结束
     */
    private Boolean done;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 内容类型（向后兼容：null 或 "text" 视为纯文本 Markdown）
     * - "text"      : 纯文本/Markdown 增量（默认）
     * - "component" : 结构化组件数据（完整 JSON，不增量传输）
     */
    private String contentType;

    /**
     * 组件类型，如 "train-card"、"order-card"
     * 仅当 contentType 为 "component" 时有效
     */
    private String componentType;

    /**
     * 组件数据，结构化组件的完整数据
     * 仅当 contentType 为 "component" 时有效
     */
    private Object componentData;

    private String traceStage;

    private String traceType;

    private String agentType;

    private String traceLabel;

    private String componentId;

    private String status;

    /**
     * 内容渲染风格，提示前端差异化渲染文本
     * 例如 greeting（欢迎语）、clarification（追问）、summary（结果摘要）、suggestion（操作建议）等
     */
    private ContentStyle contentStyle;

    /**
     * 构建普通对话增量分片
     *
     * @param delta 增量文本
     * @return CHAT_CHUNK 类型的流分片
     */
    public static AiStreamChunk chatChunk(String delta) {
        return chatChunk(delta, null);
    }

    /**
     * 构建带渲染风格的对话增量分片
     *
     * @param delta        增量文本
     * @param contentStyle 内容渲染风格
     * @return CHAT_CHUNK 类型的流分片
     */
    public static AiStreamChunk chatChunk(String delta, ContentStyle contentStyle) {
        return AiStreamChunk.builder()
                .eventType(AiStreamEventType.CHAT_CHUNK)
                .delta(delta)
                .contentType("text")
                .messageType(AiMessageType.ASSISTANT)
                .contentStyle(contentStyle)
                .done(false)
                .build();
    }

    /**
     * 构建流完成分片
     *
     * @param usage      Token 用量信息
     * @param fullAnswer 完整回答文本
     * @return DONE 类型的流分片
     */
    public static AiStreamChunk done(Map<String, Object> usage, String fullAnswer) {
        return AiStreamChunk.builder()
                .eventType(AiStreamEventType.DONE)
                .answer(fullAnswer)
                .usage(usage)
                .messageType(AiMessageType.ASSISTANT)
                .done(true)
                .build();
    }

    /**
     * 构建错误分片
     *
     * @param message 错误消息
     * @return ERROR 类型的流分片
     */
    public static AiStreamChunk error(String message) {
        return AiStreamChunk.builder()
                .eventType(AiStreamEventType.ERROR)
                .messageType(AiMessageType.ASSISTANT)
                .done(true)
                .error(message)
                .build();
    }

    /**
     * 构建重试提示分片
     *
     * @param message 提示消息，如 "网络波动，重试中..."
     * @return RETRYING 类型的流分片
     */
    public static AiStreamChunk retrying(String message) {
        return AiStreamChunk.builder()
                .eventType(AiStreamEventType.RETRYING)
                .delta(message)
                .messageType(AiMessageType.ASSISTANT)
                .done(false)
                .build();
    }

    /**
     * 构建结构化组件分片
     *
     * @param componentType 组件类型（如 "train-card"、"order-card"）
     * @param componentData 组件数据
     * @return COMPONENT 类型的流分片
     */
    public static AiStreamChunk trace(String traceStage, String traceType, String agentType, String traceLabel, String delta, String reasoningDelta) {
        return AiStreamChunk.builder()
                .eventType(AiStreamEventType.TRACE)
                .contentType("trace")
                .traceStage(traceStage)
                .traceType(traceType)
                .agentType(agentType)
                .traceLabel(traceLabel)
                .delta(delta)
                .reasoningDelta(reasoningDelta)
                .messageType(AiMessageType.ASSISTANT)
                .done(false)
                .build();
    }

    public static AiStreamChunk tool(AiStreamEventType eventType, String traceStage, String agentType, String traceLabel, String message) {
        return AiStreamChunk.builder()
                .eventType(eventType)
                .contentType("trace")
                .traceStage(traceStage)
                .traceType("STATUS")
                .agentType(agentType)
                .traceLabel(traceLabel)
                .delta(message)
                .messageType(AiMessageType.ASSISTANT)
                .done(false)
                .build();
    }

    public static AiStreamChunk component(String componentType, String componentId, Object componentData) {
        return AiStreamChunk.builder()
                .eventType(AiStreamEventType.COMPONENT)
                .contentType("component")
                .componentType(componentType)
                .componentId(componentId)
                .componentData(componentData)
                .messageType(AiMessageType.ASSISTANT)
                .done(false)
                .build();
    }

    public static AiStreamChunk stage(String label, String message) {
        return AiStreamChunk.builder()
                .eventType(AiStreamEventType.STAGE)
                .traceLabel(label)
                .delta(message)
                .messageType(AiMessageType.ASSISTANT)
                .done(false)
                .build();
    }
}
