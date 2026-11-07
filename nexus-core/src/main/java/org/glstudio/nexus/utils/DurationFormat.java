package org.glstudio.nexus.utils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders durations for chat, menus and scoreboards. Negative durations render as zero.
 */
public final class DurationFormat {

    private DurationFormat() {
    }

    /**
     * Suffixes of each unit, so they can come from a language file.
     *
     * @param day    suffix after days
     * @param hour   suffix after hours
     * @param minute suffix after minutes
     * @param second suffix after seconds
     */
    public record UnitNames(String day, String hour, String minute, String second) {

        /** {@code d}, {@code h}, {@code m}, {@code s}. */
        public static final UnitNames SHORT = new UnitNames("d", "h", "m", "s");

        /**
         * Creates unit names, rejecting {@code null}.
         *
         * @param day    suffix after days
         * @param hour   suffix after hours
         * @param minute suffix after minutes
         * @param second suffix after seconds
         */
        public UnitNames {
            Objects.requireNonNull(day, "day");
            Objects.requireNonNull(hour, "hour");
            Objects.requireNonNull(minute, "minute");
            Objects.requireNonNull(second, "second");
        }
    }

    /**
     * Every non-zero unit from days to seconds, e.g. {@code 1d 2h 5s}; {@code 0s} for zero.
     *
     * @param duration the duration
     * @return the text
     */
    public static String compact(Duration duration) {
        return compact(duration, UnitNames.SHORT, " ", ChronoUnit.DAYS);
    }

    /**
     * Every non-zero unit from {@code largest} down to seconds; the seconds part is shown when
     * everything else is zero.
     *
     * @param duration  the duration
     * @param names     unit suffixes
     * @param separator text between parts
     * @param largest   {@link ChronoUnit#DAYS} or {@link ChronoUnit#HOURS}; with hours, days roll
     *                  into hours ({@code 26h})
     * @return the text
     */
    public static String compact(Duration duration, UnitNames names, String separator, ChronoUnit largest) {
        long total = seconds(duration);
        long days = largest == ChronoUnit.DAYS ? total / 86_400 : 0;
        total -= days * 86_400;
        long hours = total / 3_600;
        long minutes = (total % 3_600) / 60;
        long secs = total % 60;

        List<String> parts = new ArrayList<>(4);
        if (days > 0) parts.add(days + names.day());
        if (hours > 0) parts.add(hours + names.hour());
        if (minutes > 0) parts.add(minutes + names.minute());
        if (secs > 0 || parts.isEmpty()) parts.add(secs + names.second());
        return String.join(separator, parts);
    }

    /**
     * Hours, minutes and seconds, dropping leading zero units but keeping inner ones: {@code 1h 0m 5s},
     * {@code 2m 0s}, {@code 45s}.
     *
     * @param duration  the duration
     * @param separator text between parts, e.g. {@code " "} or {@code ", "}
     * @return the text
     */
    public static String leading(Duration duration, String separator) {
        long total = seconds(duration);
        long hours = total / 3_600;
        long minutes = (total % 3_600) / 60;
        long secs = total % 60;
        if (hours > 0) return hours + "h" + separator + minutes + "m" + separator + secs + "s";
        if (minutes > 0) return minutes + "m" + separator + secs + "s";
        return secs + "s";
    }

    /**
     * Only the largest non-zero unit among hours, minutes and seconds: {@code 1h}, {@code 20m},
     * {@code 45s}.
     *
     * @param duration the duration
     * @return the text
     */
    public static String largestUnit(Duration duration) {
        long total = seconds(duration);
        if (total >= 3_600) return total / 3_600 + "h";
        if (total >= 60) return total / 60 + "m";
        return total + "s";
    }

    /**
     * A clock without days: {@code 1:01:01} when there are hours, otherwise minutes and seconds.
     *
     * @param duration   the duration
     * @param padMinutes {@code true} for {@code 05:30}, {@code false} for {@code 5:30} when there are
     *                   no hours
     * @return the text
     */
    public static String clock(Duration duration, boolean padMinutes) {
        long total = seconds(duration);
        long hours = total / 3_600;
        long minutes = (total % 3_600) / 60;
        long secs = total % 60;
        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, secs);
        return String.format(padMinutes ? "%02d:%02d" : "%d:%02d", minutes, secs);
    }

    /**
     * A fully padded clock: {@code 00:20:34}.
     *
     * @param duration the duration
     * @return the text
     */
    public static String clockPadded(Duration duration) {
        long total = seconds(duration);
        return String.format("%02d:%02d:%02d", total / 3_600, (total % 3_600) / 60, total % 60);
    }

    /**
     * How long ago something happened, with the two largest units: {@code 3d 2h ago},
     * {@code 5h 10m ago}, {@code 4m ago}, at least {@code 1s ago}.
     *
     * @param elapsed time since the event
     * @return the text
     */
    public static String ago(Duration elapsed) {
        Duration duration = elapsed.isNegative() ? Duration.ZERO : elapsed;
        long days = duration.toDays();
        if (days > 0) return days + "d " + (duration.toHours() % 24) + "h ago";
        long hours = duration.toHours();
        if (hours > 0) return hours + "h " + (duration.toMinutes() % 60) + "m ago";
        long minutes = duration.toMinutes();
        if (minutes > 0) return minutes + "m ago";
        return Math.max(1, duration.toSeconds()) + "s ago";
    }

    /**
     * Fills {@code {days}}, {@code {hours}}, {@code {minutes}} and {@code {seconds}} in a pattern. A
     * unit only rolls into a larger one if that larger unit appears in the pattern, so no time is
     * silently dropped: {@code {hours}h {minutes}m} shows {@code 50h 5m}.
     *
     * @param duration the duration
     * @param pattern  the pattern
     * @return the text
     */
    public static String pattern(Duration duration, String pattern) {
        long remaining = seconds(duration);
        long days = 0;
        long hours = 0;
        long minutes = 0;
        long secs = 0;

        if (pattern.contains("{days}")) {
            days = remaining / 86_400L;
            remaining -= days * 86_400L;
        }
        if (pattern.contains("{hours}")) {
            hours = remaining / 3_600L;
            remaining -= hours * 3_600L;
        }
        if (pattern.contains("{minutes}")) {
            minutes = remaining / 60L;
            remaining -= minutes * 60L;
        }
        if (pattern.contains("{seconds}")) {
            secs = remaining;
        }

        return pattern
                .replace("{days}", String.valueOf(days))
                .replace("{hours}", String.valueOf(hours))
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{seconds}", String.valueOf(secs));
    }

    private static long seconds(Duration duration) {
        return duration == null || duration.isNegative() ? 0 : duration.toSeconds();
    }
}
