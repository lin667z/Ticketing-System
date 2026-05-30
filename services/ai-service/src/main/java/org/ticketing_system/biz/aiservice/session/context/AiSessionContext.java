package org.ticketing_system.biz.aiservice.session.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-turn context bundle consumed by Master Agent and validators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionContext {

    @Builder.Default
    private SessionSlotState slotState = SessionSlotState.empty();

    @Builder.Default
    private SessionSummaryContext summaryContext = SessionSummaryContext.empty();

    @Builder.Default
    private List<LlmRequest.Message> recentTurns = new ArrayList<>();

    private int turnCount;

    private boolean recovered;
}
