package org.glstudio.nexus.modules.lang;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.modules.menu.placeholder.Placeholders;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.nexus.utils.TextFormat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The plugin's {@code language.yml}: every player-facing text goes through here, so all wording
 * stays configurable.
 *
 * <p>Placeholders are given as {@code token, value} pairs and substituted in a single pass (see
 * {@link Placeholders#apply(String, Object...)}). The prefix is prepended to single-line messages
 * sent with {@link #send}; list messages and components are rendered without it.
 *
 * <p>Keys missing from the server's file resolve against the copy in the jar. A key missing from
 * both renders as nothing.
 */
public class LanguageFile {

    private final ConfigFile file;
    private final TextFormat format;
    private String prefixPath = "prefix";
    private boolean papi;

    /**
     * Wraps {@code file}.
     *
     * @param file   the language file
     * @param format how texts are written in it
     */
    public LanguageFile(ConfigFile file, TextFormat format) {
        this.file = Objects.requireNonNull(file, "file");
        this.format = Objects.requireNonNull(format, "format");
    }

    /**
     * Sets the key of the prefix, {@code "prefix"} by default.
     *
     * @param path the key, or {@code null} for no prefix
     * @return this object
     */
    public LanguageFile prefixPath(@Nullable String path) {
        this.prefixPath = path;
        return this;
    }

    /**
     * Resolves PlaceholderAPI placeholders against the recipient when PlaceholderAPI is installed.
     *
     * @param enabled {@code true} to resolve them
     * @return this object
     */
    public LanguageFile papi(boolean enabled) {
        this.papi = enabled;
        return this;
    }

    /**
     * Returns the underlying file.
     *
     * @return the file
     */
    public ConfigFile getFile() {
        return file;
    }

    /**
     * Returns the text format of the file.
     *
     * @return the format
     */
    public TextFormat getFormat() {
        return format;
    }

    /**
     * Returns the template of {@code key} without prefix or substitutions.
     *
     * @param key the key
     * @return the template, or {@code null} if neither the file nor the jar has it
     */
    public @Nullable String raw(String key) {
        Object value = file.resolved().get(key);
        return value == null || value instanceof List<?> ? null : value.toString();
    }

    /**
     * Returns the template of {@code key}, or {@code fallback} if it is missing.
     *
     * @param key      the key
     * @param fallback used when missing
     * @return the template
     */
    public String raw(String key, String fallback) {
        String value = raw(key);
        return value == null ? fallback : value;
    }

    /**
     * Returns whether the file or the jar defines {@code key}.
     *
     * @param key the key
     * @return {@code true} if defined
     */
    public boolean has(String key) {
        return file.resolved().isSet(key);
    }

    /**
     * Returns the lines of {@code key}: the list itself, or a one-element list for a single string.
     *
     * @param key the key
     * @return the lines, empty if missing
     */
    public List<String> rawList(String key) {
        Object value = file.resolved().get(key);
        if (value instanceof List<?>) return file.resolved().getStringList(key);
        return value == null ? new ArrayList<>() : new ArrayList<>(List.of(value.toString()));
    }

    /**
     * Returns the prefix template.
     *
     * @return the prefix, empty if none
     */
    public String prefix() {
        return prefixPath == null ? "" : raw(prefixPath, "");
    }

    /**
     * Substitutes {@code pairs} (and PlaceholderAPI, if enabled) into {@code template}.
     *
     * @param viewer   the player PlaceholderAPI resolves against, may be {@code null}
     * @param template the text
     * @param pairs    alternating tokens and values
     * @return the resolved text, still in the file's format
     */
    public String apply(@Nullable OfflinePlayer viewer, String template, Object... pairs) {
        // PlaceholderAPI reads the template before the values go in, so a value typed by a player,
        // such as a condition or a command, is shown as written instead of being filled in.
        String base = papi ? Placeholders.papi(viewer, template) : template;
        return Placeholders.apply(base, pairs);
    }

    /**
     * Renders already-known text in the file's format, e.g. a lore line read from a menu file.
     *
     * @param viewer the player PlaceholderAPI resolves against, may be {@code null}
     * @param text   the text
     * @param pairs  alternating tokens and values
     * @return the component
     */
    public Component render(@Nullable OfflinePlayer viewer, String text, Object... pairs) {
        return text == null ? Component.empty() : CC.component(apply(viewer, text, pairs), format);
    }

    /**
     * Renders {@code key} without prefix.
     *
     * @param key   the key
     * @param pairs alternating tokens and values
     * @return the component, empty if the key is missing
     */
    public Component component(String key, Object... pairs) {
        String template = raw(key);
        return template == null ? Component.empty() : render(null, template, pairs);
    }

    /**
     * Renders every line of {@code key} without prefix.
     *
     * @param viewer the player PlaceholderAPI resolves against, may be {@code null}
     * @param key    the key
     * @param pairs  alternating tokens and values
     * @return the components, empty if the key is missing
     */
    public List<Component> components(@Nullable OfflinePlayer viewer, String key, Object... pairs) {
        List<Component> result = new ArrayList<>();
        for (String line : rawList(key)) result.add(render(viewer, line, pairs));
        return result;
    }

    /**
     * Resolves {@code key} with prefix to a legacy {@code §}-coloured string, for APIs that only take
     * strings.
     *
     * @param viewer the player PlaceholderAPI resolves against, may be {@code null}
     * @param key    the key
     * @param pairs  alternating tokens and values
     * @return the text, empty if the key is missing or blank
     */
    public String legacy(@Nullable OfflinePlayer viewer, String key, Object... pairs) {
        String template = raw(key);
        if (template == null || template.isEmpty()) return "";
        return CC.legacy(render(viewer, prefix() + template, pairs));
    }

    /**
     * Sends {@code key} with prefix. Missing or blank messages are not sent, which lets an
     * administrator silence a message by emptying it.
     *
     * @param to    the recipient, ignored if {@code null}
     * @param key   the key
     * @param pairs alternating tokens and values
     */
    public void send(@Nullable CommandSender to, String key, Object... pairs) {
        if (to == null) return;
        String template = raw(key);
        if (template == null || template.isBlank()) return;
        to.sendMessage(render(viewerOf(to), prefix() + template, pairs));
    }

    /**
     * Sends {@code key} to {@code player} only if still online.
     *
     * @param player the recipient
     * @param key    the key
     * @param pairs  alternating tokens and values
     */
    public void sendIfOnline(@Nullable Player player, String key, Object... pairs) {
        if (player != null && player.isOnline()) send(player, key, pairs);
    }

    /**
     * Sends every line of {@code key} without prefix; a single-string key is sent with prefix.
     *
     * @param to    the recipient, ignored if {@code null}
     * @param key   the key
     * @param pairs alternating tokens and values
     */
    public void sendLines(@Nullable CommandSender to, String key, Object... pairs) {
        if (to == null) return;
        if (!(file.resolved().get(key) instanceof List<?>)) {
            send(to, key, pairs);
            return;
        }
        OfflinePlayer viewer = viewerOf(to);
        for (String line : rawList(key)) to.sendMessage(render(viewer, line, pairs));
    }

    /**
     * Shows {@code key} above the hotbar, without prefix.
     *
     * @param player the recipient
     * @param key    the key
     * @param pairs  alternating tokens and values
     */
    public void actionBar(@Nullable Player player, String key, Object... pairs) {
        if (player == null) return;
        String template = raw(key);
        if (template == null || template.isBlank()) return;
        player.sendActionBar(render(player, template, pairs));
    }

    /**
     * Sends {@code key} with prefix to every online player and the console.
     *
     * @param key   the key
     * @param pairs alternating tokens and values
     */
    public void broadcast(String key, Object... pairs) {
        String template = raw(key);
        if (template == null || template.isBlank()) return;
        Component message = render(null, prefix() + template, pairs);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(papi ? render(player, prefix() + template, pairs) : message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /**
     * Gives {@code manager} the "no permission" and "players only" texts of this file and its format.
     *
     * @param manager           the command manager
     * @param noPermissionKey   key of the "no permission" text
     * @param playersOnlyKey    key of the "players only" text
     */
    public void applyTo(CommandManager manager, String noPermissionKey, String playersOnlyKey) {
        manager.textFormat(format).messages(raw(noPermissionKey), raw(playersOnlyKey));
    }

    private static OfflinePlayer viewerOf(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }
}
