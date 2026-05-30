package org.ticketing_system.biz.aiservice.session;

import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import org.ticketing_system.biz.aiservice.session.context.SlotExtractionResult;

import java.util.Locale;
import java.util.Map;

/**
 * 将 LLM 提取的 slot 补丁合并到当前 slot 状态，日期在此环节完成归一化。
 */
@Component
public class SlotStateMerger {

    private static final String CLEAR_TICKET = "ticket";
    private static final String CLEAR_QUERY = "query";
    private static final String CLEAR_ALL = "all";
    private static final String DEPARTURE = "departure";
    private static final String ARRIVAL = "arrival";
    private static final String DATE = "date";
    private static final String TRAIN_NUMBER = "trainNumber";
    private static final String ORDER_DATE = "orderDate";
    private static final String ORDER_COUNT = "orderCount";

    private final DateNormalizer dateNormalizer;

    public SlotStateMerger(DateNormalizer dateNormalizer) {
        this.dateNormalizer = dateNormalizer;
    }

    public SessionSlotState merge(SessionSlotState current, SlotExtractionResult extraction) {
        SessionSlotState result = copy(current);
        if (extraction == null) {
            return result;
        }
        applyClears(result, extraction);
        Map<String, Object> patch = extraction.getSlotPatch();
        if (patch == null || patch.isEmpty()) {
            return result;
        }
        applyTicketPatch(result, patch);
        applyOrderPatch(result, patch);
        return result;
    }

    private void applyClears(SessionSlotState result, SlotExtractionResult extraction) {
        if (extraction.getClearSlots() == null) {
            return;
        }
        for (String slot : extraction.getClearSlots()) {
            String normalized = slot == null ? "" : slot.trim().toLowerCase(Locale.ROOT);
            if (CLEAR_ALL.equals(normalized) || CLEAR_TICKET.equals(normalized) || CLEAR_QUERY.equals(normalized)) {
                result.setTicket(new SessionSlotState.TicketSlot());
            }
            if (CLEAR_ALL.equals(normalized) || "order".equals(normalized) || "orderquery".equals(normalized)) {
                result.setOrderQuery(new SessionSlotState.OrderQuerySlot());
            }
        }
    }

    private void applyTicketPatch(SessionSlotState result, Map<String, Object> patch) {
        SessionSlotState.TicketSlot ticket = result.getTicket();
        ticket.setDeparture(readText(patch, DEPARTURE, ticket.getDeparture()));
        ticket.setArrival(readText(patch, ARRIVAL, ticket.getArrival()));
        ticket.setTrainNumber(readText(patch, TRAIN_NUMBER, ticket.getTrainNumber()));
        String date = readText(patch, DATE, null);
        if (date != null) {
            String normalizedDate = dateNormalizer.normalize(date);
            if (normalizedDate != null) {
                ticket.setDate(normalizedDate);
            }
        }
    }

    private void applyOrderPatch(SessionSlotState result, Map<String, Object> patch) {
        SessionSlotState.OrderQuerySlot orderQuery = result.getOrderQuery();
        String date = readText(patch, ORDER_DATE, null);
        if (date == null && patch.containsKey("order.date")) {
            date = String.valueOf(patch.get("order.date"));
        }
        if (date != null) {
            String normalizedDate = dateNormalizer.normalize(date);
            if (normalizedDate != null) {
                orderQuery.setDate(normalizedDate);
            }
        }
        Long count = readLong(patch, ORDER_COUNT);
        if (count == null && patch.containsKey("count")) {
            count = readLong(patch, "count");
        }
        if (count != null && count > 0L) {
            orderQuery.setCount(count);
        }
    }

    private SessionSlotState copy(SessionSlotState source) {
        if (source == null) {
            return SessionSlotState.empty();
        }
        SessionSlotState.TicketSlot ticket = source.getTicket() == null ? new SessionSlotState.TicketSlot() : source.getTicket();
        SessionSlotState.OrderQuerySlot orderQuery = source.getOrderQuery() == null ? new SessionSlotState.OrderQuerySlot() : source.getOrderQuery();
        return SessionSlotState.builder()
                .ticket(SessionSlotState.TicketSlot.builder()
                        .departure(ticket.getDeparture())
                        .arrival(ticket.getArrival())
                        .date(ticket.getDate())
                        .trainNumber(ticket.getTrainNumber())
                        .build())
                .orderQuery(SessionSlotState.OrderQuerySlot.builder()
                        .date(orderQuery.getDate())
                        .count(orderQuery.getCount())
                        .build())
                .build();
    }

    private String readText(Map<String, Object> source, String key, String defaultValue) {
        Object value = source.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private Long readLong(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
