package org.ticketing_system.biz.aiservice.service;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.agent.core.AgentDispatcher;
import org.ticketing_system.biz.aiservice.agent.core.AgentResponseAggregator;
import org.ticketing_system.biz.aiservice.agent.core.AgentTraceEmitter;
import org.ticketing_system.biz.aiservice.agent.core.AgentType;
import org.ticketing_system.biz.aiservice.agent.core.TaskValidator;
import org.ticketing_system.biz.aiservice.agent.master.MasterAgent;
import org.ticketing_system.biz.aiservice.agent.model.AgentPlan;
import org.ticketing_system.biz.aiservice.agent.model.AgentTask;
import org.ticketing_system.biz.aiservice.agent.model.AgentTaskResult;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.common.enums.AiMessageType;
import org.ticketing_system.biz.aiservice.common.enums.AiStreamEventType;
import org.ticketing_system.biz.aiservice.common.enums.ContentStyle;
import org.ticketing_system.biz.aiservice.common.exception.AiServiceException;
import org.ticketing_system.biz.aiservice.common.util.AiBusinessContextSignals;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiMessageDO;
import org.ticketing_system.biz.aiservice.dto.domain.AiStreamChunk;
import org.ticketing_system.biz.aiservice.dto.domain.AiUserProfile;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.memory.ChatMemoryService;
import org.ticketing_system.biz.aiservice.memory.MemoryContext;
import org.ticketing_system.biz.aiservice.memory.MemoryFacade;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEvent;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEventCollector;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEventType;
import org.ticketing_system.biz.aiservice.memory.execution.ExecutionContext;
import org.ticketing_system.biz.aiservice.memory.working.WorkingMemoryState;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 编排服务，协调个人资料、记忆、会话上下文与多智能体的完整对话流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestratorService {

    private static final String EMPTY_VALUE = "none";
    private static final String EMPTY_RESULT_MESSAGE = "暂时没有可返回的处理结果，请稍后再试或补充更多信息。";

    // 用户资料服务
    private final AiUserProfileService aiUserProfileService;
    // 聊天记忆服务（L2 持久化）
    private final ChatMemoryService chatMemoryService;
    // 记忆门面（编排器访问记忆子系统的唯一入口）
    private final MemoryFacade memoryFacade;
    // 主智能体
    private final MasterAgent masterAgent;
    // 智能体调度器
    private final AgentDispatcher agentDispatcher;
    // 响应聚合器
    private final AgentResponseAggregator agentResponseAggregator;
    // 任务校验器
    private final TaskValidator taskValidator;
    // AI 配置属性
    private final AiProperties aiProperties;

    /**
     * 执行 AI 聊天编排主流程
     */
    public Flux<AiStreamChunk> orchestrate(AiChatRequestContext context) {
        Sinks.Many<AiStreamChunk> traceSink = Sinks.many().unicast().onBackpressureBuffer();
        AgentTraceEmitter traceEmitter = new AgentTraceEmitter(context, traceSink, isTraceEnabled());
        traceEmitter.emitStage("AI Agent", "Request accepted, preparing context");

        ExecutionContext execCtx = new ExecutionContext();
        SessionEventCollector eventCollector = new SessionEventCollector();
        context.setExecutionContext(execCtx);
        context.setEventCollector(eventCollector);

        Mono<WorkingMemoryState> workingMemoryMono = memoryFacade.prepareWorkingMemory(context);
        Mono<AiUserProfile> profileMono = buildProfile(context);

        traceEmitter.emitStage("Working memory", "Preparing working memory");
        traceEmitter.emitStage("User profile", "Building user profile");

        AtomicReference<String> answerRef = new AtomicReference<>("");
        AtomicReference<String> modelNameRef = new AtomicReference<>();
        AtomicReference<Map<String, Object>> usageRef = new AtomicReference<>(Map.of());

        Flux<AiStreamChunk> workflow = Mono.zip(workingMemoryMono, profileMono)
                .flatMapMany(tuple -> {
                    WorkingMemoryState wm = tuple.getT1();
                    AiUserProfile profile = tuple.getT2();
                    traceEmitter.emitStage("Working memory", "Working memory ready");
                    context.setWorkingMemory(wm);
                    traceEmitter.emitStage("User profile", "User profile ready");
                    context.setUserProfile(profile);
                    MemoryContext memoryContext = memoryFacade.assemble(context, wm);
                    context.setMemoryContext(memoryContext);
                    traceEmitter.emitStage("AI Agent", "Building LLM request");
                    LlmRequest baseRequest = buildBaseRequest(context, profile, memoryContext);

                    if (wm != null && wm.exceedsClarificationLimit(memoryFacade.getMaxClarificationCount())) {
                        return Flux.just(AiStreamChunk.error("您已经连续询问多次了，请尝试更具体地描述您的需求。"));
                    }

                    return runAgentWorkflowAsFlux(context, baseRequest, wm, traceEmitter, execCtx, eventCollector,
                            answerRef, modelNameRef, usageRef);
                })
                .onErrorResume(ex -> {
                    log.error("AI orchestration failed: sessionId={}, userId={}, error={}",
                            context.getSessionId(), context.getUserId(), ex.getMessage(), ex);
                    return Flux.just(AiStreamChunk.error(resolveUserFriendlyError(ex)));
                })
                .doFinally(signalType -> traceEmitter.complete());

        Flux<AiStreamChunk> traceFlux = traceSink.asFlux()
                .doOnNext(chunk -> {
                    if (chunk.getDelta() != null && chunk.getEventType() == AiStreamEventType.CHAT_CHUNK) {
                        answerRef.updateAndGet(a -> a + chunk.getDelta());
                    }
                    if (chunk.getModelName() != null) {
                        modelNameRef.compareAndSet(null, chunk.getModelName());
                    }
                    if (chunk.getUsage() != null && !chunk.getUsage().isEmpty()) {
                        usageRef.set(chunk.getUsage());
                    }
                });
        return Flux.merge(traceFlux, workflow);
    }

    /**
     * 构建用户资料
     */
    private Mono<AiUserProfile> buildProfile(AiChatRequestContext context) {
        return aiUserProfileService.validateAndBuildProfile(context.getAuthenticatedUser(), context);
    }

    /**
     * 运行多智能体工作流
     */
    private Flux<AiStreamChunk> runAgentWorkflowAsFlux(AiChatRequestContext context,
                                                       LlmRequest baseRequest,
                                                       WorkingMemoryState workingMemoryState,
                                                       AgentTraceEmitter traceEmitter,
                                                       ExecutionContext execCtx,
                                                       SessionEventCollector eventCollector,
                                                       AtomicReference<String> answerRef,
                                                       AtomicReference<String> modelNameRef,
                                                       AtomicReference<Map<String, Object>> usageRef) {
        if (!context.isEnableTools() || !isAgentEnabled()) {
            traceEmitter.emitStage("ROUTER", "Route decision: Tools disabled, using general chat");
            AgentPlan directChatPlan = buildGeneralChatPlan(context, baseRequest, "tools-disabled", execCtx, eventCollector);
            return dispatchAndAggregate(directChatPlan, context, baseRequest, traceEmitter,
                    answerRef, modelNameRef, usageRef, List.of(), false);
        }
        if (shouldUseFastGeneralRoute(context, baseRequest)) {
            traceEmitter.emitStage("ROUTER", "Route decision: General chat route selected");
            AgentPlan fastGeneralPlan = buildGeneralChatPlan(context, baseRequest, "fast-general-route", execCtx, eventCollector);
            return dispatchAndAggregate(fastGeneralPlan, context, baseRequest, traceEmitter,
                    answerRef, modelNameRef, usageRef, List.of(), false);
        }
        if (workingMemoryState != null && workingMemoryState.hasPendingTasks()) {
            traceEmitter.emitStage("Pending Tasks", "Found pending tasks, attempting resume");
            return resumePendingWorkflow(workingMemoryState, context, baseRequest, traceEmitter, execCtx,
                    eventCollector, answerRef, modelNameRef, usageRef);
        }
        traceEmitter.emitStage("Master Agent", "Planning agent tasks");
        return masterAgent.plan(context, baseRequest, traceEmitter)
                .flatMapMany(plan -> handleValidatedPlan(plan, context, baseRequest, traceEmitter, execCtx,
                        eventCollector, answerRef, modelNameRef, usageRef, false, List.of()));
    }

    /**
     * 存在挂起任务时的跟进轮：不再直接复用旧的 pendingTasks 计划（那样无法把用户本轮补充的信息
     * 并入对应线路），而是让 Master Agent 作为唯一 NLU 重新规划。Master 已通过注入的「会话已知/
     * 待补全线路」上下文感知到挂起线路，会把本轮补充（如只补了日期）合并到语义对应的那条线路。
     * 此前已执行并缓冲的结果（mixed plan 的 ready 部分）在此一并带入，最终汇总为一次完整回复。
     */
    private Flux<AiStreamChunk> resumePendingWorkflow(WorkingMemoryState workingMemoryState,
                                                      AiChatRequestContext context,
                                                      LlmRequest baseRequest,
                                                      AgentTraceEmitter traceEmitter,
                                                      ExecutionContext execCtx,
                                                      SessionEventCollector eventCollector,
                                                      AtomicReference<String> answerRef,
                                                      AtomicReference<String> modelNameRef,
                                                      AtomicReference<Map<String, Object>> usageRef) {
        List<AgentTaskResult> bufferedResults = workingMemoryState.getCompletedTaskResults();
        traceEmitter.emitStage("Master Agent", "Re-planning with pending route context");
        return masterAgent.plan(context, baseRequest, traceEmitter)
                .flatMapMany(plan -> handleValidatedPlan(plan, context, baseRequest, traceEmitter, execCtx,
                        eventCollector, answerRef, modelNameRef, usageRef, true, bufferedResults));
    }

    private Flux<AiStreamChunk> handleValidatedPlan(AgentPlan plan,
                                                    AiChatRequestContext context,
                                                    LlmRequest baseRequest,
                                                    AgentTraceEmitter traceEmitter,
                                                    ExecutionContext execCtx,
                                                    SessionEventCollector eventCollector,
                                                    AtomicReference<String> answerRef,
                                                    AtomicReference<String> modelNameRef,
                                                    AtomicReference<Map<String, Object>> usageRef,
                                                    boolean appendBufferedResults,
                                                    List<AgentTaskResult> bufferedResults) {
        plan = taskValidator.validate(plan, context);
        persistDerivedSlotState(context, plan);
        injectExecutionContext(plan, execCtx, eventCollector);
        if (plan.isMixedPlan()) {
            traceEmitter.emitStage("Master Agent", "Ready tasks buffered while clarification is required");
            return bufferReadyTasksAndClarify(plan, context, baseRequest, traceEmitter, answerRef,
                    appendBufferedResults, bufferedResults);
        }
        if (plan.hasPendingTasks()) {
            traceEmitter.emitStage("Master Agent", "Clarification required");
            return emitClarificationOnly(plan, context, baseRequest, eventCollector, answerRef);
        }
        traceEmitter.emitStage("Master Agent", "Agent tasks planned: " + plan.getReadyTasks().size());
        AgentPlan executablePlan = AgentPlan.builder()
                .readyTasks(plan.getReadyTasks())
                .needAggregation(plan.isNeedAggregation() || (bufferedResults != null && !bufferedResults.isEmpty()))
                .build()
                .normalize();
        return dispatchAndAggregate(executablePlan, context, baseRequest, traceEmitter,
                answerRef, modelNameRef, usageRef, bufferedResults, appendBufferedResults);
    }

    private Flux<AiStreamChunk> bufferReadyTasksAndClarify(AgentPlan plan,
                                                           AiChatRequestContext context,
                                                           LlmRequest baseRequest,
                                                           AgentTraceEmitter traceEmitter,
                                                           AtomicReference<String> answerRef,
                                                           boolean appendBufferedResults,
                                                           List<AgentTaskResult> bufferedResults) {
        AgentPlan readyPlan = AgentPlan.readyOnly(plan.getReadyTasks());
        traceEmitter.emitStage("Agent Dispatcher", "Mixed plan detected, executing ready tasks in background buffer");
        return dispatchReadyTasks(readyPlan, traceEmitter)
                .collectList()
                .flatMapMany(results -> {
                    List<AgentTaskResult> merged = new ArrayList<>();
                    if (appendBufferedResults && bufferedResults != null) {
                        merged.addAll(bufferedResults);
                    }
                    merged.addAll(results);
                    memoryFacade.saveCompletedTaskResults(context.getUserId(), context.getSessionId(), merged);
                    memoryFacade.savePendingTasks(context.getUserId(), context.getSessionId(), plan.getPendingTasks(), plan.getClarification());
                    return emitClarificationOnly(plan, context, baseRequest, context.getEventCollector(), answerRef);
                })
                .onErrorResume(ex -> {
                    log.error("Buffered ready-task execution failed: sessionId={}, error={}",
                            context.getSessionId(), ex.getMessage(), ex);
                    return Flux.just(AiStreamChunk.error(resolveUserFriendlyError(ex)))
                            .concatWith(Flux.just(buildDoneChunk(context, null, null)));
                });
    }

    private Flux<AiStreamChunk> emitClarificationOnly(AgentPlan plan,
                                                      AiChatRequestContext context,
                                                      LlmRequest baseRequest,
                                                      SessionEventCollector eventCollector,
                                                      AtomicReference<String> answerRef) {
        String clarification = plan.getClarification();
        if (clarification == null || clarification.isBlank()) {
            clarification = "请补充当前任务所需信息。";
        }
        String finalClarification = clarification;
        eventCollector.record(SessionEvent.of(
                SessionEventType.SYSTEM_CLARIFIED,
                0,
                Map.of("reason", finalClarification)));
        answerRef.set(finalClarification);
        persistConversationAsync(context, baseRequest, finalClarification, null, Map.of());
        return Flux.just(AiStreamChunk.chatChunk(finalClarification, ContentStyle.CLARIFICATION))
                .concatWith(Flux.just(buildDoneChunk(context, null, finalClarification)));
    }

    private Flux<AgentTaskResult> dispatchReadyTasks(AgentPlan plan, AgentTraceEmitter traceEmitter) {
        traceEmitter.emitStage("Agent Dispatcher", "正在分配任务：" + plan.getReadyTasks().size() + " 个");
        return agentDispatcher.dispatch(plan, traceEmitter)
                .doOnNext(result -> {
                    if (result.getComponentType() != null && result.getComponentData() != null) {
                        traceEmitter.emitComponent(result.getComponentType(), result.getComponentId(), "done", result.getComponentData());
                    }
                });
    }

    private Flux<AiStreamChunk> dispatchAndAggregate(AgentPlan plan,
                                                     AiChatRequestContext context,
                                                     LlmRequest baseRequest,
                                                     AgentTraceEmitter traceEmitter,
                                                     AtomicReference<String> answerRef,
                                                     AtomicReference<String> modelNameRef,
                                                     AtomicReference<Map<String, Object>> usageRef,
                                                     List<AgentTaskResult> bufferedResults,
                                                     boolean clearBufferedStateOnFinish) {
        return dispatchReadyTasks(plan, traceEmitter)
                .collectList()
                .flatMapMany(results -> {
                    List<AgentTaskResult> combinedResults = new ArrayList<>();
                    if (bufferedResults != null && !bufferedResults.isEmpty()) {
                        combinedResults.addAll(bufferedResults);
                    }
                    combinedResults.addAll(results);
                    if (combinedResults.isEmpty()) {
                        answerRef.set(EMPTY_RESULT_MESSAGE);
                        persistConversationAsync(context, baseRequest, EMPTY_RESULT_MESSAGE, null, Map.of());
                        if (clearBufferedStateOnFinish) {
                            clearBufferedConversationState(context);
                        }
                        return Flux.just(AiStreamChunk.chatChunk(EMPTY_RESULT_MESSAGE, ContentStyle.SUMMARY))
                                .concatWith(Flux.just(buildDoneChunk(context, usageRef.get(), EMPTY_RESULT_MESSAGE)));
                    }
                    return agentResponseAggregator.aggregateAsFlux(plan, combinedResults, baseRequest, traceEmitter)
                            .doOnNext(chunk -> {
                                if (chunk.getDelta() != null) {
                                    answerRef.updateAndGet(a -> a + chunk.getDelta());
                                }
                                if (chunk.getModelName() != null) {
                                    modelNameRef.set(chunk.getModelName());
                                }
                                if (chunk.getUsage() != null && !chunk.getUsage().isEmpty()) {
                                    usageRef.set(chunk.getUsage());
                                }
                            })
                            .concatWith(Flux.defer(() -> {
                                persistConversationAsync(context, baseRequest, answerRef.get(), modelNameRef.get(), usageRef.get());
                                if (clearBufferedStateOnFinish) {
                                    clearBufferedConversationState(context);
                                }
                                return Flux.just(buildDoneChunk(context, usageRef.get(), answerRef.get()));
                            }));
                })
                .onErrorResume(ex -> {
                    log.error("Agent dispatch/aggregate failed: sessionId={}, error={}",
                            context.getSessionId(), ex.getMessage(), ex);
                    String userFriendlyError = resolveUserFriendlyError(ex);
                    return Flux.just(AiStreamChunk.error(userFriendlyError))
                            .concatWith(Flux.just(buildDoneChunk(context, usageRef.get(), answerRef.get())));
                });
    }

    private void clearBufferedConversationState(AiChatRequestContext context) {
        memoryFacade.clearBufferedConversationState(context.getUserId(), context.getSessionId());
    }

    private String resolveUserFriendlyError(Throwable ex) {
        return AiServiceException.resolveType(ex).getUserMessage();
    }

    /**
     * 构建通用对话计划（免去路由判断，直接走闲聊）
     */
    private AgentPlan buildGeneralChatPlan(AiChatRequestContext context, LlmRequest baseRequest, String intent,
                                           ExecutionContext execCtx, SessionEventCollector eventCollector) {
        return AgentPlan.builder()
                .readyTasks(List.of(AgentTask.builder()
                        .type(AgentType.GENERAL_CHAT)
                        .intent(intent)
                        .originalMessage(baseRequest.getUserMessage())
                        .context(context)
                        .llmRequest(baseRequest)
                        .executionContext(execCtx)
                        .sessionEventCollector(eventCollector)
                        .build()))
                .needAggregation(false)
                .build()
                .normalize();
    }

    /**
     * 判断是否走快速通用闲聊路由
     */
    private boolean shouldUseFastGeneralRoute(AiChatRequestContext context, LlmRequest baseRequest) {
        if (!isFastGeneralRouteEnabled() || AiBusinessContextSignals.isContextDependent(baseRequest.getUserMessage())) {
            return false;
        }
        WorkingMemoryState wm = context.getWorkingMemory();
        if (wm != null && AiBusinessContextSignals.hasBusinessSlot(wm.getTicketSlot())) {
            return false;
        }
        if (wm != null && AiBusinessContextSignals.hasRecentBusinessContext(wm.getRecentMessages())) {
            return false;
        }
        return !AiBusinessContextSignals.hasBusinessSignal(baseRequest.getUserMessage());
    }

    /**
     * 将执行结果转换为流式响应块
     */
    private AiStreamChunk buildDoneChunk(AiChatRequestContext context, Map<String, Object> usage, String fullAnswer) {
        AiStreamChunk chunk = AiStreamChunk.done(usage, fullAnswer);
        chunk.setSessionId(context.getSessionId());
        return chunk;
    }

    private void persistConversation(AiChatRequestContext context, LlmRequest request, String fullAnswer, String modelName, Map<String, Object> usage) {
        try {
            List<AiMessageDO> messagesToSave = new ArrayList<>();
            AiMessageDO userMsg = new AiMessageDO();
            userMsg.setSessionId(context.getSessionId());
            userMsg.setUserId(context.getUserId());
            userMsg.setRole(AiMessageType.USER.role());
            userMsg.setContent(request.getUserMessage());
            userMsg.setModelName(modelName);
            messagesToSave.add(userMsg);

            if (fullAnswer != null && !fullAnswer.isBlank()) {
                AiMessageDO assistantMsg = new AiMessageDO();
                assistantMsg.setSessionId(context.getSessionId());
                assistantMsg.setUserId(context.getUserId());
                assistantMsg.setRole(AiMessageType.ASSISTANT.role());
                assistantMsg.setContent(fullAnswer);
                assistantMsg.setModelName(modelName);
                messagesToSave.add(assistantMsg);
            }
            chatMemoryService.saveMessagesBatch(messagesToSave);
            memoryFacade.recordTurn(context, request.getUserMessage(), fullAnswer);
            log.info("AI agent conversation persisted: sessionId={}, userId={}, messageCount={}",
                    context.getSessionId(), context.getUserId(), messagesToSave.size());
        } catch (Exception ex) {
            log.error("AI conversation persistence failed: sessionId={}, error={}",
                    context.getSessionId(), ex.getMessage(), ex);
        }
    }

    private void persistConversationAsync(AiChatRequestContext context, LlmRequest request, String fullAnswer, String modelName, Map<String, Object> usage) {
        Mono.fromRunnable(() -> persistConversation(context, request, fullAnswer, modelName, usage))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * 构建基础 LLM 请求，包含运行时上下文和历史消息
     */
    private LlmRequest buildBaseRequest(AiChatRequestContext context,
                                        AiUserProfile profile,
                                        MemoryContext memoryContext) {
        WorkingMemoryState workingMemory = memoryContext == null ? null : memoryContext.getWorkingMemory();
        List<LlmRequest.Message> messages = new ArrayList<>();
        messages.add(buildCompactRuntimeContextMessage(context, profile, memoryContext));
        if (workingMemory != null && workingMemory.getRecentMessages() != null) {
            messages.addAll(workingMemory.getRecentMessages());
        }
        return LlmRequest.builder()
                .userMessage(context.getCurrentMessage().getContent())
                .messages(messages)
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .build();
    }

    private LlmRequest.Message buildCompactRuntimeContextMessage(AiChatRequestContext context, AiUserProfile profile, MemoryContext memoryContext) {
        WorkingMemoryState workingMemory = memoryContext == null ? null : memoryContext.getWorkingMemory();
        SessionSlotState slotState = workingMemory == null ? null : workingMemory.getTicketSlot();
        StringBuilder content = new StringBuilder();
        content.append("Runtime context for identity, preferences, and business constraints.\n");
        content.append("userId=").append(stringifyUserId(context.getUserId())).append('\n');
        appendIfPresent(content, "idType", profile == null ? null : profile.getIdType());
        appendIfPresent(content, "userType", profile == null ? null : profile.getUserType());
        appendIfPresent(content, "userTag", profile == null ? null : profile.getUserTag());
        appendIfPresent(content, "preferences", profile == null ? null : profile.getPreferences());
        if (AiBusinessContextSignals.hasBusinessSlot(slotState)) {
            content.append("slotState=").append(JSON.toJSONString(slotState)).append('\n');
        }
        if (workingMemory != null && workingMemory.getSummaryContext() != null
                && workingMemory.getSummaryContext().getFacts() != null
                && !workingMemory.getSummaryContext().getFacts().isEmpty()) {
            content.append("sessionFacts=").append(JSON.toJSONString(workingMemory.getSummaryContext())).append('\n');
        }
        if (memoryContext != null && memoryContext.getRuleChunks() != null && !memoryContext.getRuleChunks().isEmpty()) {
            content.append("railwayRules=").append(JSON.toJSONString(memoryContext.getRuleChunks())).append('\n');
        }
        content.append("Recent messages, if present, are complete recent turns. Current user message is supplied separately.");
        return LlmRequest.Message.builder()
                .role("system")
                .content(content.toString())
                .build();
    }

    /**
     * 将本轮校验后的计划反推为多线路槽位状态并写回工作记忆（槽位的唯一写入路径，
     * 取代原先独立的槽位抽取-合并 LLM 链路）。失败不影响主流程。
     */
    private void persistDerivedSlotState(AiChatRequestContext context, AgentPlan validatedPlan) {
        if (context == null || validatedPlan == null) {
            return;
        }
        try {
            WorkingMemoryState wm = context.getWorkingMemory();
            SessionSlotState prior = wm == null ? null : wm.getTicketSlot();
            SessionSlotState derived = taskValidator.applyPlanToSlotState(prior, validatedPlan);
            if (wm != null) {
                wm.setTicketSlot(derived);
            }
            memoryFacade.updateSlotState(context.getUserId(), context.getSessionId(), derived);
        } catch (Exception ex) {
            log.warn("Persist derived slot state failed: sessionId={}, error={}",
                    context.getSessionId(), ex.getMessage());
        }
    }

    /**
     * 将执行上下文注入到计划中的每个任务
     */
    private void injectExecutionContext(AgentPlan plan, ExecutionContext execCtx, SessionEventCollector eventCollector) {
        if (plan == null) {
            return;
        }
        for (AgentTask task : plan.getReadyTasks()) {
            task.setExecutionContext(execCtx);
            task.setSessionEventCollector(eventCollector);
        }
        for (AgentTask task : plan.getPendingTasks()) {
            task.setExecutionContext(execCtx);
            task.setSessionEventCollector(eventCollector);
        }
    }

    /**
     * 向 StringBuilder 追加非空键值对
     */
    private void appendIfPresent(StringBuilder content, String key, Object value) {
        String normalized = normalize(value, EMPTY_VALUE);
        if (!EMPTY_VALUE.equals(normalized)) {
            content.append(key).append('=').append(normalized).append('\n');
        }
    }

    /**
     * 将用户 ID 转为字符串，空时为默认值
     */
    private String stringifyUserId(Long userId) {
        return userId == null ? EMPTY_VALUE : String.valueOf(userId);
    }

    /**
     * 对象值归一化，空值返回默认值
     */
    private String normalize(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    /**
     * 智能体功能是否启用
     */
    private boolean isAgentEnabled() {
        AiProperties.Agent agentConfig = aiProperties.getAgent();
        return agentConfig == null || agentConfig.isEnabled();
    }

    /**
     * 调用链追踪是否启用
     */
    private boolean isTraceEnabled() {
        AiProperties.Agent agentConfig = aiProperties.getAgent();
        return agentConfig != null && agentConfig.isTraceEnabled();
    }

    /**
     * 快速通用闲聊路由是否启用
     */
    private boolean isFastGeneralRouteEnabled() {
        AiProperties.Agent agentConfig = aiProperties.getAgent();
        return agentConfig == null || agentConfig.isFastGeneralRouteEnabled();
    }
}
