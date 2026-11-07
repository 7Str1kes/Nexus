package org.glstudio.nexus.utils;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Collects configuration problems found while validating, so they can be reported together at the
 * end of loading instead of aborting on the first one.
 *
 * <p>Checks only look at keys present in the server's file: a missing key falls back to the
 * bundled default and is not a problem.
 */
public final class ConfigProblems {

    private final List<String> problems = new ArrayList<>();

    /**
     * Records a problem.
     *
     * @param problem a full sentence naming the key and what will happen instead
     */
    public void add(String problem) {
        problems.add(problem);
    }

    /**
     * Checks that an integer is not below {@code minimum}.
     *
     * @param section the section to read
     * @param path    the key
     * @param minimum the lowest usable value
     */
    public void atLeast(ConfigurationSection section, String path, int minimum) {
        if (section == null || !section.isSet(path)) return;
        int value = section.getInt(path, minimum);
        if (value < minimum) add(path + " is " + value + "; the lowest usable value is " + minimum + ".");
    }

    /**
     * Checks that a number lies within {@code [minimum, maximum]}.
     *
     * @param section the section to read
     * @param path    the key
     * @param minimum the lowest usable value
     * @param maximum the highest usable value
     */
    public void between(ConfigurationSection section, String path, double minimum, double maximum) {
        if (section == null || !section.isSet(path)) return;
        double value = section.getDouble(path, minimum);
        if (value < minimum || value > maximum) {
            add(path + " is " + value + "; it must be between " + minimum + " and " + maximum + ".");
        }
    }

    /**
     * Checks that a string names a constant of {@code type}, ignoring case.
     *
     * @param section the section to read
     * @param path    the key
     * @param type    the enum
     * @param <E>     the enum type
     */
    public <E extends Enum<E>> void enumValue(ConfigurationSection section, String path, Class<E> type) {
        if (section == null || !section.isSet(path)) return;
        String value = section.getString(path, "");
        try {
            Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            add(path + " is '" + value + "', which is not a valid " + type.getSimpleName() + ".");
        }
    }

    /**
     * Checks that a string is one of {@code allowed}, ignoring case.
     *
     * @param section the section to read
     * @param path    the key
     * @param allowed the accepted values
     */
    public void oneOf(ConfigurationSection section, String path, Collection<String> allowed) {
        if (section == null || !section.isSet(path)) return;
        String value = section.getString(path, "");
        for (String candidate : allowed) {
            if (candidate.equalsIgnoreCase(value)) return;
        }
        add(path + " is '" + value + "'; expected one of " + String.join(", ", allowed) + ".");
    }

    /**
     * Returns whether no problem was recorded.
     *
     * @return {@code true} if the configuration is clean
     */
    public boolean isEmpty() {
        return problems.isEmpty();
    }

    /**
     * Returns the recorded problems.
     *
     * @return an unmodifiable list, in the order found
     */
    public List<String> list() {
        return Collections.unmodifiableList(problems);
    }

    /**
     * Logs every problem as a warning prefixed with {@code fileName}, plus a closing line.
     *
     * @param fileName the file the problems come from, e.g. {@code config.yml}
     * @return the number of problems
     */
    public int report(String fileName) {
        for (String problem : problems) {
            LoggerUtils.logWarn(fileName + ": " + problem);
        }
        if (!problems.isEmpty()) {
            LoggerUtils.logWarn("The settings above are being ignored or clamped. Everything else loaded normally.");
        }
        return problems.size();
    }
}
