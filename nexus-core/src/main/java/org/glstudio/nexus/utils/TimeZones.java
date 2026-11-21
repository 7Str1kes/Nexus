package org.glstudio.nexus.utils;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Reads the time zone a config file names, such as {@code Europe/Madrid}.
 */
public final class TimeZones {

    private TimeZones() {
    }

    /**
     * Reads a time zone.
     *
     * @param raw the configured value; blank for the server's
     * @return the time zone, or empty if it is not one
     */
    public static Optional<ZoneId> tryParse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.of(ZoneId.systemDefault());
        try {
            return Optional.of(ZoneId.of(raw.trim()));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads a time zone, falling back to the server's.
     *
     * @param raw  the configured value; blank for the server's
     * @param warn told when the value is not a time zone
     * @return the time zone, the server's when blank or unknown
     */
    public static ZoneId parse(String raw, Consumer<String> warn) {
        Optional<ZoneId> zone = tryParse(raw);
        if (zone.isEmpty()) warn.accept("'" + raw + "' is not a time zone; the server's is used.");
        return zone.orElseGet(ZoneId::systemDefault);
    }
}
