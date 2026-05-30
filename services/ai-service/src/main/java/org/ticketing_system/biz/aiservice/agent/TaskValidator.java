package org.ticketing_system.biz.aiservice.agent;

import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.session.DateNormalizer;
import org.ticketing_system.biz.aiservice.session.context.AiSessionContext;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证并规范化 Master Agent 计划，对日期进行二次归一化后传给下游 Worker
 */
@Component
public class TaskValidator {

    private static final String DEPARTURE = "departure";
    private static final String ARRIVAL = "arrival";
    private static final String DATE = "date";
    private static final String TRAIN_NUMBER = "trainNumber";
    private static final String COUNT = "count";
    private static final String TICKET_DATE_NOT_OPEN_MESSAGE = "仅支持查询%s及之前的车票，之后日期暂未开放。";

    private final DateNormalizer dateNormalizer;

    public TaskValidator(DateNormalizer dateNormalizer) {
        this.dateNormalizer = dateNormalizer;
    }

    public AgentPlan validate(AgentPlan plan, AiChatRequestContext context) {
        if (plan == null) {
            return AgentPlan.builder().tasks(List.of()).build();
        }
        if (plan.hasClarification()) {
            return plan;
        }
        List<AgentTask> validatedTasks = new ArrayList<>();
        for (AgentTask task : plan.getTasks() == null ? List.<AgentTask>of() : plan.getTasks()) {
            if (isTicketDateNotOpen(task, context)) {
                plan.setTasks(List.of());
                plan.setNeedAggregation(false);
                plan.setClarification(String.format(TICKET_DATE_NOT_OPEN_MESSAGE, dateNormalizer.maxTicketQueryDate()));
                return plan;
            }
            AgentTask validated = validateTask(task, context);
            if (validated != null) {
                validatedTasks.add(validated);
            }
        }
        plan.setTasks(validatedTasks);
        plan.setNeedAggregation(validatedTasks.size() > 1);
        if (validatedTasks.isEmpty() && looksLikeTicketIntent(context)) {
            plan.setClarification("请补充出发地、目的地和出行日期。");
        }
        return plan;
    }

    private AgentTask validateTask(AgentTask task, AiChatRequestContext context) {
        if (task == null || task.getType() == null) {
            return null;
        }
        return switch (task.getType()) {
            case TICKET_INFO -> validateTicketTask(task, context);
            case ORDER_QUERY -> sanitizeOrderTask(task, context);
            case GENERAL_CHAT -> task.toBuilder()
                    .parameters(Map.of())
                    .build();
        };
    }

    private AgentTask validateTicketTask(AgentTask task, AiChatRequestContext context) {
        SessionSlotState.TicketSlot slot = ticketSlot(context);
        String departure = firstNonBlank(task.getStringParam(DEPARTURE), slot == null ? null : slot.getDeparture());
        String arrival = firstNonBlank(task.getStringParam(ARRIVAL), slot == null ? null : slot.getArrival());
        String date = firstNonBlank(task.getStringParam(DATE), slot == null ? null : slot.getDate());
        String trainNumber = firstNonBlank(task.getStringParam(TRAIN_NUMBER), slot == null ? null : slot.getTrainNumber());
        String normalizedDate = dateNormalizer.normalize(date);
        if (isBlank(departure) || isBlank(arrival) || normalizedDate == null || departure.equals(arrival)) {
            return null;
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(DEPARTURE, departure);
        parameters.put(ARRIVAL, arrival);
        parameters.put(DATE, normalizedDate);
        parameters.put(TRAIN_NUMBER, trainNumber == null ? "" : trainNumber);
        return task.toBuilder()
                .parameters(parameters)
                .build();
    }

    private AgentTask sanitizeOrderTask(AgentTask task, AiChatRequestContext context) {
        SessionSlotState.OrderQuerySlot slot = orderSlot(context);
        String date = firstNonBlank(task.getStringParam(DATE), slot == null ? null : slot.getDate());
        Long count = parseLong(firstNonBlank(task.getStringParam(COUNT), slot == null || slot.getCount() == null ? null : String.valueOf(slot.getCount())));
        Map<String, Object> parameters = new LinkedHashMap<>();
        String normalizedDate = dateNormalizer.normalize(date);
        if (normalizedDate != null) {
            parameters.put(DATE, normalizedDate);
        }
        if (count != null && count > 0L) {
            parameters.put(COUNT, count);
        }
        return task.toBuilder()
                .parameters(parameters)
                .build();
    }

    private boolean isTicketDateNotOpen(AgentTask task, AiChatRequestContext context) {
        if (task == null || task.getType() != AgentType.TICKET_INFO) {
            return false;
        }
        SessionSlotState.TicketSlot slot = ticketSlot(context);
        String date = firstNonBlank(task.getStringParam(DATE), slot == null ? null : slot.getDate());
        String normalizedDate = dateNormalizer.normalize(date);
        return normalizedDate != null && !dateNormalizer.isTicketQueryDateOpen(normalizedDate);
    }

    private SessionSlotState.TicketSlot ticketSlot(AiChatRequestContext context) {
        AiSessionContext sessionContext = context == null ? null : context.getSessionContext();
        SessionSlotState slotState = sessionContext == null ? null : sessionContext.getSlotState();
        return slotState == null ? null : slotState.getTicket();
    }

    private SessionSlotState.OrderQuerySlot orderSlot(AiChatRequestContext context) {
        AiSessionContext sessionContext = context == null ? null : context.getSessionContext();
        SessionSlotState slotState = sessionContext == null ? null : sessionContext.getSlotState();
        return slotState == null ? null : slotState.getOrderQuery();
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
