package org.glstudio.nexus.utils;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes colours as they are written in config files: {@code #RRGGBB}, {@code #AARRGGBB},
 * {@code #RRGGBB:alpha} (alpha 0-255), {@code r,g,b}, or a name. Names are those of MiniMessage
 * ({@code gold}, {@code dark_aqua}...) unless a palette of the caller's own is given, such as
 * {@link #BUKKIT} for fireworks or a plugin's tuned particle colours.
 */
public final class Colors {

    /** The colours Bukkit names in {@link Color}, the ones firework and leather settings use. */
    public static final Map<String, Color> BUKKIT = Map.ofEntries(
            Map.entry("white", Color.WHITE), Map.entry("silver", Color.SILVER), Map.entry("gray", Color.GRAY),
            Map.entry("black", Color.BLACK), Map.entry("red", Color.RED), Map.entry("maroon", Color.MAROON),
            Map.entry("yellow", Color.YELLOW), Map.entry("olive", Color.OLIVE), Map.entry("lime", Color.LIME),
            Map.entry("green", Color.GREEN), Map.entry("aqua", Color.AQUA), Map.entry("teal", Color.TEAL),
            Map.entry("blue", Color.BLUE), Map.entry("navy", Color.NAVY), Map.entry("fuchsia", Color.FUCHSIA),
            Map.entry("purple", Color.PURPLE), Map.entry("orange", Color.ORANGE));

    private Colors() {
    }

    /**
     * Reads a colour, naming colours as MiniMessage does.
     *
     * @param raw the text
     * @return the colour, or empty if not understood
     */
    public static Optional<Color> parse(@Nullable String raw) {
        return parse(raw, Map.of());
    }

    /**
     * Reads a colour; names of {@code palette} come first, then those of MiniMessage.
     *
     * @param raw     the text
     * @param palette names of the caller's own, lower case
     * @return the colour, or empty if not understood
     */
    public static Optional<Color> parse(@Nullable String raw, Map<String, Color> palette) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String value = raw.trim().toLowerCase(Locale.ROOT);
        Color own = palette.get(value);
        if (own == null) own = palette.get(value.replace('-', '_'));
        if (own != null) return Optional.of(own);
        String[] parts = value.split(",");
        if (parts.length == 3) return rgb(parts);
        int alpha = 255;
        int colon = value.indexOf(':');
        if (colon > 0) {
            try {
                alpha = Integer.parseInt(value.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (alpha < 0 || alpha > 255) return Optional.empty();
            value = value.substring(0, colon).trim();
        }
        if (value.startsWith("#")) {
            String hex = value.substring(1);
            try {
                if (hex.length() == 6) {
                    int rgb = Integer.parseInt(hex, 16);
                    return Optional.of(Color.fromARGB(alpha, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
                }
                if (hex.length() == 8 && colon < 0) return Optional.of(Color.fromARGB((int) Long.parseLong(hex, 16)));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            return Optional.empty();
        }
        NamedTextColor named = NamedTextColor.NAMES.value(value.replace('-', '_'));
        if (named == null) return Optional.empty();
        return Optional.of(Color.fromARGB(alpha, named.red(), named.green(), named.blue()));
    }

    /**
     * Writes a colour so {@link #parse(String)} reads it back.
     *
     * @param color the colour
     * @return {@code #RRGGBB}, with {@code :alpha} when not opaque
     */
    public static String format(Color color) {
        String hex = String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        return color.getAlpha() == 255 ? hex : hex + ":" + color.getAlpha();
    }

    private static Optional<Color> rgb(String[] parts) {
        try {
            return Optional.of(Color.fromRGB(channel(parts[0]), channel(parts[1]), channel(parts[2])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static int channel(String raw) {
        return Math.max(0, Math.min(255, Integer.parseInt(raw.trim())));
    }
}
