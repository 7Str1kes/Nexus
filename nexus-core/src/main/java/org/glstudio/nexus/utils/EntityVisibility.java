package org.glstudio.nexus.utils;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Who sees an entity that is not shown to everyone. An entity visible by default is hidden from
 * the players who may not see it; one hidden by default is shown to the players who may. Only
 * changes are applied, so it can be updated for every player every few ticks. Main thread only.
 */
public final class EntityVisibility {

    private final boolean visibleByDefault;
    private final Set<UUID> toggled = new HashSet<>();

    /**
     * Creates the tracker.
     *
     * @param visibleByDefault whether the entity was spawned visible to everyone
     */
    public EntityVisibility(boolean visibleByDefault) {
        this.visibleByDefault = visibleByDefault;
    }

    /**
     * Returns whether the entity was spawned visible to everyone.
     *
     * @return {@code true} if it was
     */
    public boolean visibleByDefault() {
        return visibleByDefault;
    }

    /**
     * Brings one player's view in line with whether they may see the entity.
     *
     * @param player  the player
     * @param allowed whether they may see it
     * @param apply   shows ({@code true}) or hides ({@code false}) the entity for the player
     */
    public void update(Player player, boolean allowed, BiConsumer<Player, Boolean> apply) {
        UUID id = player.getUniqueId();
        boolean differs = visibleByDefault != allowed;
        if (differs && toggled.add(id)) {
            apply.accept(player, allowed);
        } else if (!differs && toggled.remove(id)) {
            apply.accept(player, allowed);
        }
    }

    /**
     * Returns whether every player sees the entity as it is by default.
     *
     * @return {@code true} if no player was shown or hidden it
     */
    public boolean untouched() {
        return toggled.isEmpty();
    }

    /**
     * Forgets a player who left.
     *
     * @param playerId the player
     */
    public void forget(UUID playerId) {
        toggled.remove(playerId);
    }
}
