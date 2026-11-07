package org.glstudio.nexus.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationTest {

    @Test
    void lenientParseKeepsItsHistoricBehaviour() {
        assertEquals(Duration.ofMinutes(90), DurationParser.parse("1h30m"));
        assertEquals(Duration.ZERO, DurationParser.parse("abc"));
        assertEquals(Duration.ofSeconds(5), DurationParser.parse("x5sx"));
    }

    @Test
    void strictParseAcceptsUnitsAndSpaces() {
        assertEquals(Optional.of(Duration.ofSeconds(30)), DurationParser.tryParse("30s"));
        assertEquals(Optional.of(Duration.ofMinutes(90)), DurationParser.tryParse("1h30m"));
        assertEquals(Optional.of(Duration.ofHours(60)), DurationParser.tryParse("2d 12h"));
        assertEquals(Optional.of(Duration.ofDays(14)), DurationParser.tryParse("2W"));
    }

    @Test
    void strictParseRejectsGarbage() {
        assertEquals(Optional.empty(), DurationParser.tryParse("abc"));
        assertEquals(Optional.empty(), DurationParser.tryParse("10x"));
        assertEquals(Optional.empty(), DurationParser.tryParse("1h junk"));
        assertEquals(Optional.empty(), DurationParser.tryParse(""));
        assertEquals(Optional.empty(), DurationParser.tryParse(null));
        assertEquals(Optional.empty(), DurationParser.tryParse("90"));
    }

    @Test
    void strictParseRejectsOverflow() {
        assertEquals(Optional.empty(), DurationParser.tryParse("99999999999999999999d"));
        assertEquals(Optional.empty(), DurationParser.tryParse("9223372036854775807w"));
    }

    @Test
    void bareNumbersAreSecondsOnlyWhenAsked() {
        assertEquals(Optional.of(Duration.ofSeconds(90)), DurationParser.tryParse("90", true));
        assertEquals(Optional.of(Duration.ofMinutes(2)), DurationParser.tryParse("2m", true));
    }

    @Test
    void formatsMatchTheFormatsPluginsUsed() {
        Duration d = Duration.ofSeconds(3_661);
        assertEquals("1h 1m 1s", DurationFormat.compact(d));
        assertEquals("1d 1h", DurationFormat.compact(Duration.ofHours(25)));
        assertEquals("25h", DurationFormat.compact(Duration.ofHours(25), DurationFormat.UnitNames.SHORT, " ", ChronoUnit.HOURS));
        assertEquals("0s", DurationFormat.compact(Duration.ZERO));
        assertEquals("1h 0m 5s", DurationFormat.leading(Duration.ofSeconds(3_605), " "));
        assertEquals("22h, 30m, 10s", DurationFormat.leading(Duration.ofSeconds(81_010), ", "));
        assertEquals("45s", DurationFormat.leading(Duration.ofSeconds(45), ", "));
        assertEquals("20m", DurationFormat.largestUnit(Duration.ofSeconds(1_234)));
        assertEquals("5:30", DurationFormat.clock(Duration.ofSeconds(330), false));
        assertEquals("05:30", DurationFormat.clock(Duration.ofSeconds(330), true));
        assertEquals("1:01:01", DurationFormat.clock(d, true));
        assertEquals("00:20:34", DurationFormat.clockPadded(Duration.ofSeconds(1_234)));
        assertEquals("3d 2h ago", DurationFormat.ago(Duration.ofHours(74)));
        assertEquals("1s ago", DurationFormat.ago(Duration.ZERO));
        assertEquals("50h 5m", DurationFormat.pattern(Duration.ofMinutes(3_005), "{hours}h {minutes}m"));
        assertEquals("2d 2h 5m", DurationFormat.pattern(Duration.ofMinutes(3_005), "{days}d {hours}h {minutes}m"));
        assertEquals("0s", DurationFormat.compact(Duration.ofSeconds(-5)));
    }
}
