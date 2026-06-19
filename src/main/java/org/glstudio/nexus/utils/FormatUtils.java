package org.glstudio.nexus.utils;

import java.text.DecimalFormat;

public class FormatUtils {

    private static final DecimalFormat FORMAT_ONE_DECIMAL = new DecimalFormat("#.#");

    public static String formatMoneyToString(double amount) {
        if (amount < 1000) {
            return String.valueOf((int) amount);
        } else if (amount < 1_000_000) {
            return FORMAT_ONE_DECIMAL.format(amount / 1_000D) + "k";
        } else if (amount < 1_000_000_000) {
            return FORMAT_ONE_DECIMAL.format(amount / 1_000_000D) + "M";
        } else if (amount < 1_000_000_000_000L) {
            return FORMAT_ONE_DECIMAL.format(amount / 1_000_000_000D) + "B";
        } else {
            return FORMAT_ONE_DECIMAL.format(amount / 1_000_000_000_000D) + "T";
        }
    }

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