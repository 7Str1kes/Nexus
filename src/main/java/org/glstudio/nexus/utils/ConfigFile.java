package org.glstudio.nexus.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A configuration file that falls back to the copy bundled in the jar, so a key added in a new
 * version of a plugin still resolves against a file the server generated before it existed.
 *
 * <p><b>Threading.</b> Reloading replaces this configuration's contents in place — the map is
 * cleared and repopulated — while other threads may be reading from it. A plugin that reads a
 * message inside a database callback would otherwise be able to observe the file mid-swap and
 * find a key that exists in neither the old contents nor the new. A read-write lock keeps the
 * accessors below and {@link #reload()} apart, so a reader sees one version of the file or the
 * other and never the gap between them.
 *
 * <p>The guarantee covers the accessors this class declares, which is how configuration is read
 * in practice. A {@link ConfigurationSection} from {@link #getConfigurationSection} is a live
 * view of the map behind it: retrieving one is safe, but holding onto it across a reload is
 * not, so read what you need from it and let it go.
 */
public class ConfigFile extends YamlConfiguration {

    private final Plugin plugin;

    /**
     * Held for the length of a reload, and by every read. Reads run concurrently with each
     * other; only the swap is exclusive, and it is rare.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile java.io.File file;
    private volatile String resourcePath;

    /**
     * The file re-read with the jar's copy set as its defaults, which is what makes a key that
     * is missing from the file resolve. Built on first use and dropped on reload.
     */
    private volatile FileConfiguration copy;

    public ConfigFile(final Plugin plugin, final String folderName, final String fileName) {
        this.plugin = plugin;
        try {
            load(folderName, fileName);
        } catch (IOException | InvalidConfigurationException exception) {
            // Named from the argument rather than the field: the failure may be the reason the
            // field was never assigned, and an NPE here would bury it.
            plugin.getLogger().log(Level.WARNING, "A problem has occurred, the file " + fileName
                    + " could not be created. Notify the developer", exception);
        }
    }

    public ConfigFile(final Plugin plugin, final String fileName) {
        this(plugin, null, fileName);
    }

    public void load(final String folderName, final String fileName) throws IOException, InvalidConfigurationException {
        String fileN = fileName.endsWith(".yml") ? fileName : fileName + ".yml";

        lock.writeLock().lock();
        try {
            if (folderName == null) {
                file = new java.io.File(plugin.getDataFolder(), fileN);
                resourcePath = fileN;
            } else {
                java.io.File folder = new java.io.File(plugin.getDataFolder(), folderName);
                if (!folder.exists()) folder.mkdirs();

                file = new java.io.File(folder, fileN);
                resourcePath = folderName.replace(java.io.File.separator, "/") + "/" + fileN;
            }

            if (!file.exists()) {
                InputStream resource = plugin.getResource(resourcePath);
                if (resource != null) {
                    plugin.saveResource(resourcePath, false);
                } else {
                    file.createNewFile();
                }
            }

            this.load(file);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── reading ─────────────────────────────────────────────────────────────────
    // Each accessor answers from the file when it has the key, and from the bundled copy when
    // it does not. Reading the file through `super` is deliberate: `super` carries no defaults,
    // so asking it for a key that only the jar has would return null rather than the default.

    public boolean isExist(String path) {
        lock.readLock().lock();
        try {
            if (super.contains(path)) return true;

            FileConfiguration defaults = defaults();
            if (defaults != null && defaults.contains(path)) return true;

            warnMissing(path);
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getString(final String path, final boolean color) {
        String value = getString(path);

        if (value == null) value = "path not found: " + path;
        return color ? org.bukkit.ChatColor.translateAlternateColorCodes('&', value) : value;
    }

    @Override
    public String getString(String key) {
        lock.readLock().lock();
        try {
            if (super.contains(key)) return super.getString(key);

            FileConfiguration defaults = defaults();
            if (defaults != null && defaults.contains(key)) return defaults.getString(key);

            warnMissing(key);
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int getInt(String key) {
        lock.readLock().lock();
        try {
            if (super.contains(key)) return super.getInt(key);

            FileConfiguration defaults = defaults();
            if (defaults != null && defaults.contains(key)) return defaults.getInt(key);

            warnMissing(key);
            return 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean getBoolean(String key) {
        lock.readLock().lock();
        try {
            if (super.contains(key)) return super.getBoolean(key);

            FileConfiguration defaults = defaults();
            if (defaults != null && defaults.contains(key)) return defaults.getBoolean(key);

            warnMissing(key);
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public double getDouble(String key) {
        lock.readLock().lock();
        try {
            if (super.contains(key)) return super.getDouble(key);

            FileConfiguration defaults = defaults();
            if (defaults != null && defaults.contains(key)) return defaults.getDouble(key);

            warnMissing(key);
            return 0.0;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long getLong(String key) {
        lock.readLock().lock();
        try {
            if (super.contains(key)) return super.getLong(key);

            FileConfiguration defaults = defaults();
            if (defaults != null && defaults.contains(key)) return defaults.getLong(key);

            warnMissing(key);
            return 0L;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull List<String> getStringList(String key) {
        lock.readLock().lock();
        try {
            if (super.contains(key)) return super.getStringList(key);

            FileConfiguration defaults = defaults();
            if (defaults != null && defaults.contains(key)) return defaults.getStringList(key);

            warnMissing(key);
            return List.of();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @Nullable List<?> getList(@NotNull String key) {
        lock.readLock().lock();
        try {
            if (super.contains(key)) return super.getList(key);

            FileConfiguration defaults = defaults();
            return defaults != null ? defaults.getList(key) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    // The variants that carry an explicit default are locked but not redirected through the
    // bundled copy: the caller already said what a missing key should produce, and quietly
    // preferring the jar's value over the one they passed would be the wrong kind of helpful.

    @Override
    public @Nullable String getString(@NotNull String path, @Nullable String def) {
        lock.readLock().lock();
        try {
            return super.getString(path, def);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int getInt(@NotNull String path, int def) {
        lock.readLock().lock();
        try {
            return super.getInt(path, def);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean getBoolean(@NotNull String path, boolean def) {
        lock.readLock().lock();
        try {
            return super.getBoolean(path, def);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public double getDouble(@NotNull String path, double def) {
        lock.readLock().lock();
        try {
            return super.getDouble(path, def);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long getLong(@NotNull String path, long def) {
        lock.readLock().lock();
        try {
            return super.getLong(path, def);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean contains(@NotNull String path) {
        lock.readLock().lock();
        try {
            return super.contains(path);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @Nullable ConfigurationSection getConfigurationSection(@NotNull String path) {
        lock.readLock().lock();
        try {
            ConfigurationSection section = super.getConfigurationSection(path);
            if (section != null) return section;

            FileConfiguration defaults = defaults();
            return defaults == null ? null : defaults.getConfigurationSection(path);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isConfigurationSection(@NotNull String path) {
        lock.readLock().lock();
        try {
            if (super.isConfigurationSection(path)) return true;

            FileConfiguration defaults = defaults();
            return defaults != null && defaults.isConfigurationSection(path);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull String saveToString() {
        lock.readLock().lock();
        try {
            return super.saveToString();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── file ────────────────────────────────────────────────────────────────────

    public boolean exists() {
        java.io.File current = file;
        return current != null && current.exists();
    }

    /** Exclusive: serialising the map reads it, and two saves would race on the same file. */
    public void save() {
        lock.writeLock().lock();
        try {
            this.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "There was an error while saving " + resourcePath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Re-reads the file. A syntax error leaves the previous contents in place — SnakeYAML
     * parses before anything is cleared, so the exception arrives before the swap begins — and
     * is logged rather than thrown, so callers cannot tell a broken file from a clean reload.
     * Validate first if that distinction matters.
     */
    public void reload() {
        lock.writeLock().lock();
        try {
            this.copy = null;
            this.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "There was an error while reloading " + resourcePath, exception);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete() {
        lock.writeLock().lock();
        try {
            java.io.File current = file;
            if (current != null && current.exists()) current.delete();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── internals ───────────────────────────────────────────────────────────────

    /**
     * The defaults-backed copy, built on demand.
     *
     * <p>Assigned only once it is complete. Publishing it before {@code setDefaults} had run
     * would let another thread find a non-null copy that resolves none of the keys it exists
     * for, and report every one of them as missing.
     */
    private FileConfiguration defaults() {
        FileConfiguration cached = copy;
        if (cached != null) return cached;

        java.io.File source = file;
        if (source == null) return null;

        FileConfiguration loaded = YamlConfiguration.loadConfiguration(source);

        InputStream stream = plugin.getResource(resourcePath);
        if (stream != null) {
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (IOException ignored) {
                // The jar's copy is a fallback for a fallback; the file itself still loaded.
            }
        }

        // Two threads racing here both build an equivalent copy, so the loser is wasted work
        // and never a half-built object handed to a reader.
        copy = loaded;
        return loaded;
    }

    private void warnMissing(String path) {
        plugin.getLogger().log(Level.WARNING, "Path not found: '" + path + "' in file: " + resourcePath);
    }
}
