package org.glstudio.nexus.utils;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/**
 * Spelling of configuration keys. Plugins differ: most write {@code custom-model-data}, some write
 * {@code CUSTOM_MODEL_DATA}. Readers take a logical key in kebab case and look it up in the style of
 * the file, so existing files keep working.
 */
public enum KeyStyle {

    /** {@code custom-model-data}. */
    KEBAB,
    /** {@code CUSTOM_MODEL_DATA}. */
    UPPER_SNAKE,
    /** Accepts both, preferring kebab case. */
    ANY;

    /**
     * Returns the spellings to try for a logical key.
     *
     * @param kebabKey the key in kebab case, possibly dotted ({@code redis.allow-insecure})
     * @return candidate keys, in order of preference
     */
    public List<String> candidates(String kebabKey) {
        String upper = kebabKey.replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (this) {
            case KEBAB -> List.of(kebabKey);
            case UPPER_SNAKE -> List.of(upper);
            case ANY -> kebabKey.equals(upper) ? List.of(kebabKey) : List.of(kebabKey, upper);
        };
    }

    /**
     * Finds the spelling of {@code kebabKey} that {@code section} uses.
     *
     * @param section  the section to search
     * @param kebabKey the logical key
     * @return the key present in the section, or the preferred spelling if none is present
     */
    public String resolve(ConfigurationSection section, String kebabKey) {
        List<String> candidates = candidates(kebabKey);
        if (section != null) {
            for (String candidate : candidates) {
                if (section.contains(candidate)) return candidate;
            }
        }
        return candidates.getFirst();
    }
}
