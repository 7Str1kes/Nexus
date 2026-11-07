package org.glstudio.nexus.utils;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses durations typed by players or administrators, such as {@code 1h30m} or {@code 7d}.
 */
public final class DurationParser {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhd])");
    private static final Pattern STRICT_PART = Pattern.compile("(\\d+)([smhdw])");
    private static final Pattern STRICT_WHOLE = Pattern.compile("(\\d+[smhdw]\\s*)+");
    private static final Pattern BARE_NUMBER = Pattern.compile("\\d+");

    private DurationParser() {
    }

    /**
     * Sums every {@code <number><s|m|h|d>} part found in {@code input}, ignoring anything else.
     *
     * <p>Lenient on purpose and kept for compatibility: {@code "abc"} parses as zero. Prefer
     * {@link #tryParse(String)} for input that must be validated.
     *
     * @param input the text
     * @return the total, {@link Duration#ZERO} if nothing matches
     */
    public static Duration parse(String input) {
        Matcher matcher = TIME_PATTERN.matcher(input.toLowerCase(Locale.ROOT));
        Duration duration = Duration.ZERO;

        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            duration = switch (unit) {
                case "s" -> duration.plusSeconds(value);
                case "m" -> duration.plusMinutes(value);
                case "h" -> duration.plusHours(value);
                case "d" -> duration.plusDays(value);
                default -> duration;
            };
        }

        return duration;
    }

    /**
     * Parses a duration whose every part has a unit: {@code s}, {@code m}, {@code h}, {@code d} or
     * {@code w}, e.g. {@code 30m}, {@code 1h30m}, {@code 2d 12h}.
     *
     * @param input the text, case-insensitive
     * @return the duration, or empty if anything in the input is not a valid part or the total
     * overflows
     */
    public static Optional<Duration> tryParse(String input) {
        return tryParse(input, false);
    }

    /**
     * Parses a duration like {@link #tryParse(String)}, optionally accepting a bare number as
     * seconds.
     *
     * @param input               the text, case-insensitive
     * @param bareNumberIsSeconds {@code true} to read {@code "90"} as 90 seconds
     * @return the duration, or empty if the input is invalid or overflows
     */
    public static Optional<Duration> tryParse(String input, boolean bareNumberIsSeconds) {
        if (input == null) return Optional.empty();
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return Optional.empty();

        try {
            if (bareNumberIsSeconds && BARE_NUMBER.matcher(trimmed).matches()) {
                return Optional.of(Duration.ofSeconds(Long.parseLong(trimmed)));
            }
            if (!STRICT_WHOLE.matcher(trimmed).matches()) return Optional.empty();

            long seconds = 0;
            Matcher matcher = STRICT_PART.matcher(trimmed);
            while (matcher.find()) {
                long value = Long.parseLong(matcher.group(1));
                long unit = switch (matcher.group(2)) {
                    case "m" -> 60L;
                    case "h" -> 3_600L;
                    case "d" -> 86_400L;
                    case "w" -> 604_800L;
                    default -> 1L;
                };
                seconds = Math.addExact(seconds, Math.multiplyExact(value, unit));
            }
            return Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException | ArithmeticException tooLarge) {
            return Optional.empty();
        }
    }
}
