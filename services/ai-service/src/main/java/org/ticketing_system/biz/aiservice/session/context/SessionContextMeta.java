package org.ticketing_system.biz.aiservice.session.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis metadata for a session context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContextMeta {

    private int turnCount;

    private long lastActiveTime;
}
