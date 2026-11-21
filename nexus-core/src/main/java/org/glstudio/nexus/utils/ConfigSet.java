package org.glstudio.nexus.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * The config files of a plugin, as its {@code ConfigManager} keeps them: each file is tracked once
 * and they are reloaded, version-checked and validated together.
 */
public abstract class ConfigSet {

    private final List<ConfigFile> files = new ArrayList<>();

    /**
     * Creates an empty set; the subclass tracks its files in its constructor.
     */
    protected ConfigSet() {
    }

    /**
     * Tracks a file.
     *
     * @param file the file
     * @return the same file, to keep in a field
     */
    protected ConfigFile track(ConfigFile file) {
        files.add(file);
        return file;
    }

    /**
     * Returns every tracked file.
     *
     * @return the files, in the order they were tracked
     */
    public List<ConfigFile> all() {
        return List.copyOf(files);
    }

    /**
     * Reloads every tracked file.
     */
    public void reload() {
        all().forEach(ConfigFile::reload);
    }
}
