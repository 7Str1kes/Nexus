package org.glstudio.nexus.action;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.glstudio.nexus.condition.Period;
import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.nexus.utils.YamlFiles;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * {@link UsageLimits} kept in a YAML file, such as {@code data/limits.yml}, so a daily reward stays
 * claimed after a restart. Uses that ran out of time are dropped when read. Written in
 * the background after changes, and at once when the plugin stops. Main thread only.
 */
public class YamlUsageLimits implements UsageLimits {

    private final File file;
    private final Executor writer;
    private final Map<String, Map<UUID, Uses>> uses = new HashMap<>();
    private boolean dirty;

    /**
     * Creates the store; {@link #load()} reads the file.
     *
     * @param file   the file
     * @param writer where it is written, in order
     */
    public YamlUsageLimits(File file, Executor writer) {
        this.file = file;
        this.writer = writer;
    }

    /**
     * Reads the file, dropping uses that ran out.
     */
    public void load() {
        uses.clear();
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection players = yaml.getConfigurationSection(key);
            if (players == null) continue;
            for (String id : players.getKeys(false)) {
                String[] parts = players.getString(id, "").split(":");
                try {
                    Uses read = new Uses(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
                    if (read.expires() > now) uses.computeIfAbsent(key, k -> new HashMap<>()).put(UUID.fromString(id), read);
                } catch (RuntimeException e) {
                    LoggerUtils.logWarn(file.getName() + ": " + key + "." + id + " could not be read; it is dropped.");
                }
            }
        }
    }

    /**
     * Returns the uses of a player that still count.
     *
     * @param key    the limit
     * @param player the player
     * @param now    epoch milliseconds
     * @return the uses, or {@code null} if none count
     */
    @Override
    public Uses current(String key, UUID player, long now) {
        Map<UUID, Uses> players = uses.get(key);
        Uses found = players == null ? null : players.get(player);
        return found == null || found.expires() <= now ? null : found;
    }

    /**
     * Counts one more use.
     *
     * @param key    the limit
     * @param player the player
     * @param period how long uses last, used when this one starts them
     * @param now    the time, in the time zone periods are counted in
     */
    @Override
    public void use(String key, UUID player, Period period, ZonedDateTime now) {
        Uses found = current(key, player, now.toInstant().toEpochMilli());
        Uses next = found == null ? new Uses(1, period.end(now)) : new Uses(found.count() + 1, found.expires());
        uses.computeIfAbsent(key, k -> new HashMap<>()).put(player, next);
        dirty = true;
    }

    /**
     * Forgets the uses of a player, of one limit or of all.
     *
     * @param key    the limit, or {@code null} for all
     * @param player the player
     * @return whether there were any
     */
    @Override
    public boolean reset(@Nullable String key, UUID player) {
        boolean removed = false;
        for (Map.Entry<String, Map<UUID, Uses>> entry : uses.entrySet()) {
            if (key == null || entry.getKey().equals(key)) removed |= entry.getValue().remove(player) != null;
        }
        dirty |= removed;
        return removed;
    }

    /**
     * Writes the file in the background if something changed.
     */
    @Override
    public void flush() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = snapshot();
        writer.execute(() -> write(yaml));
    }

    /**
     * Writes the file now, when the plugin stops.
     */
    @Override
    public void flushNow() {
        if (!dirty) return;
        dirty = false;
        write(snapshot());
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        uses.forEach((key, players) -> players.forEach((player, read) -> {
            if (read.expires() > now) yaml.set(key + "." + player, read.count() + ":" + read.expires());
        }));
        return yaml;
    }

    private void write(YamlConfiguration yaml) {
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            Diagnostics.logFailure("YamlUsageLimits#write", e);
        }
    }
}
