package org.ticketing_system.biz.aiservice.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子 Agent 任务的执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskResult {

    /**
     * 执行任务的 Agent 类型
     */
    private AgentType type;

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 结果摘要
     */
    private String summary;

    /**
     * 组件类型（用于前端渲染特定组件）
     */
    private String componentType;

    /**
     * 组件数据内容
     */
    private Object componentData;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 鏄惁宸插皢 summary 娴佸紡杈撳嚭缁欑敤鎴?
     */
    private boolean streamedToUser;

    /**
     * 创建成功的任务结果
     *
     * @param type    Agent 类型
     * @param summary 结果摘要
     * @return 成功的任务结果对象
     */
    public static AgentTaskResult success(AgentType type, String summary) {
        return AgentTaskResult.builder()
                .type(type)
                .success(true)
                .summary(summary)
                .build();
    }

    /**
     * 创建失败的任务结果
     *
     * @param type         Agent 类型
     * @param errorMessage 错误信息
     * @return 失败的任务结果对象
     */
    public static AgentTaskResult failure(AgentType type, String errorMessage) {
        return AgentTaskResult.builder()
                .type(type)
                .success(false)
                .errorMessage(errorMessage)
                .summary(errorMessage)
                .build();
    }
}
