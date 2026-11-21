package org.glstudio.nexus.utils;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Reads amounts typed by players: money, bets, counts of items.
 */
public final class Amounts {

    private Amounts() {
    }

    /**
     * Reads plain numbers, with optional thousands separators and a {@code k}, {@code m} or {@code b}
     * suffix ({@code 2.5k} is 2500).
     *
     * @param input what the player typed
     * @return the amount, or empty if it is not a positive finite number
     */
    public static OptionalDouble parse(String input) {
        String text = input.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
        if (text.isEmpty()) return OptionalDouble.empty();
        double multiplier = switch (text.charAt(text.length() - 1)) {
            case 'k' -> 1_000d;
            case 'm' -> 1_000_000d;
            case 'b' -> 1_000_000_000d;
            default -> 1d;
        };
        if (multiplier > 1) text = text.substring(0, text.length() - 1);
        try {
            double amount = Double.parseDouble(text) * multiplier;
            return Double.isFinite(amount) && amount > 0 ? OptionalDouble.of(amount) : OptionalDouble.empty();
        } catch (NumberFormatException notANumber) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Reads a count of items: a whole number, with the same separators and suffixes as
     * {@link #parse(String)}. Dots are read as thousands separators when there are three digits after
     * them ({@code 10.000} is ten thousand).
     *
     * @param input what the player typed
     * @return the count, or empty if it is not a whole number between 1 and {@link Integer#MAX_VALUE}
     */
    public static OptionalInt parseCount(String input) {
        String text = input.trim();
        if (text.matches("\\d{1,3}(\\.\\d{3})+")) text = text.replace(".", "");
        OptionalDouble amount = parse(text);
        if (amount.isEmpty()) return OptionalInt.empty();
        double value = amount.getAsDouble();
        if (value != Math.rint(value) || value > Integer.MAX_VALUE) return OptionalInt.empty();
        return OptionalInt.of((int) value);
    }
}
