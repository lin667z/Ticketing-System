package org.ticketing_system.biz.aiservice.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聚合后的 Agent 响应结果，用于 SSE 转换和持久化
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionResult {

    /**
     * 最终生成的回答内容
     */
    private String answer;

    /**
     * 各个子任务的执行结果列表
     */
    @Builder.Default
    private List<AgentTaskResult> taskResults = new ArrayList<>();

    /**
     * 模型使用情况统计（如 Token 消耗等）
     */
    @Builder.Default
    private Map<String, Object> usage = Map.of();

    /**
     * 使用的模型名称
     */
    private String modelName;

    private boolean answerStreamed;
}
