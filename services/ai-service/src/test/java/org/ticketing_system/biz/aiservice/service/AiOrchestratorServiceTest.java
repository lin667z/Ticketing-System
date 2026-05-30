package org.ticketing_system.biz.aiservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.ticketing_system.biz.aiservice.agent.AgentExecutionResult;
import org.ticketing_system.biz.aiservice.agent.AgentDispatcher;
import org.ticketing_system.biz.aiservice.agent.AgentPlan;
import org.ticketing_system.biz.aiservice.agent.AgentResponseAggregator;
import org.ticketing_system.biz.aiservice.agent.AgentTask;
import org.ticketing_system.biz.aiservice.agent.AgentTaskResult;
import org.ticketing_system.biz.aiservice.agent.AgentType;
import org.ticketing_system.biz.aiservice.agent.AgentTraceEmitter;
import org.ticketing_system.biz.aiservice.agent.TaskValidator;
import org.ticketing_system.biz.aiservice.agent.master.MasterAgent;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.common.enums.AiStreamEventType;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.model.AiStreamChunk;
import org.ticketing_system.biz.aiservice.session.context.AiSessionContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOrchestratorServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void toStreamChunksDoesNotDuplicateStreamedAnswer() {
        AiOrchestratorService service = new AiOrchestratorService(null, null, null, null, null, null, null, null);
        AiChatRequestContext context = AiChatRequestContext.builder()
                .sessionId(1L)
                .build();
        AgentTaskResult componentResult = AgentTaskResult.builder()
                .type(AgentType.TICKET_INFO)
                .success(true)
                .summary("ticket result")
                .componentType("train_card")
                .componentData(Map.of("trainNumber", "G1"))
                .build();
        AgentExecutionResult result = AgentExecutionResult.builder()
                .answer("already streamed")
                .answerStreamed(true)
                .taskResults(List.of(componentResult))
                .build();

        Flux<AiStreamChunk> chunks = ReflectionTestUtils.invokeMethod(service, "toStreamChunks", context, result);
        List<AiStreamChunk> chunkList = chunks.collectList().block();

        assertThat(chunkList).hasSize(2);
        assertThat(chunkList.get(0).getEventType()).isEqualTo(AiStreamEventType.COMPONENT);
        assertThat(chunkList.get(1).getEventType()).isEqualTo(AiStreamEventType.DONE);
        assertThat(chunkList.get(1).getAnswer()).isEqualTo("already streamed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obviousCasualMessageBypassesMasterAgent() {
        MasterAgent masterAgent = mock(MasterAgent.class);
        AgentDispatcher dispatcher = mock(AgentDispatcher.class);
        AgentResponseAggregator aggregator = mock(AgentResponseAggregator.class);
        AiOrchestratorService service = new AiOrchestratorService(null, null, null, masterAgent, dispatcher, aggregator, null, new AiProperties());
        AgentTaskResult taskResult = AgentTaskResult.success(AgentType.GENERAL_CHAT, "hello");
        AgentExecutionResult executionResult = AgentExecutionResult.builder()
                .answer("hello")
                .taskResults(List.of(taskResult))
                .build();
        when(dispatcher.dispatch(any(), any())).thenReturn(Mono.just(List.of(taskResult)));
        when(aggregator.aggregate(any(), any(), any(), any())).thenReturn(Mono.just(executionResult));

        Mono<AgentExecutionResult> result = ReflectionTestUtils.invokeMethod(
                service,
                "runAgentWorkflow",
                contextWithSession(AiSessionContext.builder().build()),
                request("今天天气真好啊"),
                null);

        assertThat(result.block().getAnswer()).isEqualTo("hello");
        verify(masterAgent, never()).plan(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void businessLikeMessageStillUsesMasterAgent() {
        MasterAgent masterAgent = mock(MasterAgent.class);
        AgentDispatcher dispatcher = mock(AgentDispatcher.class);
        AgentResponseAggregator aggregator = mock(AgentResponseAggregator.class);
        TaskValidator taskValidator = mock(TaskValidator.class);
        AiOrchestratorService service = new AiOrchestratorService(null, null, null, masterAgent, dispatcher, aggregator, taskValidator, new AiProperties());
        AgentPlan plan = AgentPlan.builder()
                .tasks(List.of(AgentTask.builder().type(AgentType.GENERAL_CHAT).build()))
                .build();
        AgentTaskResult taskResult = AgentTaskResult.success(AgentType.GENERAL_CHAT, "ticket");
        AgentExecutionResult executionResult = AgentExecutionResult.builder()
                .answer("ticket")
                .taskResults(List.of(taskResult))
                .build();
        when(masterAgent.plan(any(), any(), any())).thenReturn(Mono.just(plan));
        when(taskValidator.validate(any(), any())).thenReturn(plan);
        when(dispatcher.dispatch(any(), any())).thenReturn(Mono.just(List.of(taskResult)));
        when(aggregator.aggregate(any(), any(), any(), any())).thenReturn(Mono.just(executionResult));

        Mono<AgentExecutionResult> result = ReflectionTestUtils.invokeMethod(
                service,
                "runAgentWorkflow",
                contextWithSession(AiSessionContext.builder().build()),
                request("帮我查明天去北京的高铁票"),
                null);

        assertThat(result.block().getAnswer()).isEqualTo("ticket");
        verify(masterAgent).plan(any(), any(), any());
    }

    private AiChatRequestContext contextWithSession(AiSessionContext sessionContext) {
        return AiChatRequestContext.builder()
                .sessionId(1L)
                .enableTools(true)
                .sessionContext(sessionContext)
                .build();
    }

    private LlmRequest request(String message) {
        return LlmRequest.builder()
                .userMessage(message)
                .sessionId(1L)
                .userId(1L)
                .build();
    }
}
