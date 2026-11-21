package org.glstudio.nexus.condition;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodTest {

    private static final ZonedDateTime NOW = LocalDateTime.of(2026, 11, 20, 15, 30).atZone(ZoneId.of("Europe/Madrid"));

    private static long end(String raw) {
        return Period.parse(raw).orElseThrow().end(NOW);
    }

    private static long at(int month, int day) {
        return LocalDateTime.of(2026, month, day, 0, 0).atZone(ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli();
    }

    @Test
    void calendarPeriodsEndAtTheirStart() {
        assertEquals(at(11, 21), end("day"));
        assertEquals(at(11, 23), end("week"), "the next Monday");
        assertEquals(at(12, 1), end("month"));
        assertEquals(Long.MAX_VALUE, end("once"));
    }

    @Test
    void lengthsCountFromNow() {
        long now = NOW.toInstant().toEpochMilli();
        assertEquals(now + 12 * 3_600_000L, end("12h"));
        assertEquals(now + 30 * 60_000L, end("30m"));
        assertEquals(now + 14 * 86_400_000L, end("2w"));
        assertEquals(now + 90 * 60_000L, end("1h30m"), "combined units, as DurationParser reads them");
        assertTrue(Period.parse("0h").isEmpty());
        assertTrue(Period.parse("soon").isEmpty());
    }
}
