package org.glstudio.nexus.hooks.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base for a plugin's PlaceholderAPI expansion. Subclasses register handlers in their constructor
 * instead of writing a {@code switch} over the parameters.
 *
 * <p>Parameters are matched ignoring case. Exact keys are tried first, then prefixes (longest first,
 * for parameters that carry an argument, such as {@code top_1_name}), then {@link #fallback}.
 *
 * <p>Only load this class when PlaceholderAPI is installed; register it with
 * {@link PlaceholderHooks#registerIfPresent(Supplier)}.
 */
public abstract class NexusExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final String identifier;
    private final Map<String, Function<OfflinePlayer, String>> exact = new LinkedHashMap<>();
    private final List<Map.Entry<String, BiFunction<OfflinePlayer, String, String>>> prefixes = new ArrayList<>();

    /**
     * Creates the expansion.
     *
     * @param plugin     the owning plugin; author and version come from its {@code plugin.yml}
     * @param identifier the part before the first underscore, e.g. {@code glteleports}
     */
    protected NexusExpansion(JavaPlugin plugin, String identifier) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.identifier = Objects.requireNonNull(identifier, "identifier");
    }

    /**
     * Registers a placeholder that does not depend on the player.
     *
     * @param key   the parameter
     * @param value supplies the value, {@code null} for "not handled"
     */
    protected final void global(String key, Supplier<String> value) {
        exact.put(key.toLowerCase(Locale.ROOT), player -> value.get());
    }

    /**
     * Registers a placeholder for online players; offline players get {@code null}.
     *
     * @param key   the parameter
     * @param value computes the value
     */
    protected final void player(String key, Function<Player, String> value) {
        exact.put(key.toLowerCase(Locale.ROOT),
                offline -> offline instanceof Player online && online.isOnline() ? value.apply(online) : null);
    }

    /**
     * Registers a placeholder that also works for offline players.
     *
     * @param key   the parameter
     * @param value computes the value; the player may be {@code null}
     */
    protected final void offline(String key, Function<OfflinePlayer, String> value) {
        exact.put(key.toLowerCase(Locale.ROOT), value);
    }

    /**
     * Registers a placeholder whose parameter starts with {@code prefix}; the rest of the parameter,
     * in its original case, is passed to the handler.
     *
     * @param prefix the prefix, e.g. {@code top_}
     * @param value  receives the player (may be {@code null}) and the rest of the parameter
     */
    protected final void prefixed(String prefix, BiFunction<OfflinePlayer, String, String> value) {
        prefixes.add(Map.entry(prefix.toLowerCase(Locale.ROOT), value));
        prefixes.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
    }

    /**
     * Handles parameters no registered handler matched.
     *
     * @param player the player, may be {@code null}
     * @param params the parameter as typed
     * @return the value, or {@code null} for "not handled"
     */
    protected @Nullable String fallback(@Nullable OfflinePlayer player, String params) {
        return null;
    }

    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        Function<OfflinePlayer, String> handler = exact.get(key);
        if (handler != null) return handler.apply(player);
        for (Map.Entry<String, BiFunction<OfflinePlayer, String, String>> entry : prefixes) {
            if (key.startsWith(entry.getKey())) {
                return entry.getValue().apply(player, params.substring(entry.getKey().length()));
            }
        }
        return fallback(player, params);
    }
}
