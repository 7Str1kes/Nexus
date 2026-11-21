package org.glstudio.nexus.utils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cooldowns kept in memory, per player and per key, such as {@code "preview"} or a hologram's id.
 * Each entry is the time it ends; a player who leaves should be {@link #forget(UUID) forgotten}.
 * Safe to use from any thread.
 */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> until = new ConcurrentHashMap<>();
    private final long defaultMillis;

    /**
     * Creates cooldowns whose length is given each time.
     */
    public Cooldowns() {
        this(Duration.ZERO);
    }

    /**
     * Creates cooldowns that all last the same, such as a throttle on repeated messages.
     *
     * @param length how long each lasts, used by {@link #tryUse(UUID, String)}
     */
    public Cooldowns(Duration length) {
        this.defaultMillis = length.toMillis();
    }

    /**
     * Starts a cooldown of the length these cooldowns were made with, unless one is running.
     *
     * @param player the player
     * @param key    what they do
     * @return {@code true} if none was running, and one starts now
     */
    public boolean tryUse(UUID player, String key) {
        return tryUse(player, key, defaultMillis);
    }

    /**
     * Starts a cooldown unless one is running: the usual "may they do it now?" check.
     *
     * @param player the player
     * @param key    what they do
     * @param length how long they then wait
     * @return {@code true} if none was running, and one starts now
     */
    public boolean tryUse(UUID player, String key, Duration length) {
        return tryUse(player, key, length.toMillis());
    }

    /**
     * Starts a cooldown unless one is running.
     *
     * @param player the player
     * @param key    what they do
     * @param millis how long they then wait
     * @return {@code true} if none was running, and one starts now
     */
    public boolean tryUse(UUID player, String key, long millis) {
        long now = System.currentTimeMillis();
        Map<String, Long> own = until.computeIfAbsent(player, id -> new ConcurrentHashMap<>());
        Long end = own.get(key);
        if (end != null && now < end) return false;
        own.put(key, now + millis);
        return true;
    }

    /**
     * Starts or restarts a cooldown, whether one was running or not.
     *
     * @param player the player
     * @param key    what they did
     * @param length how long they wait
     */
    public void start(UUID player, String key, Duration length) {
        until.computeIfAbsent(player, id -> new ConcurrentHashMap<>()).put(key, System.currentTimeMillis() + length.toMillis());
    }

    /**
     * Returns how long a cooldown has left.
     *
     * @param player the player
     * @param key    what they do
     * @return milliseconds, {@code 0} when none is running
     */
    public long remainingMillis(UUID player, String key) {
        Map<String, Long> own = until.get(player);
        Long end = own == null ? null : own.get(key);
        return end == null ? 0 : Math.max(0, end - System.currentTimeMillis());
    }

    /**
     * Returns whether a cooldown is running.
     *
     * @param player the player
     * @param key    what they do
     * @return {@code true} while they must wait
     */
    public boolean isCooling(UUID player, String key) {
        return remainingMillis(player, key) > 0;
    }

    /**
     * Ends a cooldown now.
     *
     * @param player the player
     * @param key    what they do
     */
    public void clear(UUID player, String key) {
        Map<String, Long> own = until.get(player);
        if (own != null) own.remove(key);
    }

    /**
     * Ends the cooldowns of every player for one key, such as a removed hologram's.
     *
     * @param key the key
     */
    public void clearKey(String key) {
        until.values().forEach(own -> own.remove(key));
    }

    /**
     * Forgets a player, when they leave.
     *
     * @param player the player
     */
    public void forget(UUID player) {
        until.remove(player);
    }

    /**
     * Forgets everyone.
     */
    public void clear() {
        until.clear();
    }
}
