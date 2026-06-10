package org.ticketing_system.biz.aiservice.agent.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.model.AgentTask;
import org.ticketing_system.biz.aiservice.agent.model.AgentTaskResult;
import org.ticketing_system.biz.aiservice.agent.core.AgentType;
import org.ticketing_system.biz.aiservice.agent.core.AgentTraceEmitter;
import org.ticketing_system.biz.aiservice.agent.support.TicketQueryFacade;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEvent;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEventCollector;
import org.ticketing_system.biz.aiservice.memory.episodic.SessionEventType;
import org.ticketing_system.biz.aiservice.memory.execution.ExecutionContext;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单查询 Agent，负责查询当前登录用户的购票订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueryAgent implements AiAgent {

    private static final String PARAM_DATE = "date";
    private static final String PARAM_COUNT = "count";
    private static final String PARAM_TRAIN_NUMBER = "trainNumber";
    private static final String PARAM_DEPARTURE = "departure";
    private static final String PARAM_ARRIVAL = "arrival";
    private static final String PARAM_PASSENGER_NAME = "passengerName";

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
            traceEmitter.emitStage("Order Query Agent", "正在按当前用户 ID 查询订单");
        }
        return ticketQueryFacade.querySelfOrders(task.getContext().getAuthenticatedUser(), date, count)
                .doOnSubscribe(ignored -> {
                    if (traceEmitter != null) {
                        traceEmitter.emitStage("Order Query Agent", "正在调用订单服务");
                    }
                })
                .map(result -> {
                    ExecutionContext execCtx = task.getExecutionContext();
                    if (execCtx != null) {
                        execCtx.putResult(getAgentType(), result);
                    }
                    recordEvent(task.getSessionEventCollector(), date, count, result);
                    String effectiveDate = date != null && !date.isBlank() ? date : "latest";
                    return AgentTaskResult.builder()
                            .type(getAgentType())
                            .success(true)
                            .summary(result.getSummary())
                            .componentType(result.getComponentType())
                            .componentData(result.getComponentData())
                            .componentId("order-" + effectiveDate)
                            .build();
                })
                .doOnNext(ignored -> {
                    if (traceEmitter != null) {
                        traceEmitter.emitStage("Order Query Agent", "订单数据查询完成，正在整理结果");
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("订单查询 Agent 执行失败: {}", ex.getMessage(), ex);
                    return Mono.just(AgentTaskResult.failure(getAgentType(), "订单查询失败：" + ex.getMessage()));
                });
    }

    private void recordEvent(SessionEventCollector collector, String date, Long count, TicketQueryFacade.BusinessQueryResult result) {
        if (collector == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        if (date != null) {
            payload.put("date", date);
        }
        if (count != null) {
            payload.put("count", String.valueOf(count));
        }
        collector.record(SessionEvent.of(SessionEventType.ORDER_QUERY_REQUESTED, 0, payload));

        if (result.getRawData() != null) {
            Map<String, Object> resultPayload = new HashMap<>();
            Object totalCount = result.getRawData().get("totalCount");
            if (totalCount == null) {
                totalCount = result.getRawData().get("total");
            }
            if (totalCount == null) {
                Object records = result.getRawData().get("records");
                if (records instanceof java.util.List<?> list) {
                    totalCount = String.valueOf(list.size());
                }
            }
            if (totalCount != null) {
                resultPayload.put("totalCount", String.valueOf(totalCount));
                collector.record(SessionEvent.of(SessionEventType.ORDER_RESULTS_DISPLAYED, 0, resultPayload));
            }
        }
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
