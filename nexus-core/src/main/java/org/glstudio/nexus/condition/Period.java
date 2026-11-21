package org.glstudio.nexus.condition;

import org.glstudio.nexus.utils.DurationParser;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

/**
 * How long the uses of a {@code [limit]} last before they start again: {@code once} (never),
 * {@code day}, {@code week} and {@code month} (until the next midnight, Monday or first of the
 * month), or a length such as {@code 30m}, {@code 12h}, {@code 7d} or {@code 1h30m} (as
 * {@link DurationParser#tryParse(String)} reads it), counted from the first use.
 *
 * @param kind   which of them
 * @param length the length, for {@link Kind#LENGTH}
 */
public record Period(Kind kind, Duration length) {

    /** The kinds of period. */
    public enum Kind {
        /** Never starts again. */
        ONCE,
        /** Until the next midnight. */
        DAY,
        /** Until the next Monday. */
        WEEK,
        /** Until the first of the next month. */
        MONTH,
        /** A fixed length from the first use. */
        LENGTH
    }

    /**
     * Reads a period.
     *
     * @param raw the text
     * @return the period, or empty if it is not one
     */
    public static Optional<Period> parse(String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "once", "forever" -> Optional.of(new Period(Kind.ONCE, Duration.ZERO));
            case "day", "daily" -> Optional.of(new Period(Kind.DAY, Duration.ZERO));
            case "week", "weekly" -> Optional.of(new Period(Kind.WEEK, Duration.ZERO));
            case "month", "monthly" -> Optional.of(new Period(Kind.MONTH, Duration.ZERO));
            default -> DurationParser.tryParse(text).filter(length -> !length.isZero() && !length.isNegative())
                    .map(length -> new Period(Kind.LENGTH, length));
        };
    }

    /**
     * Returns when uses that start now end.
     *
     * @param now the time of the first use, in the time zone periods are counted in
     * @return epoch milliseconds, or {@link Long#MAX_VALUE} for {@link Kind#ONCE}
     */
    public long end(ZonedDateTime now) {
        return switch (kind) {
            case ONCE -> Long.MAX_VALUE;
            case DAY -> now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
            case WEEK -> now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(now.getZone())
                    .toInstant().toEpochMilli();
            case MONTH -> now.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay(now.getZone())
                    .toInstant().toEpochMilli();
            case LENGTH -> now.toInstant().plus(length).toEpochMilli();
        };
    }
}
