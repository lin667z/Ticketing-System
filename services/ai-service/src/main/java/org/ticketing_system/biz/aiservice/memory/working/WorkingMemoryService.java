package org.ticketing_system.biz.aiservice.memory.working;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.ticketing_system.biz.aiservice.agent.model.AgentTask;
import org.ticketing_system.biz.aiservice.agent.model.AgentTaskResult;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.dao.entity.AiMessageDO;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.memory.ChatMemoryService;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEvent;
import org.ticketing_system.biz.aiservice.session.SessionContextExtractor;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import org.ticketing_system.biz.aiservice.session.context.SessionSummaryContext;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 工作记忆服务（L1）：统一管理会话槽位、摘要、最近轮次、域事件、挂起任务及澄清状态。
 *
 * <p>所有写操作均通过 {@link #mutate} 模板在 per-session 分布式锁内执行「读一次—改内存—写一次」，
 * 杜绝同一会话并发请求下的丢更新（如 {@code clarificationCount} 自增、多线路槽位合并被整对象覆盖），
 * 同时把原先一轮对话内 6+ 次零散 load-modify-save 收敛为按语义聚合的单次读写。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingMemoryService {

    // Redis 键模板
    static final String WM_KEY_TEMPLATE = "ai:wm:%s:%s";
    // per-session 互斥锁键模板
    private static final String WM_LOCK_TEMPLATE = "ai:wm:lock:%s:%s";

    // 锁等待最长时间（毫秒）：拿不到则放弃加锁，降级为无锁写，保证不阻塞流式响应
    private static final long LOCK_WAIT_MS = 2000L;
    // 锁租约时间（毫秒）：到期自动释放，避免持有者异常导致死锁
    private static final long LOCK_LEASE_MS = 5000L;

    // 全局兜底 TTL 秒数
    private static final int DEFAULT_TTL_SECONDS = 86400;
    // 全局兜底最大最近消息数
    private static final int DEFAULT_MAX_RECENT_MESSAGES = 10;
    // 全局兜底最大追问次数
    private static final int DEFAULT_MAX_CLARIFICATION_COUNT = 3;
    // 全局兜底保留完整轮数
    private static final int DEFAULT_LAST_TURNS = 2;
    // 全局兜底摘要起始轮次
    private static final int DEFAULT_SUMMARY_START_TURN = 3;
    // 全局兜底全量历史保留轮数上限
    private static final int DEFAULT_FULL_HISTORY_MAX_TURNS = 10;

    // Redis 模板
    private final StringRedisTemplate stringRedisTemplate;
    // 分布式锁客户端
    private final RedissonClient redissonClient;
    // 聊天记忆服务（L2 真相源）
    private final ChatMemoryService chatMemoryService;
    // 会话摘要提取器
    private final SessionContextExtractor sessionContextExtractor;
    // AI 配置属性
    private final AiProperties aiProperties;

    /**
     * 准备本轮工作记忆：加载/重建 → 按需提取摘要 → 自增轮次 → 写回 Redis。
     * 槽位由 Master Agent 在路由规划时统一产出，事后通过 {@link #updateSlotState} 反写。
     *
     * <p>摘要提取是异步 LLM 调用，无法放进同步锁内，因此分两段：先在锁内 load 一次拿到基底，
     * 锁外做摘要提取，最后在锁内重新 load 并合并写回（写回时仅覆盖本服务负责的字段，
     * 避免冲掉并发轮可能已写入的 pendingTasks/缓冲结果）。</p>
     */
    public Mono<WorkingMemoryState> prepare(AiChatRequestContext requestContext) {
        Long userId = requestContext.getUserId();
        Long sessionId = requestContext.getSessionId();
        String userMessage = requestContext.getCurrentMessage() == null
                ? null : requestContext.getCurrentMessage().getContent();

        WorkingMemoryState base = loadOrCreate(userId, sessionId, requestContext.isNewSession());
        List<LlmRequest.Message> recentTurns = resolveRecentTurns(base, sessionId);
        SessionSlotState currentSlot = base.getTicketSlot() == null ? SessionSlotState.empty() : base.getTicketSlot();
        SessionSummaryContext priorSummary = base.getSummaryContext();
        int priorTurnCount = base.getMeta() == null ? 0 : base.getMeta().getTurnCount();

        return maybeExtractSummary(base, currentSlot, recentTurns, userMessage, userId, sessionId)
                .map(summary -> {
                    WorkingMemoryState persisted = mutate(userId, sessionId, state -> {
                        state.setTicketSlot(currentSlot);
                        state.setSummaryContext(summary);
                        state.setRecentMessages(recentTurns);
                        state.incrementTurn();
                    });
                    requestContext.setKnownTurnCount(persisted.getMeta().getTurnCount());
                    return persisted;
                });
    }

    /**
     * 规划完成后，将 Master Agent 产出并经校验归一化的槽位状态反写回工作记忆。
     * 这是槽位的唯一写入路径，取代了原先独立的槽位抽取-合并链路。
     */
    public void updateSlotState(Long userId, Long sessionId, SessionSlotState slotState) {
        if (slotState == null) {
            return;
        }
        mutate(userId, sessionId, state -> state.setTicketSlot(slotState));
    }

    /**
     * 超过摘要起始轮次后才提取会话摘要。
     */
    private Mono<SessionSummaryContext> maybeExtractSummary(WorkingMemoryState base,
                                                            SessionSlotState slotState,
                                                            List<LlmRequest.Message> recentTurns,
                                                            String userMessage,
                                                            Long userId,
                                                            Long sessionId) {
        SessionSummaryContext currentSummary = base.getSummaryContext() == null
                ? SessionSummaryContext.empty() : base.getSummaryContext();
        int nextTurn = base.getMeta() == null ? 1 : base.getMeta().getTurnCount() + 1;
        if (nextTurn <= getSummaryStartTurn()) {
            return Mono.just(sessionContextExtractor.normalize(currentSummary));
        }
        List<LlmRequest.Message> messages = new ArrayList<>(recentTurns == null ? List.of() : recentTurns);
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(LlmRequest.Message.of("user", userMessage));
        }
        return sessionContextExtractor.extract(messages, currentSummary, slotState, userId, sessionId);
    }

    /**
     * 解析最近轮次：优先用 L1 缓存，缺失则从 L2 拉取。
     */
    private List<LlmRequest.Message> resolveRecentTurns(WorkingMemoryState state, Long sessionId) {
        if (state.getRecentMessages() != null && !state.getRecentMessages().isEmpty()) {
            return new ArrayList<>(state.getRecentMessages());
        }
        return toMessages(chatMemoryService.getRecentTurns(sessionId, getLastTurns()));
    }

    // ==================== 加锁读改写模板 ====================

    /**
     * 在 per-session 分布式锁内执行「读一次—改内存—写一次」。
     *
     * <p>拿不到锁时降级为无锁读改写（仍是单次读写，仅丧失并发隔离），保证流式响应不被阻塞。
     * 返回应用变更后的最新状态供调用方继续使用。</p>
     */
    public WorkingMemoryState mutate(Long userId, Long sessionId, Consumer<WorkingMemoryState> mutation) {
        RLock lock = redissonClient.getLock(String.format(WM_LOCK_TEMPLATE, userId, sessionId));
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Acquire WM lock interrupted: userId={}, sessionId={}", userId, sessionId);
        } catch (Exception ex) {
            log.warn("Acquire WM lock failed, fallback to lock-free: userId={}, sessionId={}, error={}",
                    userId, sessionId, ex.getMessage());
        }
        try {
            WorkingMemoryState state = loadOrCreate(userId, sessionId, false);
            if (mutation != null) {
                mutation.accept(state);
            }
            save(userId, sessionId, state);
            return state;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 加载 / 保存 ====================

    /**
     * 加载或创建工作记忆（新会话返回空状态，失败时从 DB 重建）
     */
    public WorkingMemoryState loadOrCreate(Long userId, Long sessionId, boolean isNewSession) {
        if (isNewSession) {
            return WorkingMemoryState.empty();
        }
        String key = String.format(WM_KEY_TEMPLATE, userId, sessionId);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                WorkingMemoryState state = JSON.parseObject(json, WorkingMemoryState.class);
                if (state != null) {
                    return state;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load WorkingMemoryState: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage());
        }
        return rebuildFromHistory(userId, sessionId);
    }

    /**
     * 保存工作记忆到 Redis
     */
    public void save(Long userId, Long sessionId, WorkingMemoryState state) {
        if (state == null) {
            return;
        }
        String key = String.format(WM_KEY_TEMPLATE, userId, sessionId);
        int ttl = getTtlSeconds();
        try {
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(state), Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("Failed to save WorkingMemoryState: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 记录一轮对话到工作记忆（唯一写入点）：最近轮次截断 + 累积本轮域事件。
     */
    public void recordTurn(Long userId, Long sessionId, String userMessage, String assistantAnswer,
                           List<SessionEvent> events) {
        mutate(userId, sessionId, state -> {
            List<LlmRequest.Message> updated = new ArrayList<>(
                    state.getRecentMessages() == null ? List.of() : state.getRecentMessages());
            int turnIndex = state.getMeta() == null ? 0 : state.getMeta().getTurnCount();
            long now = System.currentTimeMillis();
            if (userMessage != null && !userMessage.isBlank()) {
                updated.add(LlmRequest.Message.builder()
                        .role("user")
                        .content(userMessage)
                        .turnIndex(turnIndex)
                        .timestamp(now)
                        .build());
            }
            if (assistantAnswer != null && !assistantAnswer.isBlank()) {
                updated.add(LlmRequest.Message.builder()
                        .role("assistant")
                        .content(assistantAnswer)
                        .turnIndex(turnIndex)
                        .timestamp(now)
                        .build());
            }
            state.setRecentMessages(applyRecentMessagePolicy(updated));
            state.appendEvents(events);
        });
    }

    // ==================== 挂起任务 / 澄清 / 缓冲结果 ====================

    /**
     * 保存挂起任务及澄清提示。
     */
    public void savePendingTasks(Long userId, Long sessionId, List<AgentTask> pendingTasks, String clarification) {
        mutate(userId, sessionId, state -> {
            state.setPendingTasks(pendingTasks == null ? new ArrayList<>() : new ArrayList<>(pendingTasks));
            state.setLastClarification(clarification);
            if (pendingTasks != null && !pendingTasks.isEmpty()) {
                state.incrementClarification();
            }
        });
    }

    /**
     * 覆盖写入已完成任务结果。
     */
    public void saveCompletedTaskResults(Long userId, Long sessionId, List<AgentTaskResult> results) {
        mutate(userId, sessionId, state ->
                state.setCompletedTaskResults(results == null ? new ArrayList<>() : new ArrayList<>(results)));
    }

    /**
     * 成功收尾时一次性清理：挂起任务 + 缓冲结果 + 澄清状态，合并为单次加锁读写。
     * 取代原先三次独立的 clear* 调用（三次 load-modify-save）。
     */
    public void clearBufferedConversationState(Long userId, Long sessionId) {
        mutate(userId, sessionId, state -> {
            state.clearPendingState();
            state.resetClarification();
        });
    }

    /**
     * 获取最大追问次数
     */
    public int getMaxClarificationCount() {
        AiProperties.WorkingMemory wm = aiProperties.getWorkingMemory();
        return wm == null ? DEFAULT_MAX_CLARIFICATION_COUNT : wm.getMaxClarificationCount();
    }

    // ==================== 从 L2 重建 ====================

    /**
     * 从数据库历史重建工作记忆。
     *
     * <p>仅恢复最近消息（不调 LLM、不复原槽位/摘要/挂起任务，那些字段留待后续对话轮自然重建）。
     * 最近消息按 {@link #applyRecentMessagePolicy} 截断：短会话保全量、长会话才截断。</p>
     */
    private WorkingMemoryState rebuildFromHistory(Long userId, Long sessionId) {
        WorkingMemoryState state = WorkingMemoryState.empty();
        List<AiMessageDO> allMessages = chatMemoryService.getAllMessages(sessionId, userId);
        if (allMessages != null && !allMessages.isEmpty()) {
            state.setRecentMessages(applyRecentMessagePolicy(toMessages(allMessages)));
            state.setRecovered(true);
        }
        return state;
    }

    /**
     * 最近消息保留策略：会话轮数（按 user 消息条数计）不超过 fullHistoryMaxTurns 阈值时保留全量，
     * 超过后才截断到 maxRecentMessages 条。
     *
     * <p>用 user 消息条数派生轮数，而非依赖 {@code meta.turnCount}——重建场景下 turnCount 尚未恢复（为 0），
     * 不可信。{@link #rebuildFromHistory} 与 {@link #recordTurn} 共用本方法，保证两处截断口径一致，
     * 避免重建给了全量、下一轮 recordTurn 又砍回去。</p>
     */
    private List<LlmRequest.Message> applyRecentMessagePolicy(List<LlmRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        int turnCount = countUserTurns(messages);
        if (turnCount <= getFullHistoryMaxTurns()) {
            return new ArrayList<>(messages);
        }
        int maxMessages = getMaxRecentMessages();
        if (messages.size() > maxMessages) {
            return new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
        }
        return new ArrayList<>(messages);
    }

    /**
     * 统计 user 角色消息条数，作为对话轮数的近似。
     */
    private int countUserTurns(List<LlmRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int turns = 0;
        for (LlmRequest.Message m : messages) {
            if (m != null && "user".equals(m.getRole())) {
                turns++;
            }
        }
        return turns;
    }

    /**
     * 将持久层消息映射为 LLM 消息，携带轮次、时间戳与 messageUid 元信息。
     */
    private List<LlmRequest.Message> toMessages(List<AiMessageDO> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        List<LlmRequest.Message> result = new ArrayList<>();
        int turn = 0;
        for (AiMessageDO m : messages) {
            if (m == null || m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            String role = normalizeRole(m.getRole());
            if ("user".equals(role)) {
                turn++;
            }
            result.add(LlmRequest.Message.builder()
                    .role(role)
                    .content(m.getContent())
                    .turnIndex(turn > 0 ? turn : null)
                    .timestamp(m.getCreateTime() == null ? null : m.getCreateTime().getTime())
                    .messageUid(m.getMessageUid())
                    .build());
        }
        return result;
    }

    /**
     * 标准化角色名称
     */
    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        String normalized = role.trim().toLowerCase();
        return switch (normalized) {
            case "user", "assistant", "system", "tool" -> normalized;
            default -> "user";
        };
    }

    // ==================== 配置读取 ====================

    /**
     * 获取 Redis TTL 秒数
     */
    private int getTtlSeconds() {
        AiProperties.WorkingMemory wm = aiProperties.getWorkingMemory();
        return wm == null ? DEFAULT_TTL_SECONDS : wm.getTtlSeconds();
    }

    /**
     * 获取最大最近消息数
     */
    private int getMaxRecentMessages() {
        AiProperties.WorkingMemory wm = aiProperties.getWorkingMemory();
        return wm == null ? DEFAULT_MAX_RECENT_MESSAGES : wm.getMaxRecentMessages();
    }

    /**
     * 获取保留完整轮数
     */
    private int getLastTurns() {
        AiProperties.WorkingMemory wm = aiProperties.getWorkingMemory();
        return wm == null ? DEFAULT_LAST_TURNS : wm.getLastTurns();
    }

    /**
     * 获取摘要起始轮次
     */
    private int getSummaryStartTurn() {
        AiProperties.WorkingMemory wm = aiProperties.getWorkingMemory();
        return wm == null ? DEFAULT_SUMMARY_START_TURN : wm.getSummaryStartTurn();
    }

    /**
     * 获取全量历史保留轮数上限：对话轮数不超过该阈值时保留全量历史，超过后才截断
     */
    private int getFullHistoryMaxTurns() {
        AiProperties.WorkingMemory wm = aiProperties.getWorkingMemory();
        return wm == null ? DEFAULT_FULL_HISTORY_MAX_TURNS : wm.getFullHistoryMaxTurns();
    }
}
