package org.glstudio.nexus.modules.menu.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single-pass placeholder substitution for menu text. Recognises both {@code %token%} and
 * {@code {token}} — the audited plugins are split roughly evenly between the two — so migrating a
 * plugin's menus never requires rewriting its existing config's token syntax.
 *
 * <p>The pass is single-pass and left-to-right: a replacement value that itself contains
 * {@code %x%}/{@code {x}} is never re-scanned, so a player-supplied value can never inject a
 * placeholder of its own.
 */
public final class Placeholders {

    private static Boolean papiPresent;

    private Placeholders() {
    }

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

    public static List<String> apply(List<String> lines, Map<String, String> tokens) {
        if (lines == null) return List.of();
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) result.add(apply(line, tokens));
        return result;
    }

    /** {@link #apply(String, Map)} followed by PlaceholderAPI, if it's installed. */
    public static String applyWithPapi(Player player, String text, Map<String, String> tokens) {
        String applied = apply(text, tokens);
        if (applied != null && isPapiPresent()) {
            try {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, applied);
            } catch (Throwable ignored) {
                // PlaceholderAPI misbehaving shouldn't take the menu down with it.
            }
        }
        return applied;
    }

    private static boolean isPapiPresent() {
        if (papiPresent == null) {
            papiPresent = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        }
        return papiPresent;
    }
}
