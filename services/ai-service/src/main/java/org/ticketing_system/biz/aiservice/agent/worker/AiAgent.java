package org.ticketing_system.biz.aiservice.agent.worker;

import org.ticketing_system.biz.aiservice.agent.AgentTask;
import org.ticketing_system.biz.aiservice.agent.AgentTaskResult;
import org.ticketing_system.biz.aiservice.agent.AgentType;
import org.ticketing_system.biz.aiservice.agent.AgentTraceEmitter;
import reactor.core.publisher.Mono;

/**
 * 子 Agent 的通用接口定义
 */
public interface AiAgent {

    /**
     * 获取当前 Agent 的类型
     *
     * @return Agent 类型
     */
    AgentType getAgentType();

    /**
     * 执行特定的代理任务
     *
     * @param task 代理任务
     * @return 任务执行结果
     */
    Mono<AgentTaskResult> execute(AgentTask task, AgentTraceEmitter traceEmitter, boolean streamToUser);
}
