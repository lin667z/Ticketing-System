package org.ticketing_system.biz.aiservice.agent;

import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.common.enums.AiMessageType;
import org.ticketing_system.biz.aiservice.common.enums.AiStreamEventType;
import org.ticketing_system.biz.aiservice.model.AiStreamChunk;
import reactor.core.publisher.Sinks;

/**
 * Emits transient Agent trace chunks into the current SSE stream.
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

    public void emitStatus(AgentType agentType, String message) {
        if (agentType == null) {
            emitStatus("AGENT", "Agent", message);
            return;
        }
        emitStatus(agentType.name(), label(agentType), message);
    }

    public void emitChatChunk(String delta, String reasoningDelta, String modelName) {
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
        sink.tryEmitComplete();
    }

    private void emit(AiStreamChunk chunk) {
        chunk.setSessionId(context.getSessionId());
        sink.tryEmitNext(chunk);
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
