package org.glstudio.nexus.concurrent;

import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.Tasks;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

/**
 * The data of the players who are online: loaded when they join, kept while they play, let go of
 * when they leave. A load that ends after its player left is dropped, so a player who leaves before
 * their data arrives is never left in the cache.
 *
 * @param <T> the data of one player
 */
public final class PlayerCache<T> {

    private final Map<UUID, T> online = new ConcurrentHashMap<>();
    private final Tasks tasks;

    /**
     * Creates the cache.
     *
     * @param tasks the plugin's tasks, which bring loads back to the main thread
     */
    public PlayerCache(Tasks tasks) {
        this.tasks = tasks;
    }

    /**
     * Keeps a player's data once it is loaded, on the main thread, if they are still online.
     *
     * @param player  the player
     * @param loading their data, loading
     * @return completes on the main thread with the data kept, or empty if they left
     */
    public CompletableFuture<Optional<T>> load(Player player, CompletableFuture<T> loading) {
        return load(player.getUniqueId(), loading, player::isOnline);
    }

    /**
     * Keeps a player's data once it is loaded, on the main thread, if it is still wanted.
     *
     * @param playerId the player
     * @param loading  their data, loading
     * @param wanted   asked once it is loaded, on the main thread
     * @return completes on the main thread with the data kept, or empty if it was not wanted
     */
    public CompletableFuture<Optional<T>> load(UUID playerId, CompletableFuture<T> loading, BooleanSupplier wanted) {
        return tasks.hop(loading).thenApply(value -> {
            if (value == null || !wanted.getAsBoolean()) return Optional.empty();
            online.put(playerId, value);
            return Optional.of(value);
        });
    }

    /**
     * Keeps a player's data that is already known.
     *
     * @param playerId the player
     * @param value    their data
     */
    public void put(UUID playerId, T value) {
        online.put(playerId, value);
    }

    /**
     * Replaces a player's data with what a change makes of it, if it is loaded.
     *
     * @param playerId the player
     * @param change   makes the new data from the old
     */
    public void update(UUID playerId, UnaryOperator<T> change) {
        online.computeIfPresent(playerId, (id, value) -> change.apply(value));
    }

    /**
     * Returns a player's data.
     *
     * @param playerId the player
     * @return the data, or empty while it is not loaded
     */
    public Optional<T> get(UUID playerId) {
        return Optional.ofNullable(online.get(playerId));
    }

    /**
     * Returns a player's data, or something else while it is not loaded.
     *
     * @param playerId the player
     * @param fallback returned while it is not loaded
     * @return the data, or the fallback
     */
    public T getOrDefault(UUID playerId, T fallback) {
        return online.getOrDefault(playerId, fallback);
    }

    /**
     * Returns whether a player's data is loaded.
     *
     * @param playerId the player
     * @return {@code true} if it is
     */
    public boolean isLoaded(UUID playerId) {
        return online.containsKey(playerId);
    }

    /**
     * Lets go of a player's data, when they leave.
     *
     * @param playerId the player
     * @return the data, to save it, or {@code null} if it was not loaded
     */
    public @Nullable T unload(UUID playerId) {
        return online.remove(playerId);
    }

    /**
     * Returns the players whose data is loaded.
     *
     * @return their ids
     */
    public Set<UUID> ids() {
        return Set.copyOf(online.keySet());
    }

    /**
     * Returns the data loaded.
     *
     * @return a copy
     */
    public Collection<T> values() {
        return List.copyOf(online.values());
    }

    /**
     * Lets go of everyone's data.
     */
    public void clear() {
        online.clear();
    }
}
