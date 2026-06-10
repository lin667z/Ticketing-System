package org.ticketing_system.biz.aiservice.agent.core;

import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.model.AgentPlan;
import org.ticketing_system.biz.aiservice.agent.model.AgentTask;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.memory.working.WorkingMemoryState;
import org.ticketing_system.biz.aiservice.session.DateNormalizer;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证并规范化 Master Agent 计划，对日期进行二次归一化后传给下游 Worker。
 *
 * <p>支持多线路并行：每个 TICKET_INFO 任务按其自身「出发地→到达地」匹配对应的线路槽位回填缺参，
 * 不同线路互不污染。校验后可经 {@link #applyPlanToSlotState} 反推出新的多线路槽位状态用于持久化。</p>
 */
@Component
public class TaskValidator {

    private static final String DEPARTURE = "departure";
    private static final String ARRIVAL = "arrival";
    private static final String DATE = "date";
    private static final String TRAIN_NUMBER = "trainNumber";
    private static final String COUNT = "count";
    private static final String PASSENGER_NAME = "passengerName";
    private static final String TICKET_DATE_NOT_OPEN_MESSAGE = "仅支持查询%s及之前的车票，之后日期暂未开放。";

    private final DateNormalizer dateNormalizer;

    public TaskValidator(DateNormalizer dateNormalizer) {
        this.dateNormalizer = dateNormalizer;
    }

    public AgentPlan validate(AgentPlan plan, AiChatRequestContext context) {
        if (plan == null) {
            return AgentPlan.readyOnly(List.of());
        }
        AgentPlan normalized = plan.normalize();
        SessionSlotState priorState = priorSlotState(context);
        List<AgentTask> readyTasks = new ArrayList<>();
        List<AgentTask> pendingTasks = new ArrayList<>();

        for (AgentTask task : normalized.getReadyTasks()) {
            classifyTask(task, context, priorState, readyTasks, pendingTasks);
        }
        for (AgentTask task : normalized.getPendingTasks()) {
            classifyTask(task, context, priorState, readyTasks, pendingTasks);
        }

        String clarification = buildClarification(readyTasks, pendingTasks, context, normalized.getClarification());
        return AgentPlan.builder()
                .readyTasks(readyTasks)
                .pendingTasks(pendingTasks)
                .clarification(clarification)
                .needAggregation(normalized.isNeedAggregation())
                .build()
                .normalize();
    }

    private void classifyTask(AgentTask task, AiChatRequestContext context, SessionSlotState priorState,
                              List<AgentTask> readyTasks, List<AgentTask> pendingTasks) {
        if (task == null || task.getType() == null) {
            return;
        }
        switch (task.getType()) {
            case TICKET_INFO -> classifyTicketTask(task, priorState, readyTasks, pendingTasks);
            case ORDER_QUERY -> readyTasks.add(sanitizeOrderTask(task, priorState));
            case GENERAL_CHAT -> readyTasks.add(task.toBuilder()
                    .parameters(Map.of())
                    .missingFields(List.of())
                    .dependsOnClarification(false)
                    .build());
        }
    }

    private void classifyTicketTask(AgentTask task, SessionSlotState priorState,
                                    List<AgentTask> readyTasks, List<AgentTask> pendingTasks) {
        String depRaw = task.getStringParam(DEPARTURE);
        String arrRaw = task.getStringParam(ARRIVAL);
        SessionSlotState.TicketSlot slot = resolveRouteSlot(priorState, depRaw, arrRaw);
        String departure = firstNonBlank(depRaw, slot == null ? null : slot.getDeparture());
        String arrival = firstNonBlank(arrRaw, slot == null ? null : slot.getArrival());
        String date = firstNonBlank(task.getStringParam(DATE), slot == null ? null : slot.getDate());
        String trainNumber = firstNonBlank(task.getStringParam(TRAIN_NUMBER), slot == null ? null : slot.getTrainNumber());
        List<String> missingFields = new ArrayList<>();
        if (isBlank(departure)) {
            missingFields.add(DEPARTURE);
        }
        if (isBlank(arrival)) {
            missingFields.add(ARRIVAL);
        }
        String normalizedDate = dateNormalizer.normalize(date);
        if (normalizedDate == null) {
            missingFields.add(DATE);
        }
        boolean sameStation = departure != null && departure.equals(arrival);
        if (!missingFields.isEmpty() || sameStation) {
            pendingTasks.add(buildPendingTicketTask(task, departure, arrival, date, trainNumber, missingFields, sameStation));
            return;
        }
        if (!dateNormalizer.isTicketQueryDateOpen(normalizedDate)) {
            pendingTasks.add(buildPendingTicketTask(task, departure, arrival,
                    String.format(TICKET_DATE_NOT_OPEN_MESSAGE, dateNormalizer.maxTicketQueryDate()), trainNumber,
                    List.of(DATE), false));
            return;
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(DEPARTURE, departure);
        parameters.put(ARRIVAL, arrival);
        parameters.put(DATE, normalizedDate);
        parameters.put(TRAIN_NUMBER, trainNumber == null ? "" : trainNumber);
        readyTasks.add(task.toBuilder()
                .parameters(parameters)
                .missingFields(List.of())
                .dependsOnClarification(false)
                .build());
    }

    private AgentTask buildPendingTicketTask(AgentTask task,
                                             String departure,
                                             String arrival,
                                             String date,
                                             String trainNumber,
                                             List<String> missingFields,
                                             boolean sameStation) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(DEPARTURE, departure == null ? "" : departure);
        parameters.put(ARRIVAL, arrival == null ? "" : arrival);
        parameters.put(DATE, date == null ? "" : date);
        parameters.put(TRAIN_NUMBER, trainNumber == null ? "" : trainNumber);
        List<String> normalizedMissing = new ArrayList<>(missingFields == null ? List.of() : missingFields);
        if (sameStation && !normalizedMissing.contains(ARRIVAL)) {
            normalizedMissing.add(ARRIVAL);
        }
        return task.toBuilder()
                .parameters(parameters)
                .missingFields(normalizedMissing)
                .dependsOnClarification(true)
                .build();
    }

    private AgentTask sanitizeOrderTask(AgentTask task, SessionSlotState priorState) {
        SessionSlotState.OrderQuerySlot slot = priorState == null ? null : priorState.getOrderQuery();
        String date = firstNonBlank(task.getStringParam(DATE), slot == null ? null : slot.getDate());
        Long count = parseLong(firstNonBlank(task.getStringParam(COUNT), slot == null || slot.getCount() == null ? null : String.valueOf(slot.getCount())));
        String trainNumber = firstNonBlank(task.getStringParam(TRAIN_NUMBER), slot == null ? null : slot.getTrainNumber());
        String departure = firstNonBlank(task.getStringParam(DEPARTURE), slot == null ? null : slot.getDeparture());
        String arrival = firstNonBlank(task.getStringParam(ARRIVAL), slot == null ? null : slot.getArrival());
        String passengerName = firstNonBlank(task.getStringParam(PASSENGER_NAME), slot == null ? null : slot.getPassengerName());
        Map<String, Object> parameters = new LinkedHashMap<>();
        String normalizedDate = dateNormalizer.normalize(date);
        if (normalizedDate != null) {
            parameters.put(DATE, normalizedDate);
        }
        if (count != null && count > 0L) {
            parameters.put(COUNT, count);
        }
        if (trainNumber != null && !trainNumber.isBlank()) {
            parameters.put(TRAIN_NUMBER, trainNumber);
        }
        if (departure != null && !departure.isBlank()) {
            parameters.put(DEPARTURE, departure);
        }
        if (arrival != null && !arrival.isBlank()) {
            parameters.put(ARRIVAL, arrival);
        }
        if (passengerName != null && !passengerName.isBlank()) {
            parameters.put(PASSENGER_NAME, passengerName);
        }
        return task.toBuilder()
                .parameters(parameters)
                .missingFields(List.of())
                .dependsOnClarification(false)
                .build();
    }

    // ==================== 多线路槽位反推（持久化用） ====================

    /**
     * 依据校验后的计划，合并出新的多线路槽位状态：
     * 以历史槽位为基底，将本轮每条 TICKET_INFO 任务（无论 ready 还是 pending）按线路 upsert，
     * 订单查询条件同步更新。未在本轮出现的历史线路予以保留，作为后续跟进的上下文。
     */
    public SessionSlotState applyPlanToSlotState(SessionSlotState prior, AgentPlan validatedPlan) {
        Map<String, SessionSlotState.TicketSlot> routes = new LinkedHashMap<>();
        if (prior != null && prior.getTicketRoutes() != null) {
            for (SessionSlotState.TicketSlot slot : prior.getTicketRoutes()) {
                if (slot != null && slot.getRouteKey() != null) {
                    routes.put(slot.getRouteKey(), slot);
                }
            }
        }
        SessionSlotState.OrderQuerySlot orderQuery = prior == null || prior.getOrderQuery() == null
                ? new SessionSlotState.OrderQuerySlot() : prior.getOrderQuery();

        if (validatedPlan != null) {
            for (AgentTask task : validatedPlan.getReadyTasks()) {
                orderQuery = upsertFromTask(task, routes, orderQuery);
            }
            for (AgentTask task : validatedPlan.getPendingTasks()) {
                orderQuery = upsertFromTask(task, routes, orderQuery);
            }
        }
        return SessionSlotState.builder()
                .ticketRoutes(new ArrayList<>(routes.values()))
                .orderQuery(orderQuery)
                .build();
    }

    private SessionSlotState.OrderQuerySlot upsertFromTask(AgentTask task,
                                                           Map<String, SessionSlotState.TicketSlot> routes,
                                                           SessionSlotState.OrderQuerySlot orderQuery) {
        if (task == null || task.getType() == null) {
            return orderQuery;
        }
        if (task.getType() == AgentType.TICKET_INFO) {
            String departure = task.getStringParam(DEPARTURE);
            String arrival = task.getStringParam(ARRIVAL);
            String routeKey = SessionSlotState.TicketSlot.routeKeyOf(departure, arrival);
            if (routeKey == null) {
                return orderQuery;
            }
            SessionSlotState.TicketSlot existing = routes.get(routeKey);
            SessionSlotState.TicketSlot merged = SessionSlotState.TicketSlot.builder()
                    .routeKey(routeKey)
                    .departure(firstNonBlank(departure, existing == null ? null : existing.getDeparture()))
                    .arrival(firstNonBlank(arrival, existing == null ? null : existing.getArrival()))
                    .date(firstNonBlank(task.getStringParam(DATE), existing == null ? null : existing.getDate()))
                    .trainNumber(firstNonBlank(task.getStringParam(TRAIN_NUMBER), existing == null ? null : existing.getTrainNumber()))
                    .seatType(existing == null ? null : existing.getSeatType())
                    .passengerCount(existing == null ? null : existing.getPassengerCount())
                    .build();
            routes.put(routeKey, merged);
            return orderQuery;
        }
        if (task.getType() == AgentType.ORDER_QUERY) {
            SessionSlotState.OrderQuerySlot base = orderQuery == null ? new SessionSlotState.OrderQuerySlot() : orderQuery;
            return SessionSlotState.OrderQuerySlot.builder()
                    .date(firstNonBlank(task.getStringParam(DATE), base.getDate()))
                    .count(parseLong(firstNonBlank(task.getStringParam(COUNT), base.getCount() == null ? null : String.valueOf(base.getCount()))))
                    .status(base.getStatus())
                    .trainNumber(firstNonBlank(task.getStringParam(TRAIN_NUMBER), base.getTrainNumber()))
                    .departure(firstNonBlank(task.getStringParam(DEPARTURE), base.getDeparture()))
                    .arrival(firstNonBlank(task.getStringParam(ARRIVAL), base.getArrival()))
                    .passengerName(firstNonBlank(task.getStringParam(PASSENGER_NAME), base.getPassengerName()))
                    .build();
        }
        return orderQuery;
    }

    // ==================== 线路匹配 ====================

    /**
     * 为某条 TICKET_INFO 任务匹配历史线路槽位以回填缺参：
     * 1) 出发、到达都齐全时按 routeKey 精确匹配；
     * 2) 仅给出单边（如只说到达站）时按该字段唯一匹配；
     * 3) 完全未给线路信息（如「那明天呢」）且历史恰好只有一条线路时，回退到该唯一线路。
     */
    private SessionSlotState.TicketSlot resolveRouteSlot(SessionSlotState state, String departure, String arrival) {
        if (state == null) {
            return null;
        }
        String routeKey = SessionSlotState.TicketSlot.routeKeyOf(departure, arrival);
        if (routeKey != null) {
            SessionSlotState.TicketSlot exact = state.findRoute(routeKey);
            if (exact != null) {
                return exact;
            }
        }
        SessionSlotState.TicketSlot byField = matchByPartialField(state, departure, arrival);
        if (byField != null) {
            return byField;
        }
        if (isBlank(departure) && isBlank(arrival)) {
            return state.getSoleRouteOrNull();
        }
        return null;
    }

    private SessionSlotState.TicketSlot matchByPartialField(SessionSlotState state, String departure, String arrival) {
        List<SessionSlotState.TicketSlot> routes = state.getTicketRoutes();
        if (routes == null || routes.isEmpty()) {
            return null;
        }
        boolean hasDep = !isBlank(departure);
        boolean hasArr = !isBlank(arrival);
        if (hasDep == hasArr) {
            return null;
        }
        SessionSlotState.TicketSlot matched = null;
        for (SessionSlotState.TicketSlot slot : routes) {
            if (slot == null) {
                continue;
            }
            boolean hit = hasDep ? departure.equals(slot.getDeparture()) : arrival.equals(slot.getArrival());
            if (hit) {
                if (matched != null) {
                    return null;
                }
                matched = slot;
            }
        }
        return matched;
    }

    // ==================== 澄清文案 ====================

    private String buildClarification(List<AgentTask> readyTasks,
                                      List<AgentTask> pendingTasks,
                                      AiChatRequestContext context,
                                      String existingClarification) {
        if (pendingTasks.isEmpty()) {
            return null;
        }
        List<String> prompts = new ArrayList<>();
        if (!readyTasks.isEmpty()) {
            prompts.add("当前请求中的其他事项已受理处理中");
        }
        for (AgentTask task : pendingTasks) {
            prompts.add(describePendingTask(task));
        }
        if (!prompts.isEmpty()) {
            return String.join("；", prompts) + "。";
        }
        if (existingClarification != null && !existingClarification.isBlank()) {
            return existingClarification;
        }
        if (looksLikeTicketIntent(context)) {
            return "查车票需要您提供出发地、目的地和出行日期，请补充。";
        }
        return "请补充当前任务所需信息。";
    }

    /**
     * 多线路场景下，澄清文案带上线路标识，让用户清楚是哪条线路缺信息。
     */
    private String describePendingTask(AgentTask task) {
        if (task == null) {
            return "请补充当前任务所需信息";
        }
        if (task.getType() == AgentType.TICKET_INFO) {
            String routeLabel = ticketRouteLabel(task);
            return routeLabel + "还需要补充" + joinTicketMissingFields(task.getMissingFields());
        }
        if (task.getType() == AgentType.ORDER_QUERY) {
            return "查订单还需要补充" + String.join("、", task.getMissingFields());
        }
        return "请补充当前任务所需信息";
    }

    private String ticketRouteLabel(AgentTask task) {
        String departure = task.getStringParam(DEPARTURE);
        String arrival = task.getStringParam(ARRIVAL);
        boolean hasDep = departure != null && !departure.isBlank();
        boolean hasArr = arrival != null && !arrival.isBlank();
        if (hasDep && hasArr) {
            return "查" + departure + "→" + arrival + "的车票";
        }
        if (hasDep) {
            return "查从" + departure + "出发的车票";
        }
        if (hasArr) {
            return "查到" + arrival + "的车票";
        }
        return "查车票";
    }

    private String joinTicketMissingFields(List<String> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) {
            return "必要信息";
        }
        List<String> labels = new ArrayList<>();
        for (String field : missingFields) {
            labels.add(switch (field) {
                case DEPARTURE -> "出发地";
                case ARRIVAL -> "目的地";
                case DATE -> "出行日期";
                default -> field;
            });
        }
        return String.join("、", labels);
    }

    private SessionSlotState priorSlotState(AiChatRequestContext context) {
        WorkingMemoryState wm = context == null ? null : context.getWorkingMemory();
        SessionSlotState slotState = wm == null ? null : wm.getTicketSlot();
        return slotState == null ? SessionSlotState.empty() : slotState;
    }

    private boolean looksLikeTicketIntent(AiChatRequestContext context) {
        String message = context == null || context.getCurrentMessage() == null ? "" : context.getCurrentMessage().getContent();
        return message.contains("票") || message.contains("车") || message.contains("余票") || message.contains("查");
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Long parseLong(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
