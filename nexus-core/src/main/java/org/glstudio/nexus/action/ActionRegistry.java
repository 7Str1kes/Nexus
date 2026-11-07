package org.glstudio.nexus.action;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.menu.placeholder.Placeholders;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.nexus.utils.SoundSpec;
import org.glstudio.nexus.utils.TextFormat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Runs {@link ActionLine}s for a player. Must be used on the main thread.
 *
 * <p>Built-in types: {@code console} (alias {@code command}), {@code player}, {@code message}
 * (alias {@code msg}), {@code broadcast}, {@code actionbar}, {@code title} ({@code title|subtitle})
 * and {@code sound} (see {@link SoundSpec#parse(String)}). Plugins add their own with
 * {@link #register(String, BiConsumer)}. Unknown types and failing actions are reported once per
 * line, not on every run.
 */
public final class ActionRegistry {

    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500));

    private final Map<String, BiConsumer<Player, String>> handlers = new HashMap<>();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private final TextFormat format;

    private ActionRegistry(TextFormat format) {
        this.format = Objects.requireNonNull(format, "format");
    }

    /**
     * Creates a registry with the built-in types.
     *
     * @param format how message, broadcast, action bar and title texts are written
     * @return the registry
     */
    public static ActionRegistry withDefaults(TextFormat format) {
        ActionRegistry registry = new ActionRegistry(format);
        BiConsumer<Player, String> console = (player, command) -> {
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                registry.reportOnce("console:" + command, "Action '" + command + "' is not a command any plugin recognised.");
            }
        };
        registry.register("console", console);
        registry.register("command", console);
        registry.register("player", Player::performCommand);
        BiConsumer<Player, String> message = (player, text) -> player.sendMessage(registry.text(text));
        registry.register("message", message);
        registry.register("msg", message);
        registry.register("broadcast", (player, text) -> {
            Component component = registry.text(text);
            Bukkit.getOnlinePlayers().forEach(online -> online.sendMessage(component));
            Bukkit.getConsoleSender().sendMessage(component);
        });
        registry.register("actionbar", (player, text) -> player.sendActionBar(registry.text(text)));
        registry.register("title", (player, text) -> {
            String[] parts = text.split("\\|", 2);
            player.showTitle(Title.title(registry.text(parts[0]),
                    parts.length > 1 ? registry.text(parts[1]) : Component.empty(), TITLE_TIMES));
        });
        registry.register("sound", (player, text) -> SoundSpec.parse(text).play(player));
        return registry;
    }

    /**
     * Adds or replaces an action type.
     *
     * @param type    the type, any case
     * @param handler receives the player and the value, with placeholders already applied
     * @return this registry
     */
    public ActionRegistry register(String type, BiConsumer<Player, String> handler) {
        handlers.put(type.toLowerCase(Locale.ROOT), Objects.requireNonNull(handler, "handler"));
        return this;
    }

    /**
     * Returns whether {@code type} has a handler.
     *
     * @param type the type, any case
     * @return {@code true} if known
     */
    public boolean isKnown(String type) {
        return handlers.containsKey(type.toLowerCase(Locale.ROOT));
    }

    /**
     * Runs one action.
     *
     * @param player the target
     * @param action the action
     * @param pairs  placeholder pairs applied to the value, e.g. {@code "%player%", name}
     */
    public void run(Player player, ActionLine action, Object... pairs) {
        BiConsumer<Player, String> handler = handlers.get(action.type());
        if (handler == null) {
            reportOnce("type:" + action.type(), "Unknown action type [" + action.type() + "].");
            return;
        }
        String value = Placeholders.apply(action.value(), pairs);
        try {
            handler.accept(player, value);
        } catch (Exception e) {
            if (reported.add("fail:" + action.type() + ":" + value)) {
                Diagnostics.logFailure("Action [" + action.type() + "] " + value, e);
            }
        }
    }

    /**
     * Parses and runs every line; lines that do not parse are skipped.
     *
     * @param player the target
     * @param lines  the configured lines
     * @param pairs  placeholder pairs applied to each value
     */
    public void run(Player player, List<String> lines, Object... pairs) {
        for (String line : lines) {
            ActionLine.parse(line).ifPresent(action -> run(player, action, pairs));
        }
    }

    private Component text(String text) {
        return CC.component(text, format);
    }

    private void reportOnce(String key, String message) {
        if (reported.add(key)) LoggerUtils.logWarn(message);
    }
}
