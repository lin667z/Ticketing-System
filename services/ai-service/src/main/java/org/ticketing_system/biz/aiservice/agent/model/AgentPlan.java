package org.ticketing_system.biz.aiservice.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 主代理（Master Agent）生成的路由计划
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlan {

    /**
     * 参数完整、可立即执行的任务列表
     */
    @Builder.Default
    private List<AgentTask> readyTasks = new ArrayList<>();

    /**
     * 缺少参数、等待用户补充后恢复的任务列表
     */
    @Builder.Default
    private List<AgentTask> pendingTasks = new ArrayList<>();

    /**
     * 需要用户澄清的问题内容
     */
    private String clarification;

    /**
     * 是否需要进行结果聚合
     */
    private boolean needAggregation;

    public static AgentPlan readyOnly(List<AgentTask> readyTasks) {
        return AgentPlan.builder()
                .readyTasks(safeTasks(readyTasks))
                .pendingTasks(new ArrayList<>())
                .needAggregation(readyTasks != null && readyTasks.size() > 1)
                .build()
                .normalize();
    }

    public static AgentPlan clarificationOnly(String clarification, List<AgentTask> pendingTasks) {
        return AgentPlan.builder()
                .readyTasks(new ArrayList<>())
                .pendingTasks(safeTasks(pendingTasks))
                .clarification(clarification)
                .needAggregation(false)
                .build()
                .normalize();
    }

    public static AgentPlan mixed(List<AgentTask> readyTasks, List<AgentTask> pendingTasks, String clarification) {
        return AgentPlan.builder()
                .readyTasks(safeTasks(readyTasks))
                .pendingTasks(safeTasks(pendingTasks))
                .clarification(clarification)
                .needAggregation(true)
                .build()
                .normalize();
    }

    public AgentPlan normalize() {
        readyTasks = safeTasks(readyTasks);
        pendingTasks = safeTasks(pendingTasks);
        if (!hasPendingTasks()) {
            clarification = null;
        }
        if (!hasReadyTasks()) {
            needAggregation = false;
        } else if (hasPendingTasks()) {
            needAggregation = true;
        } else {
            needAggregation = readyTasks.size() > 1;
        }
        return this;
    }

    public boolean hasClarification() {
        return clarification != null && !clarification.isBlank();
    }

    public boolean hasReadyTasks() {
        return readyTasks != null && !readyTasks.isEmpty();
    }

    public boolean hasPendingTasks() {
        return pendingTasks != null && !pendingTasks.isEmpty();
    }

    public boolean isMixedPlan() {
        return hasReadyTasks() && hasPendingTasks();
    }

    public boolean isExecutable() {
        return hasReadyTasks();
    }

    public List<AgentTask> getTasks() {
        return safeTasks(readyTasks);
    }

    public void setTasks(List<AgentTask> tasks) {
        this.readyTasks = safeTasks(tasks);
    }

    private static List<AgentTask> safeTasks(List<AgentTask> tasks) {
        return tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
    }
}
