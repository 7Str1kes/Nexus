package org.glstudio.nexus.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * A YAML file in the plugin's data folder that falls back to the copy bundled in the jar, so a key
 * added in a new version of a plugin still resolves against a file the server generated before it
 * existed.
 *
 * <p><b>Two lookup semantics.</b> The getters without a default ({@link #getString(String)},
 * {@link #getInt(String)}...) answer from the file, then from the jar, and warn when neither has the
 * key. The Bukkit getters with an explicit default ({@link #getInt(String, int)}...) only look at the
 * file: the caller already said what a missing key means. {@link #resolved()} gives a third view
 * that looks at the file, then the jar, then the caller's default, which is what most settings want.
 *
 * <p><b>Threading.</b> Reloading replaces this configuration's contents in place while other threads
 * may be reading from it. A read-write lock keeps the accessors declared here and {@link #reload()}
 * apart, so a reader sees one version of the file or the other and never the gap between them. A
 * {@link ConfigurationSection} returned by {@link #getConfigurationSection} is a live view: read what
 * you need from it and let it go.
 */
public class ConfigFile extends YamlConfiguration {

    /** Key holding the schema version of a configuration file. */
    public static final String VERSION_KEY = "config-version";

    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^" + VERSION_KEY + ":.*$");

    private final Plugin plugin;

    /** Held for the length of a reload, and by every read. */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ResolvedConfig resolved = new ResolvedConfig(this);

    private volatile File file;
    private volatile String resourcePath;

    /** The file re-read with the jar's copy set as its defaults. Built on first use, dropped on reload. */
    private volatile FileConfiguration copy;
    /** The jar's copy alone. It never changes while the plugin runs, so it is never dropped. */
    private volatile FileConfiguration bundled;
    private volatile boolean bundledLoaded;

    /**
     * Loads {@code folderName/fileName} from the data folder, copying it from the jar first if it
     * does not exist. Problems are logged rather than thrown.
     *
     * @param plugin     the owning plugin
     * @param folderName sub-folder of the data folder, or {@code null} for the data folder itself
     * @param fileName   the file name; {@code .yml} is appended if missing
     */
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

    /**
     * Loads {@code fileName} from the data folder, copying it from the jar first if it does not exist.
     *
     * @param plugin   the owning plugin
     * @param fileName the file name; {@code .yml} is appended if missing
     */
    public ConfigFile(final Plugin plugin, final String fileName) {
        this(plugin, null, fileName);
    }

    /**
     * (Re)binds this object to a file and loads it.
     *
     * @param folderName sub-folder of the data folder, or {@code null}
     * @param fileName   the file name; {@code .yml} is appended if missing
     * @throws IOException                   if the file cannot be created or read
     * @throws InvalidConfigurationException if it is not valid YAML
     */
    public void load(final String folderName, final String fileName) throws IOException, InvalidConfigurationException {
        String fileN = fileName.endsWith(".yml") ? fileName : fileName + ".yml";

        lock.writeLock().lock();
        try {
            if (folderName == null) {
                file = new File(plugin.getDataFolder(), fileN);
                resourcePath = fileN;
            } else {
                File folder = new File(plugin.getDataFolder(), folderName);
                if (!folder.exists()) folder.mkdirs();

                file = new File(folder, fileN);
                resourcePath = folderName.replace(File.separator, "/") + "/" + fileN;
            }

            if (!file.exists()) {
                InputStream resource = plugin.getResource(resourcePath);
                if (resource != null) {
                    resource.close();
                    plugin.saveResource(resourcePath, false);
                } else {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
            }

            this.copy = null;
            this.load(file);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── reading: file, then jar, warning when missing ───────────────────────────
    // Reading the file through `super` is deliberate: `super` carries no defaults, so asking it for
    // a key that only the jar has returns null rather than the default.

    /**
     * Returns whether {@code path} exists in the file or in the jar, warning if in neither.
     *
     * @param path the key
     * @return {@code true} if found
     */
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

    /**
     * Returns a string from the file or the jar, optionally translating {@code &} colour codes.
     *
     * @param path  the key
     * @param color {@code true} to translate colour codes
     * @return the value, or {@code "path not found: <path>"}
     */
    public String getString(final String path, final boolean color) {
        String value = getString(path);

        if (value == null) value = "path not found: " + path;
        return color ? org.bukkit.ChatColor.translateAlternateColorCodes('&', value) : value;
    }

    @Override
    public String getString(@NotNull String key) {
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
    public int getInt(@NotNull String key) {
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
    public boolean getBoolean(@NotNull String key) {
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
    public double getDouble(@NotNull String key) {
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
    public long getLong(@NotNull String key) {
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
    public @NotNull List<String> getStringList(@NotNull String key) {
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

    // ─── reading: file only, caller's default ────────────────────────────────────
    // Locked but not redirected through the bundled copy: the caller already said what a missing
    // key should produce. Use resolved() to also consult the jar.

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

            // Not defaults.getConfigurationSection(path): Bukkit's default-value machinery sees a
            // section-shaped default and returns a new, empty section instead of its contents.
            FileConfiguration jar = bundled();
            return jar == null ? null : jar.getConfigurationSection(path);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isConfigurationSection(@NotNull String path) {
        lock.readLock().lock();
        try {
            if (super.isConfigurationSection(path)) return true;

            FileConfiguration jar = bundled();
            return jar != null && jar.isConfigurationSection(path);
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

    // ─── reading: file, then jar, then caller's default ─────────────────────────

    /**
     * Returns the view that resolves every key from the file, then the jar copy, then the caller's
     * default.
     *
     * @return the resolved view, the same instance on every call
     */
    public ResolvedConfig resolved() {
        return resolved;
    }

    /**
     * Returns the raw value of {@code path} from the file, or from the jar if the file lacks it.
     *
     * @param path the key
     * @return the value, or {@code null} if neither has it
     */
    @Nullable
    Object resolvedValue(String path) {
        lock.readLock().lock();
        try {
            if (super.contains(path)) return super.get(path);
            FileConfiguration jar = bundled();
            return jar != null && jar.contains(path) ? jar.get(path) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns where {@code path} is defined.
     *
     * @param path the key
     * @return {@code 1} for the file, {@code 2} for the jar only, {@code 0} for neither
     */
    int resolvedSource(String path) {
        lock.readLock().lock();
        try {
            if (super.contains(path)) return 1;
            FileConfiguration jar = bundled();
            return jar != null && jar.contains(path) ? 2 : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the copy of this file bundled in the jar, without the server's changes.
     *
     * @return the jar copy, or {@code null} if the jar has no such resource
     */
    public @Nullable FileConfiguration bundled() {
        if (bundledLoaded) return bundled;
        synchronized (this) {
            if (bundledLoaded) return bundled;
            String path = resourcePath;
            InputStream stream = path == null ? null : plugin.getResource(path);
            if (stream != null) {
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    bundled = YamlConfiguration.loadConfiguration(reader);
                } catch (IOException ignored) {
                    bundled = null;
                }
            }
            bundledLoaded = true;
            return bundled;
        }
    }

    // ─── validation ──────────────────────────────────────────────────────────────

    /**
     * Returns the {@value #VERSION_KEY} written in the server's file.
     *
     * @return the version, {@code 0} if the file has none
     */
    public int version() {
        return getInt(VERSION_KEY, 0);
    }

    /**
     * Brings an older file up to {@code expected}. Only the version line changes, in the text itself,
     * so the server's values, comments and layout stay exactly as they were. Keys this version adds
     * are not written: they keep falling back to the jar copy until the owner sets them.
     *
     * @param expected the version this build of the plugin writes
     * @param warn     receives the problem if the file could not be updated
     * @return {@code true} if the file was already up to date
     */
    public boolean checkVersion(int expected, Consumer<String> warn) {
        int found = version();
        if (found >= expected) return true;
        lock.writeLock().lock();
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String line = VERSION_KEY + ": " + expected;
            String updated = VERSION_LINE.matcher(text).find()
                    ? VERSION_LINE.matcher(text).replaceFirst(line)
                    : line + System.lineSeparator() + System.lineSeparator() + text;
            YamlFiles.writeAtomically(file.toPath(), updated);
            set(VERSION_KEY, expected);
            plugin.getLogger().info(getFileName() + " updated from version " + found + " to " + expected
                    + "; your values are kept.");
        } catch (IOException e) {
            warn.accept(getFileName() + " is version " + found + " and could not be updated to " + expected
                    + ": " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
        return false;
    }

    /**
     * Returns the keys the server's file has and the jar copy does not, usually typos.
     *
     * @param depth           how many levels to inspect ({@code 1} for top-level keys only)
     * @param ignoredPrefixes sections whose children are free-form (e.g. per-group limits)
     * @return the unknown keys, in file order; empty if the jar has no copy
     */
    public Set<String> unknownKeys(int depth, Collection<String> ignoredPrefixes) {
        FileConfiguration jar = bundled();
        Set<String> unknown = new LinkedHashSet<>();
        if (jar == null) return unknown;

        lock.readLock().lock();
        try {
            for (String key : super.getKeys(true)) {
                if (levels(key) > depth || VERSION_KEY.equals(key) || isIgnored(key, ignoredPrefixes)) continue;
                if (!jar.contains(key)) unknown.add(key);
            }
        } finally {
            lock.readLock().unlock();
        }
        return unknown;
    }

    /**
     * Returns the keys the jar copy has and the server's file does not.
     *
     * @param depth how many levels to inspect
     * @return the missing keys, in jar order
     */
    public Set<String> missingKeys(int depth) {
        FileConfiguration jar = bundled();
        Set<String> missing = new LinkedHashSet<>();
        if (jar == null) return missing;

        lock.readLock().lock();
        try {
            for (String key : jar.getKeys(true)) {
                if (levels(key) > depth) continue;
                if (!super.contains(key)) missing.add(key);
            }
        } finally {
            lock.readLock().unlock();
        }
        return missing;
    }

    private static int levels(String key) {
        int levels = 1;
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) == '.') levels++;
        }
        return levels;
    }

    private static boolean isIgnored(String key, Collection<String> prefixes) {
        if (prefixes == null) return false;
        for (String prefix : prefixes) {
            if (key.equals(prefix) || key.startsWith(prefix + ".")) return true;
        }
        return false;
    }

    // ─── file ────────────────────────────────────────────────────────────────────

    /**
     * Returns whether the file exists on disk.
     *
     * @return {@code true} if it exists
     */
    public boolean exists() {
        File current = file;
        return current != null && current.exists();
    }

    /**
     * Returns the file on disk.
     *
     * @return the file, or {@code null} if loading failed before it was resolved
     */
    public @Nullable File getFile() {
        return file;
    }

    /**
     * Returns the path of the file relative to the data folder, e.g. {@code menus/homes.yml}.
     *
     * @return the relative path
     */
    public String getFileName() {
        return resourcePath;
    }

    /**
     * Writes the contents to disk. Exclusive: serialising reads the map, and two saves would race on
     * the same file. Failures are logged.
     */
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
     * Writes the contents to a temporary file next to the real one and moves it into place, so a
     * crash mid-write never leaves a truncated file. Failures are logged.
     *
     * @return {@code true} if the file was written
     */
    public boolean saveAtomically() {
        lock.writeLock().lock();
        try {
            YamlFiles.writeAtomically(file.toPath(), super.saveToString());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "There was an error while saving " + resourcePath, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Re-reads the file. A syntax error leaves the previous contents in place (SnakeYAML parses
     * before anything is cleared) and is logged rather than thrown. Use {@link #validate()} first if
     * the caller needs to know.
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

    /**
     * Parses the file on disk without applying it.
     *
     * @return {@code null} if it parses, otherwise the parser's error message
     */
    public @Nullable String validate() {
        File current = file;
        if (current == null || !current.exists()) return null;
        try {
            new YamlConfiguration().load(current);
            return null;
        } catch (IOException | InvalidConfigurationException e) {
            String message = e.getMessage();
            return message == null ? e.getClass().getSimpleName() : message;
        }
    }

    /**
     * Deletes the file from disk. The in-memory contents stay.
     */
    public void delete() {
        lock.writeLock().lock();
        try {
            File current = file;
            if (current != null && current.exists()) current.delete();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── internals ───────────────────────────────────────────────────────────────

    /**
     * The defaults-backed copy, built on demand. Assigned only once complete, so another thread
     * never sees a copy whose defaults are not set yet.
     */
    private FileConfiguration defaults() {
        FileConfiguration cached = copy;
        if (cached != null) return cached;

        File source = file;
        if (source == null) return null;

        FileConfiguration loaded = YamlConfiguration.loadConfiguration(source);
        FileConfiguration jar = bundled();
        if (jar != null) loaded.setDefaults(jar);

        copy = loaded;
        return loaded;
    }

    private void warnMissing(String path) {
        plugin.getLogger().log(Level.WARNING, "Path not found: '" + path + "' in file: " + resourcePath);
    }
}
