package org.ticketing_system.biz.aiservice.service;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.agent.AgentDispatcher;
import org.ticketing_system.biz.aiservice.agent.AgentExecutionResult;
import org.ticketing_system.biz.aiservice.agent.AgentPlan;
import org.ticketing_system.biz.aiservice.agent.AgentResponseAggregator;
import org.ticketing_system.biz.aiservice.agent.AgentTask;
import org.ticketing_system.biz.aiservice.agent.AgentTaskResult;
import org.ticketing_system.biz.aiservice.agent.AgentTraceEmitter;
import org.ticketing_system.biz.aiservice.agent.AgentType;
import org.ticketing_system.biz.aiservice.agent.TaskValidator;
import org.ticketing_system.biz.aiservice.agent.master.MasterAgent;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.common.enums.AiMessageType;
import org.ticketing_system.biz.aiservice.common.enums.AiStreamEventType;
import org.ticketing_system.biz.aiservice.common.util.AiBusinessContextSignals;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiMessageDO;
import org.ticketing_system.biz.aiservice.memory.ChatMemoryService;
import org.ticketing_system.biz.aiservice.model.AiChatMessage;
import org.ticketing_system.biz.aiservice.model.AiStreamChunk;
import org.ticketing_system.biz.aiservice.model.AiUserProfile;
import org.ticketing_system.biz.aiservice.session.AiSessionContextService;
import org.ticketing_system.biz.aiservice.session.context.AiSessionContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates AI chat workflow across profile, memory, session context and agents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestratorService {

    private static final String EMPTY_VALUE = "none";
    private static final int SYNTHETIC_STREAM_CHUNK_SIZE = 8;
    private static final long SYNTHETIC_STREAM_DELAY_MS = 25L;

    private final AiUserProfileService aiUserProfileService;
    private final ChatMemoryService chatMemoryService;
    private final AiSessionContextService aiSessionContextService;
    private final MasterAgent masterAgent;
    private final AgentDispatcher agentDispatcher;
    private final AgentResponseAggregator agentResponseAggregator;
    private final TaskValidator taskValidator;
    private final AiProperties aiProperties;

    /**
     * Executes the AI chat orchestration workflow.
     */
    public Flux<AiStreamChunk> orchestrate(AiChatRequestContext context, AiChatMessage userMessage) {
        context.setCurrentMessage(userMessage);
        Sinks.Many<AiStreamChunk> traceSink = Sinks.many().unicast().onBackpressureBuffer();
        AgentTraceEmitter traceEmitter = new AgentTraceEmitter(context, traceSink, isTraceEnabled());
        traceEmitter.emitStatus("WORKFLOW", "AI Agent", "Request accepted, preparing context");

        Mono<AiSessionContext> sessionContextMono = aiSessionContextService.prepare(context, userMessage)
                .doOnSubscribe(ignored -> traceEmitter.emitStatus("CONTEXT", "Session context", "Loading session context"))
                .doOnNext(ignored -> traceEmitter.emitStatus("CONTEXT", "Session context", "Session context ready"))
                .doOnNext(context::setSessionContext);
        Mono<AiUserProfile> profileMono = buildProfile(context)
                .doOnSubscribe(ignored -> traceEmitter.emitStatus("PROFILE", "User profile", "Building user profile"))
                .doOnNext(ignored -> traceEmitter.emitStatus("PROFILE", "User profile", "User profile ready"));

        Flux<AiStreamChunk> workflow = Mono.zip(sessionContextMono, profileMono)
                .flatMapMany(tuple -> {
                    AiSessionContext sessionContext = tuple.getT1();
                    AiUserProfile profile = tuple.getT2();
                    context.setSessionContext(sessionContext);
                    context.setUserProfile(profile);
                    traceEmitter.emitStatus("WORKFLOW", "AI Agent", "Building LLM request");
                    LlmRequest baseRequest = buildBaseRequest(context, profile, sessionContext, userMessage);
                    return runAgentWorkflow(context, baseRequest, traceEmitter)
                            .doOnNext(result -> persistConversationAsync(context, baseRequest, result))
                            .flatMapMany(result -> toStreamChunks(context, result));
                })
                .onErrorResume(ex -> {
                    log.error("AI orchestration failed: sessionId={}, userId={}, error={}",
                            context.getSessionId(), context.getUserId(), ex.getMessage(), ex);
                    return Flux.just(AiStreamChunk.error(ex.getMessage()));
                })
                .doFinally(signalType -> traceEmitter.complete());
        return Flux.merge(traceSink.asFlux(), workflow);
    }

    private Mono<AiUserProfile> buildProfile(AiChatRequestContext context) {
        return aiUserProfileService.validateAndBuildProfile(context.getAuthenticatedUser(), context);
    }

    private Mono<AgentExecutionResult> runAgentWorkflow(AiChatRequestContext context, LlmRequest baseRequest, AgentTraceEmitter traceEmitter) {
        if (!context.isEnableTools() || !isAgentEnabled()) {
            emitStatus(traceEmitter, "ROUTER", "Route decision", "Tools disabled, using general chat");
            AgentPlan directChatPlan = buildGeneralChatPlan(context, baseRequest, "tools-disabled");
            return agentDispatcher.dispatch(directChatPlan, traceEmitter)
                    .flatMap(results -> agentResponseAggregator.aggregate(directChatPlan, results, baseRequest, traceEmitter));
        }
        if (shouldUseFastGeneralRoute(context, baseRequest)) {
            emitStatus(traceEmitter, "ROUTER", "Route decision", "General chat route selected");
            AgentPlan fastGeneralPlan = buildGeneralChatPlan(context, baseRequest, "fast-general-route");
            return agentDispatcher.dispatch(fastGeneralPlan, traceEmitter)
                    .flatMap(results -> agentResponseAggregator.aggregate(fastGeneralPlan, results, baseRequest, traceEmitter));
        }
        emitStatus(traceEmitter, "MASTER", "Master Agent", "Planning agent tasks");
        return masterAgent.plan(context, baseRequest, traceEmitter)
                .map(plan -> taskValidator.validate(plan, context))
                .flatMap(plan -> {
                    if (plan.hasClarification()) {
                        emitStatus(traceEmitter, "MASTER", "Master Agent", "Clarification required");
                        return Mono.just(AgentExecutionResult.builder()
                                .answer(plan.getClarification())
                                .taskResults(List.of())
                                .build());
                    }
                    emitStatus(traceEmitter, "MASTER", "Master Agent", "Agent tasks planned: " + plan.getTasks().size());
                    return agentDispatcher.dispatch(plan, traceEmitter)
                            .flatMap(results -> agentResponseAggregator.aggregate(plan, results, baseRequest, traceEmitter));
                });
    }

    private AgentPlan buildGeneralChatPlan(AiChatRequestContext context, LlmRequest baseRequest, String intent) {
        return AgentPlan.builder()
                .tasks(List.of(AgentTask.builder()
                        .type(AgentType.GENERAL_CHAT)
                        .intent(intent)
                        .originalMessage(baseRequest.getUserMessage())
                        .context(context)
                        .llmRequest(baseRequest)
                        .build()))
                .needAggregation(false)
                .build();
    }

    private boolean shouldUseFastGeneralRoute(AiChatRequestContext context, LlmRequest baseRequest) {
        if (!isFastGeneralRouteEnabled() || AiBusinessContextSignals.isContextDependent(baseRequest.getUserMessage())) {
            return false;
        }
        AiSessionContext sessionContext = context.getSessionContext();
        if (sessionContext != null && AiBusinessContextSignals.hasBusinessSlot(sessionContext.getSlotState())) {
            return false;
        }
        if (sessionContext != null && AiBusinessContextSignals.hasRecentBusinessContext(sessionContext.getRecentTurns())) {
            return false;
        }
        return !AiBusinessContextSignals.hasBusinessSignal(baseRequest.getUserMessage());
    }

    private Flux<AiStreamChunk> toStreamChunks(AiChatRequestContext context, AgentExecutionResult result) {
        List<AiStreamChunk> chunks = new ArrayList<>();
        for (AgentTaskResult taskResult : result.getTaskResults()) {
            if (taskResult.getComponentType() != null && taskResult.getComponentData() != null) {
                AiStreamChunk componentChunk = AiStreamChunk.component(taskResult.getComponentType(), taskResult.getComponentData());
                componentChunk.setSessionId(context.getSessionId());
                chunks.add(componentChunk);
            }
        }
        String answer = result.getAnswer() == null ? "" : result.getAnswer();
        List<AiStreamChunk> answerChunks = new ArrayList<>();
        if (!answer.isBlank() && !result.isAnswerStreamed()) {
            answerChunks.addAll(splitAnswerChunks(context, result, answer));
        }
        AiStreamChunk doneChunk = AiStreamChunk.done(result.getUsage(), answer);
        doneChunk.setSessionId(context.getSessionId());
        return Flux.concat(
                Flux.fromIterable(chunks),
                Flux.fromIterable(answerChunks).delayElements(Duration.ofMillis(SYNTHETIC_STREAM_DELAY_MS)),
                Flux.just(doneChunk));
    }

    private List<AiStreamChunk> splitAnswerChunks(AiChatRequestContext context, AgentExecutionResult result, String answer) {
        List<AiStreamChunk> chunks = new ArrayList<>();
        int index = 0;
        while (index < answer.length()) {
            int nextIndex = Math.min(index + SYNTHETIC_STREAM_CHUNK_SIZE, answer.length());
            chunks.add(AiStreamChunk.builder()
                    .eventType(AiStreamEventType.CHAT_CHUNK)
                    .delta(answer.substring(index, nextIndex))
                    .contentType("text")
                    .messageType(AiMessageType.ASSISTANT)
                    .modelName(result.getModelName())
                    .sessionId(context.getSessionId())
                    .done(false)
                    .build());
            index = nextIndex;
        }
        return chunks;
    }

    private LlmRequest buildBaseRequest(AiChatRequestContext context,
                                        AiUserProfile profile,
                                        AiSessionContext sessionContext,
                                        AiChatMessage userMessage) {
        List<LlmRequest.Message> messages = new ArrayList<>();
        messages.add(buildCompactRuntimeContextMessage(context, profile, sessionContext));
        if (sessionContext != null && sessionContext.getRecentTurns() != null) {
            messages.addAll(sessionContext.getRecentTurns());
        }
        return LlmRequest.builder()
                .userMessage(userMessage.getContent())
                .messages(messages)
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .build();
    }

    private LlmRequest.Message buildCompactRuntimeContextMessage(AiChatRequestContext context, AiUserProfile profile, AiSessionContext sessionContext) {
        StringBuilder content = new StringBuilder();
        content.append("Runtime context for identity, preferences, and business constraints.\n");
        content.append("userId=").append(stringifyUserId(context.getUserId())).append('\n');
        appendIfPresent(content, "idType", profile == null ? null : profile.getIdType());
        appendIfPresent(content, "userType", profile == null ? null : profile.getUserType());
        appendIfPresent(content, "userTag", profile == null ? null : profile.getUserTag());
        appendIfPresent(content, "preferences", profile == null ? null : profile.getPreferences());
        if (sessionContext != null && AiBusinessContextSignals.hasBusinessSlot(sessionContext.getSlotState())) {
            content.append("slotState=").append(JSON.toJSONString(sessionContext.getSlotState())).append('\n');
        }
        if (sessionContext != null && sessionContext.getSummaryContext() != null
                && sessionContext.getSummaryContext().getFacts() != null
                && !sessionContext.getSummaryContext().getFacts().isEmpty()) {
            content.append("sessionFacts=").append(JSON.toJSONString(sessionContext.getSummaryContext())).append('\n');
        }
        content.append("Recent messages, if present, are complete recent turns. Current user message is supplied separately.");
        return LlmRequest.Message.builder()
                .role("system")
                .content(content.toString())
                .build();
    }

    private void persistConversation(AiChatRequestContext context, LlmRequest request, AgentExecutionResult result) {
        try {
            List<AiMessageDO> messagesToSave = new ArrayList<>();
            AiMessageDO userMsg = new AiMessageDO();
            userMsg.setSessionId(context.getSessionId());
            userMsg.setUserId(context.getUserId());
            userMsg.setRole(AiMessageType.USER.role());
            userMsg.setContent(request.getUserMessage());
            userMsg.setModelName(result.getModelName());
            messagesToSave.add(userMsg);

            String fullAnswer = result.getAnswer();
            if (fullAnswer != null && !fullAnswer.isBlank()) {
                AiMessageDO assistantMsg = new AiMessageDO();
                assistantMsg.setSessionId(context.getSessionId());
                assistantMsg.setUserId(context.getUserId());
                assistantMsg.setRole(AiMessageType.ASSISTANT.role());
                assistantMsg.setContent(fullAnswer);
                assistantMsg.setModelName(result.getModelName());
                messagesToSave.add(assistantMsg);
            }
            chatMemoryService.saveMessagesBatch(messagesToSave);
            aiSessionContextService.persistRecentTurns(context, request.getUserMessage(), fullAnswer);
            log.info("AI agent conversation persisted: sessionId={}, userId={}, messageCount={}",
                    context.getSessionId(), context.getUserId(), messagesToSave.size());
        } catch (Exception ex) {
            log.error("AI conversation persistence failed: sessionId={}, error={}",
                    context.getSessionId(), ex.getMessage(), ex);
        }
    }

    private void persistConversationAsync(AiChatRequestContext context, LlmRequest request, AgentExecutionResult result) {
        Mono.fromRunnable(() -> persistConversation(context, request, result))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private void appendIfPresent(StringBuilder content, String key, Object value) {
        String normalized = normalize(value, EMPTY_VALUE);
        if (!EMPTY_VALUE.equals(normalized)) {
            content.append(key).append('=').append(normalized).append('\n');
        }
    }

    private String stringifyUserId(Long userId) {
        return userId == null ? EMPTY_VALUE : String.valueOf(userId);
    }

    private String normalize(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private boolean isAgentEnabled() {
        AiProperties.Agent agentConfig = aiProperties.getAgent();
        return agentConfig == null || agentConfig.isEnabled();
    }

    private boolean isTraceEnabled() {
        AiProperties.Agent agentConfig = aiProperties.getAgent();
        return agentConfig != null && agentConfig.isTraceEnabled();
    }

    private boolean isFastGeneralRouteEnabled() {
        AiProperties.Agent agentConfig = aiProperties.getAgent();
        return agentConfig == null || agentConfig.isFastGeneralRouteEnabled();
    }

    private void emitStatus(AgentTraceEmitter traceEmitter, String stage, String label, String message) {
        if (traceEmitter != null) {
            traceEmitter.emitStatus(stage, label, message);
        }
    }
}
