package org.glstudio.nexus.hooks.worldguard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Questions to WorldGuard, safe to ask whether or not it is installed: WorldGuard's classes are only
 * touched once it is known to be running. Flags are named as in WorldGuard's commands, such as
 * {@code pvp}, {@code use}, {@code chest-access} or a flag another plugin registered.
 */
public final class WorldGuardHook {

    private static final String PLUGIN = "WorldGuard";

    private WorldGuardHook() {
    }

    /**
     * Returns whether WorldGuard is running.
     *
     * @return {@code true} if it is enabled
     */
    public static boolean isPresent() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled(PLUGIN);
        } catch (Throwable noServer) {
            return false;
        }
    }

    /**
     * Returns the ids of the regions at a location, lower case.
     *
     * @param location the location
     * @return the ids, empty without WorldGuard
     */
    public static Set<String> regionsAt(Location location) {
        return isPresent() ? WorldGuardBridge.regionsAt(location) : Set.of();
    }

    /**
     * Returns whether a player stands in one of some regions.
     *
     * @param player  the player
     * @param regions region ids, each alone or as {@code world:region}, any case
     * @return {@code true} if they stand in one; {@code false} without WorldGuard
     */
    public static boolean inAny(Player player, Collection<String> regions) {
        if (!isPresent() || regions.isEmpty()) return false;
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        for (String id : WorldGuardBridge.regionsAt(player.getLocation())) {
            for (String wanted : regions) {
                String lower = wanted.toLowerCase(Locale.ROOT);
                if (lower.equals(id) || lower.equals(world + ":" + id)) return true;
            }
        }
        return false;
    }

    /**
     * Returns the state a state flag has at a location, for anyone.
     *
     * @param location the location
     * @param flag     the flag's name
     * @return {@code true} for allow, {@code false} for deny, empty when unset, unknown or without
     *         WorldGuard
     */
    public static Optional<Boolean> state(Location location, String flag) {
        return isPresent() ? WorldGuardBridge.state(location, null, flag) : Optional.empty();
    }

    /**
     * Returns the state a state flag has at a location for a player, counting their membership.
     *
     * @param player   the player
     * @param location the location
     * @param flag     the flag's name
     * @return {@code true} for allow, {@code false} for deny, empty when unset, unknown or without
     *         WorldGuard
     */
    public static Optional<Boolean> state(Player player, Location location, String flag) {
        return isPresent() ? WorldGuardBridge.state(location, player, flag) : Optional.empty();
    }

    /**
     * Returns whether a state flag is denied at a location, such as {@code pvp} in a safe zone.
     *
     * @param location the location
     * @param flag     the flag's name
     * @return {@code true} only when WorldGuard denies it there
     */
    public static boolean denied(Location location, String flag) {
        return state(location, flag).map(allowed -> !allowed).orElse(false);
    }

    /**
     * Returns whether a player may build at a location, the way WorldGuard's own protection decides,
     * optionally with flags that also allow it, such as {@code use} or {@code chest-access}.
     *
     * @param player   the player
     * @param location the location
     * @param flags    flags that allow it too
     * @return {@code true} if allowed, and always without WorldGuard
     */
    public static boolean canBuild(Player player, Location location, String... flags) {
        return !isPresent() || WorldGuardBridge.canBuild(player, location, flags);
    }
}
