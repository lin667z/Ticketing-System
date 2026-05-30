package org.ticketing_system.biz.aiservice.agent;

import org.junit.jupiter.api.Test;
import org.ticketing_system.biz.aiservice.client.LlmClient;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.client.dto.LlmStreamResponse;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.common.enums.AiStreamEventType;
import org.ticketing_system.biz.aiservice.common.enums.LlmStreamResponseType;
import org.ticketing_system.biz.aiservice.model.AiStreamChunk;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLlmServiceTest {

    @Test
    void completeEmitsTraceAndAggregatesAnswer() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.streamChat(request())).thenReturn(Flux.just(
                content("he", "think"),
                content("llo", null)));
        AgentLlmService service = new AgentLlmService(llmClient);
        List<AiStreamChunk> emitted = emittedChunks();

        AgentLlmResponse response = service.complete(request(), emitter(emitted), "MASTER", AgentType.GENERAL_CHAT, "Master Agent", false).block();

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("hello");

        assertThat(emitted)
                .extracting(AiStreamChunk::getEventType)
                .containsOnly(AiStreamEventType.TRACE);
        assertThat(emitted)
                .extracting(AiStreamChunk::getDelta)
                .contains("he", "llo");
        assertThat(emitted)
                .extracting(AiStreamChunk::getReasoningDelta)
                .contains("think");
    }

    @Test
    void completeCanStreamFinalAnswerChunks() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.streamChat(request())).thenReturn(Flux.just(content("ok", "reason")));
        AgentLlmService service = new AgentLlmService(llmClient);
        List<AiStreamChunk> emitted = emittedChunks();

        AgentLlmResponse response = service.complete(request(), emitter(emitted), "AGGREGATOR", null, "Aggregator Agent", true).block();

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("ok");

        assertThat(emitted)
                .extracting(AiStreamChunk::getEventType)
                .contains(AiStreamEventType.TRACE, AiStreamEventType.CHAT_CHUNK);
    }

    @Test
    void completeSuppressesTraceWhenTraceEmitterDisablesIt() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.streamChat(request())).thenReturn(Flux.just(content("ok", "reason")));
        AgentLlmService service = new AgentLlmService(llmClient);
        List<AiStreamChunk> emitted = emittedChunks();

        AgentLlmResponse response = service.complete(request(), emitter(emitted, false), "MASTER", AgentType.GENERAL_CHAT, "Master Agent", true).block();

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("ok");
        assertThat(emitted)
                .extracting(AiStreamChunk::getEventType)
                .containsOnly(AiStreamEventType.CHAT_CHUNK);
    }

    private LlmRequest request() {
        return LlmRequest.builder()
                .userMessage("hello")
                .sessionId(1L)
                .userId(1L)
                .build();
    }

    private LlmStreamResponse content(String delta, String reasoningDelta) {
        return LlmStreamResponse.builder()
                .type(LlmStreamResponseType.CONTENT)
                .delta(delta)
                .reasoningDelta(reasoningDelta)
                .modelName("test-model")
                .build();
    }

    private AgentTraceEmitter emitter(List<AiStreamChunk> emitted) {
        return emitter(emitted, true);
    }

    private AgentTraceEmitter emitter(List<AiStreamChunk> emitted, boolean traceEnabled) {
        Sinks.Many<AiStreamChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
        sink.asFlux().subscribe(emitted::add);
        AiChatRequestContext context = AiChatRequestContext.builder()
                .sessionId(1L)
                .build();
        return new AgentTraceEmitter(context, sink, traceEnabled);
    }

    private List<AiStreamChunk> emittedChunks() {
        return new CopyOnWriteArrayList<>();
    }
}
