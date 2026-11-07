package org.glstudio.nexus.utils;

import java.util.Locale;

/**
 * How a text in a configuration file is written.
 */
public enum TextFormat {
    /**
     * {@code &} colour codes, with {@code &#rrggbb} hex.
     */
    LEGACY,
    /**
     * MiniMessage tags; legacy codes are accepted too.
     */
    MINIMESSAGE;
    /**
     * Parses a configured value: {@code legacy}, {@code ampersand}, {@code section}, {@code minimessage},
     * {@code mm} or {@code mini}, ignoring case, dashes and underscores.
     *
     * @param raw      the configured value
     * @param fallback used for a blank or unknown value; unknown values are also logged
     * @return the format
     */
    public static TextFormat parse(String raw, TextFormat fallback) {
        if (raw == null || raw.isBlank()) return fallback;

        String value = raw.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        return switch (value) {
            case "LEGACY", "AMPERSAND", "SECTION" -> LEGACY;
            case "MINIMESSAGE", "MM", "MINI" -> MINIMESSAGE;
            default -> {
                LoggerUtils.logWarn("Unknown text format '" + raw + "'; using " + fallback + ".");
                yield fallback;
            }
        };
    }
}
