package org.ticketing_system.biz.aiservice.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.agent.model.AgentTask;
import org.ticketing_system.biz.aiservice.agent.model.AgentTaskResult;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiEpisodeDO;
import org.ticketing_system.biz.aiservice.dao.entity.AiMemoryDO;
import org.ticketing_system.biz.aiservice.memory.episodic.EpisodicSummaryService;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEvent;
import org.ticketing_system.biz.aiservice.memory.knowledge.KnowledgeRetriever;
import org.ticketing_system.biz.aiservice.memory.longterm.LongTermMemoryService;
import org.ticketing_system.biz.aiservice.memory.working.WorkingMemoryService;
import org.ticketing_system.biz.aiservice.memory.working.WorkingMemoryState;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 记忆门面（编排器的唯一记忆入口）。
 *
 * <p>统一协调五层记忆：</p>
 * <ul>
 *   <li>L1 工作记忆（{@link WorkingMemoryService}）：会话状态、槽位、摘要、最近轮次、累积事件；</li>
 *   <li>L2 对话历史：由 WorkingMemoryService 在重建时读取（真相源）；</li>
 *   <li>L3 情景记忆（{@link EpisodicSummaryService}）：会话 finalize 派生情节；</li>
 *   <li>L4 长期记忆（{@link LongTermMemoryService}）：跨会话结构化偏好；</li>
 *   <li>L5 知识/RAG（{@link KnowledgeRetriever}）：铁路规则检索（预留）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryFacade {

    private final WorkingMemoryService workingMemoryService;
    private final EpisodicSummaryService episodicSummaryService;
    private final LongTermMemoryService longTermMemoryService;
    private final KnowledgeRetriever knowledgeRetriever;
    private final AiProperties aiProperties;

    // ==================== 读：本轮上下文准备与组装 ====================

    /**
     * 准备本轮工作记忆（L1）。
     */
    public Mono<WorkingMemoryState> prepareWorkingMemory(AiChatRequestContext context) {
        return workingMemoryService.prepare(context);
    }

    /**
     * 组装本轮完整记忆上下文：L1 工作记忆 + L4 偏好 + L5 规则片段（+ 可选 L3 跨会话情节）。
     */
    public MemoryContext assemble(AiChatRequestContext context, WorkingMemoryState workingMemory) {
        Long userId = context.getUserId();
        List<AiMemoryDO> preferences = retrievePreferences(userId);
        List<String> ruleChunks = retrieveRules(userId, currentMessage(context));

        MemoryContext.MemoryContextBuilder builder = MemoryContext.builder()
                .workingMemory(workingMemory)
                .longTermPreferences(preferences)
                .ruleChunks(ruleChunks);

        if (isCrossSessionInjectEnabled()) {
            builder.recentEpisodes(retrieveRecentEpisodes(userId));
        }
        return builder.build();
    }

    /**
     * 查询用户长期偏好（L4），按权重 Top-K。
     */
    public List<AiMemoryDO> retrievePreferences(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return longTermMemoryService.queryForPrompt(userId, null);
    }

    /**
     * 检索铁路规则片段（L5，预留；未启用时返回空）。
     */
    public List<String> retrieveRules(Long userId, String query) {
        AiProperties.Knowledge cfg = aiProperties.getKnowledge();
        int topK = cfg == null ? 0 : cfg.getTopK();
        return knowledgeRetriever.retrieve(userId, query, topK);
    }

    /**
     * 查询近期情节（L3，跨会话注入开启时使用）。
     */
    public List<AiEpisodeDO> retrieveRecentEpisodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        int limit = getMaxEpisodesPerUser();
        return episodicSummaryService.queryByUserId(userId, limit);
    }

    // ==================== 写：每轮记录与会话 finalize ====================

    /**
     * 记录一轮对话到 L1：持久化最近轮次 + 累积本轮域事件；达阈值则触发兜底 finalize。
     */
    public void recordTurn(AiChatRequestContext context, String userMessage, String assistantAnswer) {
        Long userId = context.getUserId();
        Long sessionId = context.getSessionId();
        List<SessionEvent> events = context.getEventCollector() == null
                ? List.of() : context.getEventCollector().getEvents();
        workingMemoryService.recordTurn(userId, sessionId, userMessage, assistantAnswer, events);
        maybeFinalizeOnThreshold(userId, sessionId);
    }

    /**
     * 会话结束（如删除对话）时 finalize：从 L1 取未派生事件生成情节（L3），并触发自动消化（L4）。
     */
    public void finalizeSession(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        WorkingMemoryState state = workingMemoryService.loadOrCreate(userId, sessionId, false);
        finalizeFromState(userId, sessionId, state);
    }

    /**
     * 达阈值兜底 finalize：避免长会话从不落地情节。
     *
     * <p>触发条件改为「未 finalize 事件数 ≥ 阈值」，而非原先的 {@code turnCount % minTurns == 0}：
     * 后者一旦因 Redis 抖动重建或并发使 turnCount 跳过整除点就会漏触发，按未派生事件累积量判断更稳。</p>
     */
    private void maybeFinalizeOnThreshold(Long userId, Long sessionId) {
        WorkingMemoryState state = workingMemoryService.loadOrCreate(userId, sessionId, false);
        if (!isAutoFinalizeEnabled() || !state.hasUnfinalizedEvents()) {
            return;
        }
        int minTurns = getFinalizeMinTurns();
        int unfinalizedCount = state.getUnfinalizedEvents().size();
        if (minTurns > 0 && unfinalizedCount >= minTurns) {
            finalizeFromState(userId, sessionId, state);
        }
    }

    /**
     * 从工作记忆状态取未 finalize 事件派生情节。
     *
     * <p>游标推进改到异步派生<strong>成功之后</strong>，且只推进实际已派生的事件数：
     * 原先「先同步推进游标 + save，再异步派生」一旦派生失败（仅 log 吞掉），事件会被永久跳过、情节静默丢失。
     * 现在派生成功后在锁内重新读取最新状态推进游标，避免覆盖并发轮新追加的事件。</p>
     */
    private void finalizeFromState(Long userId, Long sessionId, WorkingMemoryState state) {
        if (state == null || !state.hasUnfinalizedEvents()) {
            return;
        }
        List<SessionEvent> events = state.getUnfinalizedEvents();
        int finalizedCount = state.getFinalizedEventCursor() + events.size();
        int turnEnd = state.getMeta() == null ? events.size() : state.getMeta().getTurnCount();
        int turnStart = Math.max(0, turnEnd - events.size());
        Mono.fromRunnable(() -> {
                    try {
                        episodicSummaryService.finalizeEpisode(userId, sessionId, events, turnStart, turnEnd);
                        // 仅在情节真正落库后推进游标，且在锁内基于最新状态推进，不回退、不覆盖并发新事件
                        workingMemoryService.mutate(userId, sessionId,
                                latest -> latest.advanceFinalizedCursorTo(finalizedCount));
                    } catch (Exception ex) {
                        log.warn("Episode finalize failed (cursor not advanced, will retry next turn): "
                                        + "userId={}, sessionId={}, error={}",
                                userId, sessionId, ex.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    // ==================== 挂起任务 / 澄清 / 缓冲结果（透传 L1） ====================

    public void savePendingTasks(Long userId, Long sessionId, List<AgentTask> pendingTasks, String clarification) {
        workingMemoryService.savePendingTasks(userId, sessionId, pendingTasks, clarification);
    }

    public void saveCompletedTaskResults(Long userId, Long sessionId, List<AgentTaskResult> results) {
        workingMemoryService.saveCompletedTaskResults(userId, sessionId, results);
    }

    /**
     * 成功收尾时一次性清理挂起任务 + 缓冲结果 + 澄清状态，合并为单次加锁读写。
     * 取代编排器原先三次独立 clear* 调用（三次 load-modify-save）。
     */
    public void clearBufferedConversationState(Long userId, Long sessionId) {
        workingMemoryService.clearBufferedConversationState(userId, sessionId);
    }

    public int getMaxClarificationCount() {
        return workingMemoryService.getMaxClarificationCount();
    }

    /**
     * 将 Master Agent 规划并经校验归一化后的多线路槽位状态反写回 L1 工作记忆。
     * 这是槽位的唯一写入路径，取代了原先独立的槽位抽取-合并 LLM 链路。
     */
    public void updateSlotState(Long userId, Long sessionId, SessionSlotState slotState) {
        workingMemoryService.updateSlotState(userId, sessionId, slotState);
    }

    // ==================== 配置读取 ====================

    private String currentMessage(AiChatRequestContext context) {
        return context.getCurrentMessage() == null ? null : context.getCurrentMessage().getContent();
    }

    private boolean isAutoFinalizeEnabled() {
        AiProperties.Episodic ep = aiProperties.getEpisodic();
        return ep == null || ep.isAutoFinalize();
    }

    private int getFinalizeMinTurns() {
        AiProperties.Episodic ep = aiProperties.getEpisodic();
        return ep == null ? 4 : ep.getFinalizeMinTurns();
    }

    private int getMaxEpisodesPerUser() {
        AiProperties.Episodic ep = aiProperties.getEpisodic();
        return ep == null ? 20 : ep.getMaxEpisodesPerUser();
    }

    private boolean isCrossSessionInjectEnabled() {
        AiProperties.Episodic ep = aiProperties.getEpisodic();
        return ep != null && ep.isCrossSessionInject();
    }
}
