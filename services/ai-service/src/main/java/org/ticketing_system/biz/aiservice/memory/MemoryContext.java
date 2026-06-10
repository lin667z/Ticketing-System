package org.ticketing_system.biz.aiservice.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.dao.entity.AiEpisodeDO;
import org.ticketing_system.biz.aiservice.dao.entity.AiMemoryDO;
import org.ticketing_system.biz.aiservice.memory.execution.ExecutionContext;
import org.ticketing_system.biz.aiservice.memory.working.WorkingMemoryState;

import java.util.List;

/**
 * 对话记忆上下文，聚合工作记忆、执行上下文、情节与偏好
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryContext {

    // 工作记忆状态
    private WorkingMemoryState workingMemory;

    // 执行上下文
    private ExecutionContext executionContext;

    // 近期情节列表
    private List<AiEpisodeDO> recentEpisodes;

    // 长期偏好列表
    @Builder.Default
    private List<AiMemoryDO> longTermPreferences = List.of();

    // 规则片段列表
    private List<String> ruleChunks;

    /**
     * 是否存在工作记忆
     */
    public boolean hasWorkingMemory() {
        return workingMemory != null;
    }

    /**
     * 是否存在近期情节
     */
    public boolean hasRecentEpisodes() {
        return recentEpisodes != null && !recentEpisodes.isEmpty();
    }

    /**
     * 是否存在长期偏好
     */
    public boolean hasPreferences() {
        return longTermPreferences != null && !longTermPreferences.isEmpty();
    }
}
