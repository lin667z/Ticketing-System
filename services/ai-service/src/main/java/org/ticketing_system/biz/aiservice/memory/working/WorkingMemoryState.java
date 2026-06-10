package org.ticketing_system.biz.aiservice.memory.working;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.agent.model.AgentTask;
import org.ticketing_system.biz.aiservice.agent.model.AgentTaskResult;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEvent;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import org.ticketing_system.biz.aiservice.session.context.SessionSummaryContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作记忆状态，缓存当前会话的临时上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingMemoryState {

    // 当前票务槽位状态
    @Builder.Default
    private SessionSlotState ticketSlot = SessionSlotState.empty();

    // 会话内压缩摘要（吸收原 session 轨 summaryContext）
    @Builder.Default
    private SessionSummaryContext summaryContext = SessionSummaryContext.empty();

    // 是否由数据库历史重建（L1 缓存丢失时）
    @Builder.Default
    private boolean recovered = false;

    // 累积的会话域事件，会话 finalize 时派生情节
    @Builder.Default
    private List<SessionEvent> accumulatedEvents = new ArrayList<>();

    // 已 finalize 的事件游标（已派生进情节的事件数，避免重复派生）
    @Builder.Default
    private int finalizedEventCursor = 0;

    // 扩展属性
    @Builder.Default
    private Map<String, Object> extensions = new HashMap<>();

    // 当前活跃意图
    private String activeIntent;

    // 追问计数
    @Builder.Default
    private int clarificationCount = 0;

    // 当前会话挂起任务
    @Builder.Default
    private List<AgentTask> pendingTasks = new ArrayList<>();

    // 已完成但尚未返回给用户的缓冲结果
    @Builder.Default
    private List<AgentTaskResult> completedTaskResults = new ArrayList<>();

    // 最近一次澄清内容
    private String lastClarification;

    // 最近消息列表
    @Builder.Default
    private List<LlmRequest.Message> recentMessages = new ArrayList<>();

    // 元数据（轮次、最后活跃时间）
    @Builder.Default
    private WorkingMemoryMeta meta = new WorkingMemoryMeta();

    /**
     * 递增轮次并更新最后活跃时间
     */
    public void incrementTurn() {
        if (meta == null) {
            meta = new WorkingMemoryMeta();
        }
        meta.setTurnCount(meta.getTurnCount() + 1);
        meta.setLastActiveTime(System.currentTimeMillis());
    }

    /**
     * 递增追问次数
     */
    public void incrementClarification() {
        this.clarificationCount++;
    }

    public void resetClarification() {
        this.clarificationCount = 0;
        this.lastClarification = null;
    }

    public boolean hasPendingTasks() {
        return pendingTasks != null && !pendingTasks.isEmpty();
    }

    public boolean hasBufferedResults() {
        return completedTaskResults != null && !completedTaskResults.isEmpty();
    }

    public void appendCompletedTaskResults(List<AgentTaskResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        if (completedTaskResults == null) {
            completedTaskResults = new ArrayList<>();
        }
        completedTaskResults.addAll(results);
    }

    public void clearPendingState() {
        pendingTasks = new ArrayList<>();
        completedTaskResults = new ArrayList<>();
        lastClarification = null;
    }

    /**
     * 追加会话域事件
     */
    public void appendEvents(List<SessionEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        if (accumulatedEvents == null) {
            accumulatedEvents = new ArrayList<>();
        }
        accumulatedEvents.addAll(events);
    }

    /**
     * 获取尚未 finalize 的事件（游标之后的部分）
     */
    @JSONField(serialize = false, deserialize = false)
    public List<SessionEvent> getUnfinalizedEvents() {
        if (accumulatedEvents == null || accumulatedEvents.isEmpty()) {
            return List.of();
        }
        int from = Math.min(Math.max(finalizedEventCursor, 0), accumulatedEvents.size());
        return new ArrayList<>(accumulatedEvents.subList(from, accumulatedEvents.size()));
    }

    /**
     * 是否存在尚未 finalize 的事件
     */
    @JSONField(serialize = false, deserialize = false)
    public boolean hasUnfinalizedEvents() {
        return accumulatedEvents != null && accumulatedEvents.size() > Math.max(finalizedEventCursor, 0);
    }

    /**
     * 将事件游标推进到当前累积事件末尾，标记其已 finalize
     */
    public void advanceFinalizedCursor() {
        this.finalizedEventCursor = accumulatedEvents == null ? 0 : accumulatedEvents.size();
    }

    /**
     * 将事件游标精确推进到已成功派生的事件数（不超过累积事件总数、不回退）。
     * 供异步 finalize 成功回调使用：仅在情节真正落库后才推进，避免派生失败却丢失事件。
     */
    public void advanceFinalizedCursorTo(int finalizedCount) {
        int size = accumulatedEvents == null ? 0 : accumulatedEvents.size();
        int target = Math.min(Math.max(finalizedCount, 0), size);
        if (target > this.finalizedEventCursor) {
            this.finalizedEventCursor = target;
        }
    }

    /**
     * 是否超过追问上限
     */
    public boolean exceedsClarificationLimit(int maxClarificationCount) {
        return this.clarificationCount >= maxClarificationCount;
    }

    /**
     * 工作记忆元数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkingMemoryMeta {
        // 对话轮次
        @Builder.Default
        private int turnCount = 0;
        // 最后活跃时间戳
        @Builder.Default
        private long lastActiveTime = 0;
    }

    /**
     * 获取 Redis 键
     */
    @JSONField(serialize = false, deserialize = false)
    public String getKey(Long userId, Long sessionId) {
        return String.format(WorkingMemoryService.WM_KEY_TEMPLATE, userId, sessionId);
    }

    /**
     * 创建一个空的工作记忆状态
     */
    public static WorkingMemoryState empty() {
        return WorkingMemoryState.builder()
                .ticketSlot(SessionSlotState.empty())
                .summaryContext(SessionSummaryContext.empty())
                .recovered(false)
                .accumulatedEvents(new ArrayList<>())
                .finalizedEventCursor(0)
                .extensions(new HashMap<>())
                .pendingTasks(new ArrayList<>())
                .completedTaskResults(new ArrayList<>())
                .recentMessages(new ArrayList<>())
                .meta(new WorkingMemoryMeta())
                .build();
    }
}
