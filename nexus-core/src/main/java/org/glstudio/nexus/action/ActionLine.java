package org.glstudio.nexus.action;

import java.util.Locale;
import java.util.Optional;

/**
 * One configured action, written as {@code [type] value}; a line without a type is a console
 * command.
 *
 * <pre>{@code
 * - "give %player% diamond 1"          # console
 * - "[player] spawn"
 * - "[message] &aYou won!"
 * - "[title] &6Winner|&7well done"
 * - "[sound] entity.player.levelup:1:1"
 * }</pre>
 *
 * @param type  the action type in lower case, e.g. {@code console}
 * @param value the text after the type
 */
public record ActionLine(String type, String value) {

    /** Type of a line without {@code [type]}. */
    public static final String DEFAULT_TYPE = "console";

    /**
     * Parses a line. A leading slash is removed from command values.
     *
     * @param raw the configured line
     * @return the action, or empty for a blank line, an unclosed bracket or an empty value
     */
    public static Optional<ActionLine> parse(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();

        if (!trimmed.startsWith("[")) return Optional.of(new ActionLine(DEFAULT_TYPE, stripSlash(trimmed)));

        int close = trimmed.indexOf(']');
        if (close < 0) return Optional.empty();
        String type = trimmed.substring(1, close).trim().toLowerCase(Locale.ROOT);
        String value = trimmed.substring(close + 1).trim();
        if (type.isEmpty() || value.isEmpty()) return Optional.empty();
        if (type.equals("console") || type.equals("command") || type.equals("player")) value = stripSlash(value);
        return Optional.of(new ActionLine(type, value));
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
