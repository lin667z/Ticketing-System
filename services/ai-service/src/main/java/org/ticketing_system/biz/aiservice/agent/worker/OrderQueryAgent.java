package org.ticketing_system.biz.aiservice.agent.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.AgentTask;
import org.ticketing_system.biz.aiservice.agent.AgentTaskResult;
import org.ticketing_system.biz.aiservice.agent.AgentType;
import org.ticketing_system.biz.aiservice.agent.AgentTraceEmitter;
import org.ticketing_system.biz.aiservice.agent.support.TicketQueryFacade;
import reactor.core.publisher.Mono;

/**
 * 订单查询 Agent，负责查询当前登录用户的购票订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueryAgent implements AiAgent {

    private static final String PARAM_DATE = "date";
    private static final String PARAM_COUNT = "count";

    private final TicketQueryFacade ticketQueryFacade;

    @Override
    public AgentType getAgentType() {
        return AgentType.ORDER_QUERY;
    }

    /**
     * 执行订单查询任务
     *
     * @param task 代理任务，包含日期、条数等参数
     * @return 包含订单列表或详情的任务结果
     */
    @Override
    public Mono<AgentTaskResult> execute(AgentTask task, AgentTraceEmitter traceEmitter, boolean streamToUser) {
        String date = task.getStringParam(PARAM_DATE);
        Long count = parseLong(task.getStringParam(PARAM_COUNT));
        if (traceEmitter != null) {
            traceEmitter.emitStatus(getAgentType(), "正在按当前用户 ID 查询订单");
        }
        return ticketQueryFacade.pageSelfOrders(task.getContext().getAuthenticatedUser(), date, count)
                .doOnSubscribe(ignored -> {
                    if (traceEmitter != null) {
                        traceEmitter.emitStatus(getAgentType(), "正在调用订单服务");
                    }
                })
                .map(result -> AgentTaskResult.builder()
                        .type(getAgentType())
                        .success(true)
                        .summary(result.getSummary())
                        .componentType(result.getComponentType())
                        .componentData(result.getComponentData())
                        .build())
                .doOnNext(ignored -> {
                    if (traceEmitter != null) {
                        traceEmitter.emitStatus(getAgentType(), "订单数据查询完成，正在整理结果");
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("订单查询 Agent 执行失败: {}", ex.getMessage(), ex);
                    return Mono.just(AgentTaskResult.failure(getAgentType(), "订单查询失败：" + ex.getMessage()));
                });
    }

    /**
     * 解析长整型字符串
     */
    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
