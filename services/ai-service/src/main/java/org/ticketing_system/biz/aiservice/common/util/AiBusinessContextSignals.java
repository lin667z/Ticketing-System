package org.ticketing_system.biz.aiservice.common.util;

import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Shared predicates for deciding whether a message needs ticketing business context.
 */
public final class AiBusinessContextSignals {

    private static final Pattern TRAIN_NUMBER_PATTERN = Pattern.compile("(?i).*[GDCKTZ]\\d{1,5}.*");
    private static final Pattern ROUTE_PATTERN = Pattern.compile(".*(从|由).+(到|去|至).+.*");
    private static final Pattern DATE_SIGNAL_PATTERN = Pattern.compile(".*(今天|明天|后天|大后天|周[一二三四五六日天]|星期[一二三四五六日天]|\\d{1,2}月\\d{1,2}日|\\d{4}-\\d{1,2}-\\d{1,2}).*");
    private static final Pattern TRAVEL_CUE_PATTERN = Pattern.compile(".*(去|到|至|出发|返回|返程|行程).+.*");
    private static final String[] BUSINESS_KEYWORDS = {
            "票", "车票", "火车", "高铁", "动车", "列车", "车次", "站", "出发", "到达",
            "订单", "行程", "改签", "退票", "订", "买", "查询", "座", "铺",
            "ticket", "train", "order", "station"
    };
    private static final String[] CONTEXT_DEPENDENT_KEYWORDS = {
            "刚才", "第二个", "返程", "改成", "便宜点", "还是"
    };

    private AiBusinessContextSignals() {
    }

    public static boolean hasRecentBusinessContext(List<LlmRequest.Message> recentTurns) {
        if (recentTurns == null || recentTurns.isEmpty()) {
            return false;
        }
        return recentTurns.stream()
                .filter(Objects::nonNull)
                .map(LlmRequest.Message::getContent)
                .anyMatch(AiBusinessContextSignals::hasBusinessSignal);
    }

    public static boolean hasBusinessSignal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim().toLowerCase();
        if (TRAIN_NUMBER_PATTERN.matcher(normalized).matches() || ROUTE_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        for (String keyword : BUSINESS_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return DATE_SIGNAL_PATTERN.matcher(normalized).matches() && TRAVEL_CUE_PATTERN.matcher(normalized).matches();
    }

    public static boolean hasBusinessSlot(SessionSlotState slotState) {
        if (slotState == null) {
            return false;
        }
        SessionSlotState.TicketSlot ticket = slotState.getTicket();
        SessionSlotState.OrderQuerySlot order = slotState.getOrderQuery();
        return ticket != null
                && (!isBlank(ticket.getDeparture()) || !isBlank(ticket.getArrival()) || !isBlank(ticket.getDate()) || !isBlank(ticket.getTrainNumber()))
                || order != null && (!isBlank(order.getDate()) || order.getCount() != null);
    }

    public static boolean isContextDependent(String text) {
        if (text == null) {
            return false;
        }
        for (String keyword : CONTEXT_DEPENDENT_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
