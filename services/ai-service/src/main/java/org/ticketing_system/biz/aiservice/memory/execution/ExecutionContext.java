package org.ticketing_system.biz.aiservice.memory.execution;

import lombok.Data;
import org.ticketing_system.biz.aiservice.agent.core.AgentType;
import org.ticketing_system.biz.aiservice.agent.support.TicketQueryFacade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行上下文，存储多智能体任务结果与调用轨迹
 */
@Data
public class ExecutionContext {

    // 各智能体的业务查询结果
    private final Map<AgentType, TicketQueryFacade.BusinessQueryResult> taskResults = new ConcurrentHashMap<>();

    // 执行轨迹事件列表
    private final List<TraceEntry> traceEvents = new ArrayList<>();

    /**
     * 存入智能体执行结果
     */
    public void putResult(AgentType agentType, TicketQueryFacade.BusinessQueryResult result) {
        if (agentType != null && result != null) {
            taskResults.put(agentType, result);
        }
    }

    /**
     * 获取智能体执行结果
     */
    public TicketQueryFacade.BusinessQueryResult getResult(AgentType agentType) {
        return taskResults.get(agentType);
    }

    /**
     * 添加执行轨迹
     */
    public void addTrace(String stage, String label, String detail) {
        traceEvents.add(new TraceEntry(stage, label, detail, System.currentTimeMillis()));
    }

    /**
     * 轨迹条目记录
     */
    public record TraceEntry(String stage, String label, String detail, long timestamp) {
    }
}
