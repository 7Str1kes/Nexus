package org.glstudio.nexus.modules.menu.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-pass placeholder substitution. Recognises both {@code %token%} and {@code {token}}, so
 * existing configuration files never need their token syntax rewritten.
 *
 * <p>The pass is single and left-to-right: a replacement value that itself contains a token is never
 * re-scanned, so a value typed by a player (a home name, a clan tag) cannot inject a placeholder of
 * its own.
 */
public final class Placeholders {

    private static volatile Boolean papiPresent;

    private Placeholders() {
    }

    /**
     * Replaces tokens named in {@code tokens}. Keys are bare names ({@code "page"}) and match both
     * {@code %page%} and {@code {page}}.
     *
     * @param text   the template, may be {@code null}
     * @param tokens bare token names mapped to their values
     * @return the result, or {@code null} for a {@code null} template
     */
    public static String apply(String text, Map<String, String> tokens) {
        if (text == null) return null;
        if (tokens == null || tokens.isEmpty()) return text;

        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        int length = text.length();

        while (i < length) {
            char c = text.charAt(i);
            if (c == '%' || c == '{') {
                char closing = c == '%' ? '%' : '}';
                int end = text.indexOf(closing, i + 1);
                if (end > i) {
                    String value = tokens.get(text.substring(i + 1, end));
                    if (value != null) {
                        result.append(value);
                        i = end + 1;
                        continue;
                    }
                }
            }
            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * Replaces tokens given as {@code token, value} pairs.
     *
     * <p>A token written with its delimiters ({@code "{player}"}, {@code "%player%"}) matches exactly
     * that text. A bare token ({@code "player"}) matches both {@code {player}} and {@code %player%}.
     * Values are converted with {@code String.valueOf}; a trailing token without value is ignored.
     *
     * @param text  the template, may be {@code null}
     * @param pairs alternating tokens and values
     * @return the result, or {@code null} for a {@code null} template
     */
    public static String apply(String text, Object... pairs) {
        if (text == null) return null;
        if (pairs == null || pairs.length < 2) return text;
        return substitute(text, exactTokens(pairs));
    }

    /**
     * Applies {@link #apply(String, Object...)} to every line.
     *
     * @param lines the templates, may be {@code null}
     * @param pairs alternating tokens and values
     * @return a new list, empty for {@code null}
     */
    public static List<String> apply(List<String> lines, Object... pairs) {
        if (lines == null) return new ArrayList<>();
        Map<String, String> tokens = pairs == null || pairs.length < 2 ? Map.of() : exactTokens(pairs);
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) result.add(tokens.isEmpty() ? line : substitute(line, tokens));
        return result;
    }

    /**
     * Applies {@link #apply(String, Map)} to every line.
     *
     * @param lines  the templates, may be {@code null}
     * @param tokens bare token names mapped to their values
     * @return a new list, empty for {@code null}
     */
    public static List<String> apply(List<String> lines, Map<String, String> tokens) {
        if (lines == null) return new ArrayList<>();
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) result.add(apply(line, tokens));
        return result;
    }

    /**
     * {@link #apply(String, Map)} followed by PlaceholderAPI, if it is installed.
     *
     * @param player the player PlaceholderAPI resolves against, may be {@code null}
     * @param text   the template
     * @param tokens bare token names mapped to their values
     * @return the result
     */
    public static String applyWithPapi(OfflinePlayer player, String text, Map<String, String> tokens) {
        return papi(player, apply(text, tokens));
    }

    /**
     * {@link #apply(String, Object...)} followed by PlaceholderAPI, if it is installed.
     *
     * @param player the player PlaceholderAPI resolves against, may be {@code null}
     * @param text   the template
     * @param pairs  alternating tokens and values
     * @return the result
     */
    public static String applyWithPapi(OfflinePlayer player, String text, Object... pairs) {
        return papi(player, apply(text, pairs));
    }

    /**
     * Resolves PlaceholderAPI placeholders in {@code text}, if PlaceholderAPI is installed.
     *
     * @param player the player to resolve against, may be {@code null}
     * @param text   the text
     * @return the text with PlaceholderAPI placeholders resolved, or unchanged
     */
    public static String papi(OfflinePlayer player, String text) {
        if (text == null || text.indexOf('%') < 0 || !isPapiPresent()) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable ignored) {
            // PlaceholderAPI misbehaving shouldn't take the caller down with it.
            return text;
        }
    }

    /**
     * Returns whether PlaceholderAPI is installed. Checked once, on first use.
     *
     * @return {@code true} if present
     */
    public static boolean isPapiPresent() {
        Boolean present = papiPresent;
        if (present == null) {
            try {
                present = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
            } catch (Throwable noServer) {
                present = false;
            }
            papiPresent = present;
        }
        return present;
    }

    private static Map<String, String> exactTokens(Object[] pairs) {
        Map<String, String> tokens = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String token = String.valueOf(pairs[i]);
            String value = String.valueOf(pairs[i + 1]);
            if (token.isEmpty()) continue;
            if (isDelimited(token)) {
                tokens.putIfAbsent(token, value);
            } else {
                tokens.putIfAbsent("{" + token + "}", value);
                tokens.putIfAbsent("%" + token + "%", value);
            }
        }
        return tokens;
    }

    private static boolean isDelimited(String token) {
        return !Character.isLetterOrDigit(token.charAt(0)) && token.charAt(0) != '_';
    }

    private static String substitute(String text, Map<String, String> tokens) {
        Map<Character, List<String>> byFirst = new HashMap<>();
        for (String token : tokens.keySet()) {
            byFirst.computeIfAbsent(token.charAt(0), key -> new ArrayList<>()).add(token);
        }
        // Longest first, so "{player_name}" wins over "{player" when both are tokens.
        byFirst.values().forEach(list -> list.sort(Comparator.comparingInt(String::length).reversed()));

        StringBuilder result = new StringBuilder(text.length() + 16);
        int i = 0;
        outer:
        while (i < text.length()) {
            List<String> candidates = byFirst.get(text.charAt(i));
            if (candidates != null) {
                for (String token : candidates) {
                    if (text.startsWith(token, i)) {
                        result.append(tokens.get(token));
                        i += token.length();
                        continue outer;
                    }
                }
            }
            result.append(text.charAt(i++));
        }
        return result.toString();
    }
}
