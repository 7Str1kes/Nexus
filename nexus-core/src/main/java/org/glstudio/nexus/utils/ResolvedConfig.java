package org.glstudio.nexus.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Read-only view of a {@link ConfigFile} that resolves each key from the server's file, then from
 * the copy bundled in the jar, then from the caller's default.
 *
 * <p>This is the lookup most settings want: a key the administrator removed or never had falls back
 * to the plugin's shipped value rather than to a hard-coded constant. Values of the wrong type are
 * treated as missing.
 */
public final class ResolvedConfig {

    private final ConfigFile file;

    ResolvedConfig(ConfigFile file) {
        this.file = file;
    }

    /**
     * Returns the underlying file.
     *
     * @return the file
     */
    public ConfigFile file() {
        return file;
    }

    /**
     * Returns whether the file or the jar defines {@code path}.
     *
     * @param path the key
     * @return {@code true} if either has it
     */
    public boolean isSet(String path) {
        return file.resolvedSource(path) != 0;
    }

    /**
     * Returns a raw value.
     *
     * @param path the key
     * @return the value, or {@code null}
     */
    public @Nullable Object get(String path) {
        return file.resolvedValue(path);
    }

    /**
     * Returns an integer.
     *
     * @param path the key
     * @param def  used when neither the file nor the jar has a number there
     * @return the value
     */
    public int getInt(String path, int def) {
        return file.resolvedValue(path) instanceof Number number ? number.intValue() : def;
    }

    /**
     * Returns a long.
     *
     * @param path the key
     * @param def  used when neither the file nor the jar has a number there
     * @return the value
     */
    public long getLong(String path, long def) {
        return file.resolvedValue(path) instanceof Number number ? number.longValue() : def;
    }

    /**
     * Returns a double.
     *
     * @param path the key
     * @param def  used when neither the file nor the jar has a number there
     * @return the value
     */
    public double getDouble(String path, double def) {
        return file.resolvedValue(path) instanceof Number number ? number.doubleValue() : def;
    }

    /**
     * Returns a boolean.
     *
     * @param path the key
     * @param def  used when neither the file nor the jar has a boolean there
     * @return the value
     */
    public boolean getBoolean(String path, boolean def) {
        return file.resolvedValue(path) instanceof Boolean value ? value : def;
    }

    /**
     * Returns a string; scalars of other types are converted with {@code toString()}.
     *
     * @param path the key
     * @param def  used when neither the file nor the jar has the key
     * @return the value
     */
    public String getString(String path, String def) {
        Object value = file.resolvedValue(path);
        if (value == null || value instanceof ConfigurationSection) return def;
        return value.toString();
    }

    /**
     * Returns a list of strings, converting scalar elements with {@code toString()}.
     *
     * @param path the key
     * @return the list, empty when missing
     */
    public List<String> getStringList(String path) {
        if (!(file.resolvedValue(path) instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element != null && !(element instanceof List) && !(element instanceof java.util.Map)) {
                result.add(element.toString());
            }
        }
        return result;
    }

    /**
     * Returns a list of integers, skipping elements that are not numbers.
     *
     * @param path the key
     * @return the list, empty when missing
     */
    public List<Integer> getIntegerList(String path) {
        if (!(file.resolvedValue(path) instanceof List<?> list)) return List.of();
        List<Integer> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof Number number) result.add(number.intValue());
        }
        return result;
    }

    /**
     * Returns a raw list.
     *
     * @param path the key
     * @return the list, empty when missing
     */
    public List<?> getList(String path) {
        return file.resolvedValue(path) instanceof List<?> list ? list : List.of();
    }

    /**
     * Returns a section from the file, or from the jar if the file lacks it.
     *
     * @param path the key
     * @return the section, or {@code null}
     */
    public @Nullable ConfigurationSection getSection(String path) {
        return file.getConfigurationSection(path);
    }

    /**
     * Returns the keys of a section, from the file or the jar.
     *
     * @param path the section key
     * @param deep {@code true} to include nested keys
     * @return the keys, empty when the section is missing
     */
    public Set<String> getKeys(String path, boolean deep) {
        ConfigurationSection section = getSection(path);
        return section == null ? Collections.emptySet() : section.getKeys(deep);
    }
}
