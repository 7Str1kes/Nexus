package org.glstudio.nexus.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Resolving player names and deciding who may see whom.
 */
public final class Players {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private Players() {
    }

    /**
     * Returns whether {@code name} could be a Java Edition player name.
     *
     * @param name the name
     * @return {@code true} for 1-16 letters, digits or underscores
     */
    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /**
     * Finds an online player by exact name, ignoring case.
     *
     * @param name the name
     * @return the player, or empty
     */
    public static Optional<Player> online(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(Bukkit.getPlayerExact(name));
    }

    /**
     * Resolves a name without blocking: an online player, then the server's name cache, then
     * {@code fallback} (typically a database lookup or {@link #bukkitLookup(Executor)}).
     *
     * @param name     the name typed by the sender
     * @param fallback looks up names that are neither online nor cached
     * @return the reference, or empty if the name is invalid or unknown
     */
    public static CompletableFuture<Optional<PlayerRef>> resolve(
            String name, Function<String, CompletableFuture<Optional<PlayerRef>>> fallback) {
        if (!isValidName(name)) return CompletableFuture.completedFuture(Optional.empty());
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new PlayerRef(online.getUniqueId(), online.getName())));
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            String cachedName = cached.getName() != null ? cached.getName() : name;
            return CompletableFuture.completedFuture(Optional.of(new PlayerRef(cached.getUniqueId(), cachedName)));
        }
        return fallback.apply(name);
    }

    /**
     * Returns a fallback for {@link #resolve} that asks Bukkit on {@code io}. It answers for anyone
     * who joined this server and, less reliably, for names Mojang resolves.
     *
     * @param io executor for the blocking lookup
     * @return the fallback
     */
    @SuppressWarnings("deprecation")
    public static Function<String, CompletableFuture<Optional<PlayerRef>>> bukkitLookup(Executor io) {
        return name -> CompletableFuture.supplyAsync(() -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            if (!offline.hasPlayedBefore() && offline.getName() == null) return Optional.empty();
            return Optional.of(new PlayerRef(offline.getUniqueId(), offline.getName() != null ? offline.getName() : name));
        }, io);
    }

    /**
     * Returns whether a vanish plugin marks {@code player} as vanished (the {@code vanished}
     * metadata used by SuperVanish, PremiumVanish, EssentialsX and others).
     *
     * @param player the player
     * @return {@code true} if vanished
     */
    public static boolean isVanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) return true;
        }
        return false;
    }

    /**
     * Returns whether {@code viewer} may see {@code target}: the console sees everyone, players see
     * themselves and whoever {@link Player#canSee(Player)} allows.
     *
     * @param viewer the sender
     * @param target the player
     * @return {@code true} if visible
     */
    public static boolean canSee(CommandSender viewer, Player target) {
        return !(viewer instanceof Player player) || player.equals(target) || player.canSee(target);
    }

    /**
     * Returns the names of the online players {@code viewer} may see, for tab completion.
     *
     * @param viewer the sender
     * @return the names
     */
    public static List<String> visibleNames(CommandSender viewer) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (canSee(viewer, online)) names.add(online.getName());
        }
        return names;
    }
}
