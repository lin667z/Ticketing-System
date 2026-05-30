package org.ticketing_system.biz.aiservice.session.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Compressed non-structured context. It must never be used as executable input.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryContext {

    @Builder.Default
    private List<String> facts = new ArrayList<>();

    public static SessionSummaryContext empty() {
        return SessionSummaryContext.builder()
                .facts(new ArrayList<>())
                .build();
    }
}
