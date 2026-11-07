package org.glstudio.nexus.utils;

import java.text.DecimalFormat;

/**
 * Short number and time formats for scoreboards and lore.
 */
public final class FormatUtils {

    private static final DecimalFormat FORMAT_ONE_DECIMAL = new DecimalFormat("#.#");

    private FormatUtils() {
    }

    /**
     * Abbreviates an amount: {@code 950}, {@code 1.5k}, {@code 2.3M}, {@code 4B}, {@code 1.2T}.
     *
     * @param amount the amount
     * @return the abbreviated text; amounts below 1000 are truncated to an integer
     */
    public static String formatMoneyToString(double amount) {
        if (amount < 1000) {
            return String.valueOf((int) amount);
        } else if (amount < 1_000_000) {
            return format(amount / 1_000D) + "k";
        } else if (amount < 1_000_000_000) {
            return format(amount / 1_000_000D) + "M";
        } else if (amount < 1_000_000_000_000L) {
            return format(amount / 1_000_000_000D) + "B";
        } else {
            return format(amount / 1_000_000_000_000D) + "T";
        }
    }

    private static String format(double value) {
        // DecimalFormat is not thread-safe and this is called from async placeholder requests.
        synchronized (FORMAT_ONE_DECIMAL) {
            return FORMAT_ONE_DECIMAL.format(value);
        }
    }

    /**
     * Formats milliseconds as hours and minutes, e.g. {@code 5h 3m}; empty below one minute.
     *
     * @param playtimeMillis the duration in milliseconds
     * @return the text
     * @deprecated drops seconds and days; use {@link DurationFormat}
     */
    @Deprecated(since = "2.0.0")
    public static String formatTimeToString(double playtimeMillis) {
        long totalSeconds = (long) (playtimeMillis / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m ");
        return sb.toString().trim();
    }
}
