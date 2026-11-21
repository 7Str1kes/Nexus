package org.glstudio.nexus.action;

import org.glstudio.nexus.condition.Period;
import org.jetbrains.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * How many times each player used each {@code [limit]} of {@link ActionRunner}, and until when
 * those uses count. {@link YamlUsageLimits} keeps them in a file; a plugin may keep them elsewhere.
 * Main thread only.
 */
public interface UsageLimits {

    /**
     * The uses of one player.
     *
     * @param count   how many
     * @param expires when they start again, in epoch milliseconds
     */
    record Uses(int count, long expires) {
    }

    /**
     * Returns the uses of a player that still count.
     *
     * @param key    the limit
     * @param player the player
     * @param now    epoch milliseconds
     * @return the uses, or {@code null} if none count
     */
    @Nullable Uses current(String key, UUID player, long now);

    /**
     * Counts one more use.
     *
     * @param key    the limit
     * @param player the player
     * @param period how long uses last, used when this one starts them
     * @param now    the time, in the time zone periods are counted in
     */
    void use(String key, UUID player, Period period, ZonedDateTime now);

    /**
     * Forgets the uses of a player, of one limit or of all.
     *
     * @param key    the limit, or {@code null} for all
     * @param player the player
     * @return whether there were any
     */
    boolean reset(@Nullable String key, UUID player);

    /**
     * Saves what changed, in the background if the store can.
     */
    void flush();

    /**
     * Saves what changed now, when the plugin stops.
     */
    void flushNow();
}
