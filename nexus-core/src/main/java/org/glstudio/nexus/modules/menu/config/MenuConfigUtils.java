package org.glstudio.nexus.modules.menu.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Slot and material parsing for config-driven menus.
 */
public final class MenuConfigUtils {

    private MenuConfigUtils() {
    }

    /**
     * Reads a slot list at {@code path}: a single int, a {@code "from-to"} range, or a list mixing
     * both. Unparseable entries are skipped rather than failing the whole menu.
     *
     * @param section the section, may be {@code null}
     * @param path    the key
     * @return the slots, in order, possibly with duplicates
     */
    public static List<Integer> parseSlots(ConfigurationSection section, String path) {
        List<Integer> result = new ArrayList<>();
        if (section == null || !section.contains(path)) return result;

        Object raw = section.get(path);
        if (raw instanceof List<?> list) {
            for (Object entry : list) {
                result.addAll(parseToken(String.valueOf(entry)));
            }
        } else {
            result.addAll(parseToken(String.valueOf(raw)));
        }
        return result;
    }

    private static List<Integer> parseToken(String token) {
        String trimmed = token.trim();
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-", 2);
            try {
                int from = Integer.parseInt(parts[0].trim());
                int to = Integer.parseInt(parts[1].trim());
                List<Integer> range = new ArrayList<>();
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) range.add(i);
                return range;
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        try {
            return List.of(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
            return List.of();
        }
    }

    /**
     * Parses a material leniently ({@link Material#matchMaterial(String)}), without warning.
     *
     * @param name     the configured value, may be {@code null}
     * @param fallback used for a missing or unknown value
     * @return the material
     */
    public static Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        Material material = Material.matchMaterial(name.trim().replace('-', '_'));
        return material != null ? material : fallback;
    }
}
