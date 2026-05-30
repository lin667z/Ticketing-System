package org.ticketing_system.biz.aiservice.session;

import org.junit.jupiter.api.Test;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
import org.ticketing_system.biz.aiservice.session.context.SlotExtractionResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SlotStateMergerTest {

    private final SlotStateMerger merger = new SlotStateMerger(new DateNormalizer(
            Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneId.of("Asia/Shanghai"))));

    @Test
    void shouldMergeMissingTicketFields() {
        SessionSlotState current = SessionSlotState.empty();
        current.getTicket().setDeparture("广州");

        SessionSlotState result = merger.merge(current, SlotExtractionResult.builder()
                .slotPatch(Map.of("arrival", "北京", "date", "明天"))
                .build());

        assertEquals("广州", result.getTicket().getDeparture());
        assertEquals("北京", result.getTicket().getArrival());
        assertEquals("2026-05-29", result.getTicket().getDate());
    }

    @Test
    void shouldOverrideExplicitField() {
        SessionSlotState current = SessionSlotState.empty();
        current.getTicket().setArrival("北京");

        SessionSlotState result = merger.merge(current, SlotExtractionResult.builder()
                .slotPatch(Map.of("arrival", "上海"))
                .build());

        assertEquals("上海", result.getTicket().getArrival());
    }

    @Test
    void shouldClearTicketSlots() {
        SessionSlotState current = SessionSlotState.empty();
        current.getTicket().setDeparture("广州");
        current.getTicket().setArrival("北京");

        SessionSlotState result = merger.merge(current, SlotExtractionResult.builder()
                .clearSlots(List.of("ticket"))
                .build());

        assertNull(result.getTicket().getDeparture());
        assertNull(result.getTicket().getArrival());
    }
}
