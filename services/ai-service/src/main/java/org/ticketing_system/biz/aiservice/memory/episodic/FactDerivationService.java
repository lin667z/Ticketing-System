package org.ticketing_system.biz.aiservice.memory.episodic;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 事实推导服务，将事件列表转为结构化事实文本
 */
@Component
public class FactDerivationService {

    /**
     * 批量推导事件为事实文本
     */
    public List<String> derive(List<SessionEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<String> facts = new ArrayList<>();
        for (SessionEvent event : events) {
            String fact = deriveSingle(event);
            if (fact != null) {
                facts.add(fact);
            }
        }
        return facts;
    }

    /**
     * 将单个事件转为结构化事实文本
     */
    private String deriveSingle(SessionEvent event) {
        if (event == null || event.getEventType() == null) {
            return null;
        }
        Map<String, Object> payload = event.getPayload();
        return switch (event.getEventType()) {
            case TICKET_SEARCH_REQUESTED -> {
                String departure = str(payload, "departure");
                String arrival = str(payload, "arrival");
                String date = str(payload, "date");
                String trainNumber = str(payload, "trainNumber");
                if (departure != null && arrival != null && date != null) {
                    yield trainNumber != null
                            ? "查询了" + departure + "→" + arrival + " " + date + " 车次 " + trainNumber
                            : "查询了" + departure + "→" + arrival + " " + date;
                }
                yield null;
            }
            case TICKET_RESULTS_DISPLAYED -> {
                String totalCount = str(payload, "totalCount");
                if (totalCount != null) {
                    yield "返回 " + totalCount + " 趟列车";
                }
                yield null;
            }
            case ORDER_QUERY_REQUESTED -> {
                String date = str(payload, "date");
                if (date != null) {
                    yield "查询了 " + date + " 的订单";
                }
                yield null;
            }
            case ORDER_RESULTS_DISPLAYED -> {
                String totalCount = str(payload, "totalCount");
                if (totalCount != null) {
                    yield "返回 " + totalCount + " 条订单";
                }
                yield null;
            }
            case USER_SELECTED_TRAIN -> {
                String trainNumber = str(payload, "trainNumber");
                if (trainNumber != null) {
                    yield "选择了车次 " + trainNumber;
                }
                yield null;
            }
            case USER_ASKED_QUESTION -> {
                String category = str(payload, "category");
                if (category != null) {
                    yield "询问了 " + category + " 相关问题";
                }
                yield null;
            }
            case SYSTEM_CLARIFIED -> {
                String reason = str(payload, "reason");
                if (reason != null) {
                    yield "系统追问: " + reason;
                }
                yield null;
            }
            case SESSION_STARTED -> "会话开始";
            case SESSION_ENDED -> {
                String reason = str(payload, "reason");
                yield reason != null ? "会话结束 (" + reason + ")" : "会话结束";
            }
        };
    }

    /**
     * 安全地从 Map 中取字符串值
     */
    private String str(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : value.toString();
    }
}
