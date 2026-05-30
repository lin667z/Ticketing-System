package org.ticketing_system.biz.aiservice.session;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

/**
 * 确定性地将用户输入的相对日期归一化为 yyyy-MM-dd 绝对日期。
 */
@Component
public class DateNormalizer {

    // ISO-8601 日期输出格式
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final long NEXT_WEEK_OFFSET = 1L;
    private static final long NEXT_NEXT_WEEK_OFFSET = 2L;
    private static final long TICKET_QUERY_MAX_DAYS_AFTER_TODAY = 14L;
    private static final int DAYS_PER_WEEK = 7;

    private static final Map<String, DayOfWeek> WEEKDAY_MAP = Map.of(
            "周一", DayOfWeek.MONDAY,
            "周二", DayOfWeek.TUESDAY,
            "周三", DayOfWeek.WEDNESDAY,
            "周四", DayOfWeek.THURSDAY,
            "周五", DayOfWeek.FRIDAY,
            "周六", DayOfWeek.SATURDAY,
            "周日", DayOfWeek.SUNDAY,
            "周天", DayOfWeek.SUNDAY
    );

    // 可注入的时钟，用于测试时固定时间
    private final Clock clock;

    public DateNormalizer() {
        this(Clock.systemDefaultZone());
    }

    // 包级可见，供测试注入固定时钟
    DateNormalizer(Clock clock) {
        this.clock = clock;
    }

    public String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.replaceAll("^\"+|\"+$", "").trim();
        if ("今天".equals(text)) {
            return LocalDate.now(clock).format(DATE_FORMATTER);
        }
        if ("明天".equals(text)) {
            return LocalDate.now(clock).plusDays(1L).format(DATE_FORMATTER);
        }
        if ("后天".equals(text)) {
            return LocalDate.now(clock).plusDays(2L).format(DATE_FORMATTER);
        }
        if (text.startsWith("下下") && text.length() > 2) {
            DayOfWeek target = WEEKDAY_MAP.get(text.substring(2));
            return target == null ? null : weekdayInRelativeWeek(target, NEXT_NEXT_WEEK_OFFSET).format(DATE_FORMATTER);
        }
        if (text.startsWith("下") && text.length() > 1) {
            DayOfWeek target = WEEKDAY_MAP.get(text.substring(1));
            return target == null ? null : weekdayInRelativeWeek(target, NEXT_WEEK_OFFSET).format(DATE_FORMATTER);
        }
        DayOfWeek target = WEEKDAY_MAP.get(text);
        if (target != null) {
            return nextWeekday(target, false).format(DATE_FORMATTER);
        }
        try {
            LocalDate parsed = LocalDate.parse(text, DATE_FORMATTER);
            return parsed.format(DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public boolean isValidDate(String value) {
        return normalize(value) != null && normalize(value).equals(value);
    }

    public boolean isTicketQueryDateOpen(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return false;
        }
        return !LocalDate.parse(normalized, DATE_FORMATTER).isAfter(LocalDate.now(clock).plusDays(TICKET_QUERY_MAX_DAYS_AFTER_TODAY));
    }

    public String maxTicketQueryDate() {
        return LocalDate.now(clock).plusDays(TICKET_QUERY_MAX_DAYS_AFTER_TODAY).format(DATE_FORMATTER);
    }

    private LocalDate nextWeekday(DayOfWeek target, boolean nextWeek) {
        LocalDate current = LocalDate.now(clock);
        int diff = target.getValue() - current.getDayOfWeek().getValue();
        if (diff < 0 || nextWeek) {
            diff += DAYS_PER_WEEK;
        }
        return current.plusDays(diff);
    }

    private LocalDate weekdayInRelativeWeek(DayOfWeek target, long weekOffset) {
        return LocalDate.now(clock)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(weekOffset)
                .with(TemporalAdjusters.nextOrSame(target));
    }
}
