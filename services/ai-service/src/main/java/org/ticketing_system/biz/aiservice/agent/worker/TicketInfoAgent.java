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
 * 车票信息 Agent，负责查询列车时刻、余票以及票价信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketInfoAgent implements AiAgent {

    private static final String PARAM_DEPARTURE = "departure";
    private static final String PARAM_ARRIVAL = "arrival";
    private static final String PARAM_DATE = "date";
    private static final String PARAM_TRAIN_NUMBER = "trainNumber";

    private final TicketQueryFacade ticketQueryFacade;

    @Override
    public AgentType getAgentType() {
        return AgentType.TICKET_INFO;
    }

    @Override
    public Mono<AgentTaskResult> execute(AgentTask task, AgentTraceEmitter traceEmitter, boolean streamToUser) {
        String departure = task.getStringParam(PARAM_DEPARTURE);
        String arrival = task.getStringParam(PARAM_ARRIVAL);
        String date = task.getStringParam(PARAM_DATE);
        String trainNumber = task.getStringParam(PARAM_TRAIN_NUMBER);
        if (isBlank(departure) || isBlank(arrival) || isBlank(date)) {
            return Mono.just(AgentTaskResult.failure(getAgentType(), "查车票需要您提供出发站、到达站和出行日期，请补充后再查询"));
        }
        if (traceEmitter != null) {
            traceEmitter.emitStage("Ticket Info Agent", "正在解析出发地、目的地和出行日期");
        }
        return ticketQueryFacade.queryTickets(departure, arrival, date, trainNumber)
                .doOnSubscribe(ignored -> {
                    if (traceEmitter != null) {
                        traceEmitter.emitStage("Ticket Info Agent", "正在调用票务服务查询余票和票价");
                    }
                })
                .map(result -> {
                    ExecutionContext execCtx = task.getExecutionContext();
                    if (execCtx != null) {
                        execCtx.putResult(getAgentType(), result);
                    }
                    recordEvent(task.getSessionEventCollector(), departure, arrival, date, trainNumber, result);
                    return AgentTaskResult.builder()
                            .type(getAgentType())
                            .success(true)
                            .summary(result.getSummary())
                            .componentType(result.getComponentType())
                            .componentData(result.getComponentData())
                            .componentId("ticket-" + departure.replaceAll("\\s+", "") + "-" + arrival.replaceAll("\\s+", "") + "-" + date)
                            .build();
                })
                .doOnNext(ignored -> {
                    if (traceEmitter != null) {
                        traceEmitter.emitStage("Ticket Info Agent", "票务数据查询完成，正在整理车次信息");
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("车票信息 Agent 执行失败: {}", ex.getMessage(), ex);
                    return Mono.just(AgentTaskResult.failure(getAgentType(), "票务查询失败：" + ex.getMessage()));
                });
    }

    private void recordEvent(SessionEventCollector collector, String departure, String arrival, String date, String trainNumber, TicketQueryFacade.BusinessQueryResult result) {
        if (collector == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("departure", departure);
        payload.put("arrival", arrival);
        payload.put("date", date);
        if (trainNumber != null) {
            payload.put("trainNumber", trainNumber);
        }
        collector.record(SessionEvent.of(SessionEventType.TICKET_SEARCH_REQUESTED, 0, payload));

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
                collector.record(SessionEvent.of(SessionEventType.TICKET_RESULTS_DISPLAYED, 0, resultPayload));
            }
        }
    }

    /**
     * 判断字符串是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
