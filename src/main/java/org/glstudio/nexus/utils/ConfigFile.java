package org.glstudio.nexus.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class ConfigFile extends YamlConfiguration {

    private final Plugin plugin;
    private java.io.File file;
    private String resourcePath;
    private FileConfiguration copy;

    public ConfigFile(final Plugin plugin, final String folderName, final String fileName) {
        this.plugin = plugin;
        try {
            load(folderName, fileName);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "A problem has occurred, the file " + file.getName() + " could not be created. Notify the developer", exception);
        }
    }

    public ConfigFile(final Plugin plugin, final String fileName) {
        this.plugin = plugin;
        try {
            load(null, fileName);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "A problem has occurred, the file " + file.getName() + " could not be created. Notify the developer", exception);
        }
    }

    public void load(final String folderName, final String fileName) throws IOException, InvalidConfigurationException {
        String fileN = fileName.endsWith(".yml") ? fileName : fileName + ".yml";

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
    }

    private void loadCopyResource() {
        if (copy != null) return;

        copy = YamlConfiguration.loadConfiguration(file);

        InputStream stream = plugin.getResource(resourcePath);
        if (stream == null) return;

        Reader reader = null;
        try {
            reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            copy.setDefaults(defaults);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
        }
    }

    public boolean isExist(String path) {
        if (super.contains(path)) return true;

        loadCopyResource();

        if (copy != null && copy.contains(path)) return true;

        plugin.getLogger().log(Level.WARNING, "Path not found: '" + path + "' in file: " + resourcePath);
        return false;
    }

    public String getString(final String path, final boolean color) {
        String value = isExist(path)
                ? super.getString(path)
                : (copy != null ? copy.getString(path) : null);

        if (value == null) value = "path not found: " + path;
        return color ? org.bukkit.ChatColor.translateAlternateColorCodes('&', value) : value;
    }

    @Override
    public String getString(String key) {
        if (isExist(key)) return super.getString(key);
        return copy != null ? copy.getString(key) : null;
    }

    @Override
    public int getInt(String key) {
        if (isExist(key)) return super.getInt(key);
        return copy != null ? copy.getInt(key) : 0;
    }

    @Override
    public boolean getBoolean(String key) {
        if (isExist(key)) return super.getBoolean(key);
        return copy != null && copy.getBoolean(key);
    }

    @Override
    public double getDouble(String key) {
        if (isExist(key)) return super.getDouble(key);
        return copy != null ? copy.getDouble(key) : 0.0;
    }

    @Override
    public long getLong(String key) {
        if (isExist(key)) return super.getLong(key);
        return copy != null ? copy.getLong(key) : 0L;
    }

    @Override
    public @NotNull List<String> getStringList(String key) {
        if (isExist(key)) return super.getStringList(key);
        return copy != null ? copy.getStringList(key) : List.of();
    }

    public boolean exists() {
        return file.exists();
    }

    public void save() {
        try {
            this.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "There was an error while saving " + resourcePath, e);
        }
    }

    public void reload() {
        try {
            this.copy = null;
            this.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "There was an error while reloading " + resourcePath, exception);
        }
    }

    public void delete() {
        if (file.exists()) file.delete();
    }
}