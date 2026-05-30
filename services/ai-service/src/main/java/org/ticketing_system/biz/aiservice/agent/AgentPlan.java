package org.ticketing_system.biz.aiservice.agent;

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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlan {

    /**
     * 待执行的任务列表
     */
    @Builder.Default
    private List<AgentTask> tasks = new ArrayList<>();

    /**
     * 需要用户澄清的问题内容
     */
    private String clarification;

    /**
     * 是否需要进行结果聚合
     */
    private boolean needAggregation;

    /**
     * 判断是否包含澄清请求
     *
     * @return 如果有澄清内容则返回 true
     */
    public boolean hasClarification() {
        return clarification != null && !clarification.isBlank();
    }
}
