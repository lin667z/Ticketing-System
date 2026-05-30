package org.ticketing_system.biz.aiservice.agent;

import org.junit.jupiter.api.Test;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.model.AiChatMessage;
import org.ticketing_system.biz.aiservice.session.DateNormalizer;
import org.ticketing_system.biz.aiservice.session.context.AiSessionContext;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskValidatorTest {

    private final TaskValidator validator = new TaskValidator(new DateNormalizer());

    @Test
    void shouldFillTicketTaskFromValidatedSlot() {
        SessionSlotState slotState = SessionSlotState.empty();
        slotState.getTicket().setDeparture("广州");
        slotState.getTicket().setArrival("北京");
        slotState.getTicket().setDate("2026-06-01");
        AiChatRequestContext context = context(slotState, "查一下刚才那个");
        AgentPlan plan = AgentPlan.builder()
                .tasks(List.of(AgentTask.builder()
                        .type(AgentType.TICKET_INFO)
                        .parameters(Map.of())
                        .build()))
                .build();

        AgentPlan result = validator.validate(plan, context);

        assertFalse(result.hasClarification());
        assertEquals("广州", result.getTasks().get(0).getStringParam("departure"));
        assertEquals("北京", result.getTasks().get(0).getStringParam("arrival"));
        assertEquals("2026-06-01", result.getTasks().get(0).getStringParam("date"));
    }

    @Test
    void shouldWhitelistOrderQueryParameters() {
        AiChatRequestContext context = context(SessionSlotState.empty(), "查订单");
        AgentPlan plan = AgentPlan.builder()
                .tasks(List.of(AgentTask.builder()
                        .type(AgentType.ORDER_QUERY)
                        .parameters(Map.of("departure", "广州", "date", "2026-06-01", "count", "3"))
                        .build()))
                .build();

        AgentPlan result = validator.validate(plan, context);

        assertEquals(2, result.getTasks().get(0).getParameters().size());
        assertEquals("2026-06-01", result.getTasks().get(0).getStringParam("date"));
        assertEquals("3", result.getTasks().get(0).getStringParam("count"));
    }

    @Test
    void shouldBlockIncompleteTicketTask() {
        AiChatRequestContext context = context(SessionSlotState.empty(), "查票");
        AgentPlan plan = AgentPlan.builder()
                .tasks(List.of(AgentTask.builder()
                        .type(AgentType.TICKET_INFO)
                        .parameters(Map.of("departure", "广州"))
                        .build()))
                .build();

        AgentPlan result = validator.validate(plan, context);

        assertTrue(result.hasClarification());
        assertTrue(result.getTasks().isEmpty());
    }

    @Test
    void shouldBlockTicketTaskAfterOpenWindow() {
        AiChatRequestContext context = context(SessionSlotState.empty(), "查2999年广州到北京的票");
        AgentPlan plan = AgentPlan.builder()
                .tasks(List.of(AgentTask.builder()
                        .type(AgentType.TICKET_INFO)
                        .parameters(Map.of("departure", "广州", "arrival", "北京", "date", "2999-01-01"))
                        .build()))
                .build();

        AgentPlan result = validator.validate(plan, context);

        assertTrue(result.hasClarification());
        assertTrue(result.getClarification().contains("暂未开放"));
        assertTrue(result.getTasks().isEmpty());
    }

    private AiChatRequestContext context(SessionSlotState slotState, String message) {
        return AiChatRequestContext.builder()
                .currentMessage(AiChatMessage.user(1L, 1L, message))
                .sessionContext(AiSessionContext.builder()
                        .slotState(slotState)
                        .build())
                .build();
    }
}
