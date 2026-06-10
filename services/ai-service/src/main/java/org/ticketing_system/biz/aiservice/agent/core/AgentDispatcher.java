package org.ticketing_system.biz.aiservice.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.model.AgentPlan;
import org.ticketing_system.biz.aiservice.agent.model.AgentTask;
import org.ticketing_system.biz.aiservice.agent.model.AgentTaskResult;
import org.ticketing_system.biz.aiservice.agent.worker.AiAgent;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 根据 AgentPlan 将任务分发给对应的子 Agent 执行
 */
@Slf4j
@Component
public class AgentDispatcher {

    private static final int DEFAULT_TASK_TIMEOUT_MS = 30000;

    private final Map<AgentType, AiAgent> agentRegistry = new EnumMap<>(AgentType.class);
    private final AiProperties aiProperties;

    public AgentDispatcher(List<AiAgent> agents, AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        for (AiAgent agent : agents) {
            agentRegistry.put(agent.getAgentType(), agent);
        }
    }

    public Flux<AgentTaskResult> dispatch(AgentPlan plan, AgentTraceEmitter traceEmitter) {
        if (plan == null || plan.getTasks() == null || plan.getTasks().isEmpty()) {
            return Flux.empty();
        }
        if (traceEmitter != null) {
            traceEmitter.emitStage("任务调度", "准备执行 Agent 任务：" + plan.getTasks().size() + " 个");
        }
        boolean streamSingleGeneralAnswer = plan.getTasks().size() == 1
                && !plan.isNeedAggregation()
                && plan.getTasks().get(0).getType() == AgentType.GENERAL_CHAT;
        return Flux.fromIterable(plan.getTasks())
                .flatMap(task -> dispatchOne(task, traceEmitter, streamSingleGeneralAnswer));
    }

    private Mono<AgentTaskResult> dispatchOne(AgentTask task, AgentTraceEmitter traceEmitter, boolean streamToUser) {
        AiAgent agent = agentRegistry.get(task.getType());
        if (agent == null) {
            return Mono.just(AgentTaskResult.failure(task.getType(), "No agent found for this task"));
        }
        if (traceEmitter != null) {
            traceEmitter.emitStage(task.getType().name() + " Agent", "任务已分配，正在执行");
        }
        emitToolStart(traceEmitter, task);
        return agent.execute(task, traceEmitter, streamToUser)
                .timeout(Duration.ofMillis(getTaskTimeoutMs()))
                .doOnNext(result -> emitToolResult(traceEmitter, result))
                .doOnNext(result -> emitToolEnd(traceEmitter, result.getType()))
                .onErrorResume(ex -> handleAgentError(task, traceEmitter, ex));
    }

    private Mono<AgentTaskResult> handleAgentError(AgentTask task, AgentTraceEmitter traceEmitter, Throwable ex) {
        log.warn("Agent task failed: type={}, error={}", task.getType(), ex.getMessage(), ex);
        AgentTaskResult result = AgentTaskResult.failure(task.getType(), "Agent task failed: " + ex.getMessage());
        emitToolResult(traceEmitter, result);
        emitToolEnd(traceEmitter, task.getType());
        return Mono.just(result);
    }

    private void emitToolStart(AgentTraceEmitter traceEmitter, AgentTask task) {
        if (traceEmitter == null || task == null) {
            return;
        }
        traceEmitter.emitToolStart(task.getType(), "Agent task started");
    }

    private void emitToolEnd(AgentTraceEmitter traceEmitter, AgentType agentType) {
        if (traceEmitter == null) {
            return;
        }
        traceEmitter.emitToolEnd(agentType, "Agent task finished");
    }

    private void emitToolResult(AgentTraceEmitter traceEmitter, AgentTaskResult result) {
        if (traceEmitter == null || result == null || result.getSummary() == null || result.getSummary().isBlank()) {
            return;
        }
        traceEmitter.emitTrace(result.getType().name(), "RESULT", result.getType(), result.getType().name() + " Agent", result.getSummary(), null);
    }

    private int getTaskTimeoutMs() {
        AiProperties.Agent agent = aiProperties.getAgent();
        return agent == null ? DEFAULT_TASK_TIMEOUT_MS : agent.getTaskTimeoutMs();
    }
}
