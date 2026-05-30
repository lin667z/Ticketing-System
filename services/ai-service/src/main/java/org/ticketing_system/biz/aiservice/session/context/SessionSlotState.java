package org.ticketing_system.biz.aiservice.session.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化业务上下文，作为多轮对话的执行数据源，日期字段存储的是归一化后的 yyyy-MM-dd。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSlotState {

    @Builder.Default
    private TicketSlot ticket = new TicketSlot();

    @Builder.Default
    private OrderQuerySlot orderQuery = new OrderQuerySlot();

    public static SessionSlotState empty() {
        return SessionSlotState.builder()
                .ticket(new TicketSlot())
                .orderQuery(new OrderQuerySlot())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketSlot {

        private String departure;

        private String arrival;

        private String date;

        private String trainNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderQuerySlot {

        private String date;

        private Long count;
    }
}
