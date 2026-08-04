package org.glstudio.nexus.utils;

import java.util.Locale;

public enum TextFormat {
    LEGACY,
    MINIMESSAGE;

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
