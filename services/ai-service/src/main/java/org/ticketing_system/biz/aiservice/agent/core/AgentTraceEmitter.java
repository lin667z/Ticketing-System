package org.ticketing_system.biz.aiservice.agent.core;

import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.common.enums.AiMessageType;
import org.ticketing_system.biz.aiservice.common.enums.AiStreamEventType;
import org.ticketing_system.biz.aiservice.common.enums.ContentStyle;
import org.ticketing_system.biz.aiservice.dto.domain.AiStreamChunk;
import reactor.core.publisher.Sinks;

/**
 * 将临时的 Agent 追踪分片发送到当前 SSE 流中
 */
public class AgentTraceEmitter {

    private static final String CONTENT_TYPE_TEXT = "text";

    private final AiChatRequestContext context;
    private final Sinks.Many<AiStreamChunk> sink;
    private final boolean traceEnabled;

    public AgentTraceEmitter(AiChatRequestContext context, Sinks.Many<AiStreamChunk> sink) {
        this(context, sink, true);
    }

    public AgentTraceEmitter(AiChatRequestContext context, Sinks.Many<AiStreamChunk> sink, boolean traceEnabled) {
        this.context = context;
        this.sink = sink;
        this.traceEnabled = traceEnabled;
    }

    public void emitTrace(String stage, String type, AgentType agentType, String label, String delta, String reasoningDelta) {
        if (!traceEnabled) {
            return;
        }
        if (isBlank(delta) && isBlank(reasoningDelta)) {
            return;
        }
        emit(AiStreamChunk.trace(stage, type, stringify(agentType), label, delta, reasoningDelta));
    }

    public void emitStatus(String stage, String label, String message) {
        if (!traceEnabled) {
            return;
        }
        if (isBlank(message)) {
            return;
        }
        emit(AiStreamChunk.trace(stage, "STATUS", null, label, message, null));
    }

    public void emitStage(String label, String message) {
        if (isBlank(message)) {
            return;
        }
        emit(AiStreamChunk.stage(label, message));
    }

    public void emitComponent(String componentType, String componentId, String status, Object componentData) {
        AiStreamChunk chunk = AiStreamChunk.component(componentType, componentId, componentData);
        chunk.setStatus(status);
        emit(chunk);
    }

    public void emitStatus(AgentType agentType, String message) {
        if (agentType == null) {
            emitStatus("AGENT", "Agent", message);
            return;
        }
        emitStatus(agentType.name(), label(agentType), message);
    }

    /**
     * 发送聊天内容增量到前端。
     * 每条 CHAT_CHUNK 应只携带 delta 或 reasoningDelta 中的一种内容类型，
     * 不同内容类型不可混在同一 SSE 消息中，前端将据此做差异化渲染。
     */
    public void emitChatChunk(String delta, String reasoningDelta, String modelName) {
        emitChatChunk(delta, reasoningDelta, modelName, null);
    }

    /**
     * 发送聊天内容增量到前端（带渲染风格）。
     * 调用方应确保 delta 和 reasoningDelta 不同时非空。
     */
    public void emitChatChunk(String delta, String reasoningDelta, String modelName, ContentStyle contentStyle) {
        if (isBlank(delta) && isBlank(reasoningDelta)) {
            return;
        }
        AiStreamChunk chunk = AiStreamChunk.builder()
                .eventType(AiStreamEventType.CHAT_CHUNK)
                .delta(delta)
                .reasoningDelta(reasoningDelta)
                .contentType(CONTENT_TYPE_TEXT)
                .messageType(AiMessageType.ASSISTANT)
                .modelName(modelName)
                .sessionId(context.getSessionId())
                .contentStyle(contentStyle)
                .done(false)
                .build();
        emit(chunk);
    }

    public void emitRetrying(String message) {
        if (isBlank(message)) {
            return;
        }
        emit(AiStreamChunk.retrying(message));
    }

    public void emitToolStart(AgentType agentType, String message) {
        if (!traceEnabled) {
            return;
        }
        emit(AiStreamChunk.tool(AiStreamEventType.TOOL_START, stringify(agentType), stringify(agentType), label(agentType), message));
    }

    public void emitToolEnd(AgentType agentType, String message) {
        if (!traceEnabled) {
            return;
        }
        emit(AiStreamChunk.tool(AiStreamEventType.TOOL_END, stringify(agentType), stringify(agentType), label(agentType), message));
    }

    public void complete() {
        Sinks.EmitResult result = sink.tryEmitComplete();
        if (result.isFailure()) {
            sink.tryEmitError(new RuntimeException("SSE stream terminated"));
        }
    }

    private void emit(AiStreamChunk chunk) {
        chunk.setSessionId(context.getSessionId());
        Sinks.EmitResult result = sink.tryEmitNext(chunk);
        if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
            sink.tryEmitComplete();
        }
    }

    private String label(AgentType agentType) {
        if (agentType == null) {
            return "Agent";
        }
        return agentType.name() + " Agent";
    }

    private String stringify(AgentType agentType) {
        return agentType == null ? null : agentType.name();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
