package org.ticketing_system.biz.aiservice.session;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateNormalizerTest {

    private final DateNormalizer dateNormalizer = new DateNormalizer(
            Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void shouldNormalizeRelativeDates() {
        assertEquals("2026-05-29", dateNormalizer.normalize("明天"));
        assertEquals("2026-05-30", dateNormalizer.normalize("后天"));
    }

    @Test
    void shouldNormalizeWeekdayToFutureDate() {
        assertEquals("2026-05-29", dateNormalizer.normalize("周五"));
        assertEquals("2026-06-01", dateNormalizer.normalize("下周一"));
        assertEquals("2026-06-12", dateNormalizer.normalize("下下周五"));
    }

    @Test
    void shouldNormalizeSameWeekdayToToday() {
        DateNormalizer fridayNormalizer = new DateNormalizer(
                Clock.fixed(Instant.parse("2026-05-29T00:00:00Z"), ZoneId.of("Asia/Shanghai")));

        assertEquals("2026-05-29", fridayNormalizer.normalize("周五"));
    }

    @Test
    void shouldRejectAmbiguousDate() {
        assertNull(dateNormalizer.normalize("五一"));
    }

    @Test
    void shouldLimitTicketQueryDateToFourteenDaysAfterToday() {
        assertEquals("2026-06-11", dateNormalizer.maxTicketQueryDate());
        assertTrue(dateNormalizer.isTicketQueryDateOpen("2026-06-11"));
        assertFalse(dateNormalizer.isTicketQueryDateOpen("2026-06-12"));
        assertFalse(dateNormalizer.isTicketQueryDateOpen("下下周五"));
    }
}
