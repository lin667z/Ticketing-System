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

    /**
     * 执行车票信息查询任务
     *
     * @param task 代理任务，包含出发站、到达站、日期等参数
     * @return 包含车次列表或特定车次详情的任务结果
     */
    @Override
    public Mono<AgentTaskResult> execute(AgentTask task, AgentTraceEmitter traceEmitter, boolean streamToUser) {
        String departure = task.getStringParam(PARAM_DEPARTURE);
        String arrival = task.getStringParam(PARAM_ARRIVAL);
        String date = task.getStringParam(PARAM_DATE);
        String trainNumber = task.getStringParam(PARAM_TRAIN_NUMBER);
        if (isBlank(departure) || isBlank(arrival) || isBlank(date)) {
            return Mono.just(AgentTaskResult.failure(getAgentType(), "请补充出发站、到达站和出行日期后再查询"));
        }
        if (traceEmitter != null) {
            traceEmitter.emitStatus(getAgentType(), "正在解析出发地、目的地和出行日期");
        }
        return ticketQueryFacade.queryTickets(departure, arrival, date, trainNumber)
                .doOnSubscribe(ignored -> {
                    if (traceEmitter != null) {
                        traceEmitter.emitStatus(getAgentType(), "正在调用票务服务查询余票和票价");
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
                        traceEmitter.emitStatus(getAgentType(), "票务数据查询完成，正在整理车次信息");
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("车票信息 Agent 执行失败: {}", ex.getMessage(), ex);
                    return Mono.just(AgentTaskResult.failure(getAgentType(), "票务查询失败：" + ex.getMessage()));
                });
    }

    /**
     * 判断字符串是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
