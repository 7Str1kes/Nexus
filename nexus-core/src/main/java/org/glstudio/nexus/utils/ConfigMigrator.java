package org.glstudio.nexus.utils;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Upgrades a server's configuration file in place when a plugin changes keys, keeping every value
 * the administrator customised.
 *
 * <p>Each step is registered under the version it produces. {@link #migrate(int)} runs the steps
 * between the file's {@value ConfigFile#VERSION_KEY} and the target, in order, then writes the new
 * version and saves the file atomically. A file that needs no step is left untouched on disk.
 *
 * <pre>{@code
 * ConfigMigrator.of(configManager.getMainConfig())
 *         .step(2, cfg -> ConfigMigrator.move(cfg, "MULTI_SERVER.REDIS.HOST", "multi-server.redis.host"))
 *         .migrate(2);
 * }</pre>
 */
public final class ConfigMigrator {

    private final ConfigFile file;
    private final Map<Integer, Consumer<ConfigFile>> steps = new TreeMap<>();

    private ConfigMigrator(ConfigFile file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Starts a migration of {@code file}.
     *
     * @param file the file to upgrade
     * @return a migrator without steps
     */
    public static ConfigMigrator of(ConfigFile file) {
        return new ConfigMigrator(file);
    }

    /**
     * Registers the step that upgrades the file to {@code version}.
     *
     * @param version the version the step produces
     * @param step    edits the file in memory
     * @return this migrator
     */
    public ConfigMigrator step(int version, Consumer<ConfigFile> step) {
        steps.put(version, Objects.requireNonNull(step, "step"));
        return this;
    }

    /**
     * Runs the pending steps up to {@code target}.
     *
     * @param target the version this build of the plugin expects
     * @return the version the file had before migrating
     */
    public int migrate(int target) {
        int from = file.version();
        boolean changed = false;
        for (Map.Entry<Integer, Consumer<ConfigFile>> entry : steps.entrySet()) {
            int version = entry.getKey();
            if (version <= from || version > target) continue;
            entry.getValue().accept(file);
            changed = true;
            LoggerUtils.logInfo("Migrated " + file.getFileName() + " to version " + version + ".");
        }
        if (changed) {
            file.set(ConfigFile.VERSION_KEY, target);
            file.saveAtomically();
        }
        return from;
    }

    /**
     * Moves the value or section at {@code from} to {@code to}, unless {@code to} already exists.
     *
     * @param section the section to edit
     * @param from    the old key
     * @param to      the new key
     * @return {@code true} if something was moved
     */
    public static boolean move(ConfigurationSection section, String from, String to) {
        if (!section.isSet(from) || section.isSet(to)) return false;
        Object value = section.get(from);
        if (value instanceof ConfigurationSection child) {
            section.createSection(to, child.getValues(true));
        } else {
            section.set(to, value);
        }
        section.set(from, null);
        return true;
    }

    /**
     * Renames a key in place; an alias of {@link #move(ConfigurationSection, String, String)}.
     *
     * @param section the section to edit
     * @param from    the old key
     * @param to      the new key
     * @return {@code true} if something was renamed
     */
    public static boolean rename(ConfigurationSection section, String from, String to) {
        return move(section, from, to);
    }
}
