package org.glstudio.nexus.modules.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.glstudio.nexus.modules.command.executor.CommandExecutorAdapter;
import org.glstudio.nexus.modules.command.executor.DynamicCommand;
import org.glstudio.nexus.modules.command.executor.TabCompleterAdapter;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.nexus.utils.TextFormat;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Registers a plugin's {@link Command}s in the server command map and takes them out again.
 *
 * <p>Commands are registered at runtime, so {@code plugin.yml} only declares permissions. Every
 * label is tracked: {@link #unregisterAll()} removes exactly the entries this manager added, leaving
 * any other plugin's command with the same label in place, and refreshes the clients' command
 * trees.
 *
 * <p>The manager also owns the text format and the shared error texts ("no permission", "players
 * only") used by every command it registers.
 */
public class CommandManager {

    private final JavaPlugin plugin;
    private final String fallbackPrefix;
    private final Supplier<CommandMap> commandMap;
    private final Runnable refreshClients;

    private final Map<String, Command> registeredCommands = new LinkedHashMap<>();
    private final Map<String, org.bukkit.command.Command> bukkitCommands = new LinkedHashMap<>();

    private TextFormat textFormat = TextFormat.LEGACY;
    private String noPermissionMessage;
    private String playersOnlyMessage;

    /**
     * Creates the manager of {@code plugin}, working on the server command map.
     *
     * @param plugin the owning plugin
     */
    public CommandManager(JavaPlugin plugin) {
        this(plugin, plugin.getName(), Bukkit::getCommandMap, CommandManager::refreshOnlinePlayers);
    }

    /**
     * Creates a manager working on a given command map, for tests and custom setups.
     *
     * @param plugin         the owning plugin, may be {@code null} in tests
     * @param fallbackPrefix the namespace of the {@code prefix:label} entries
     * @param commandMap     supplies the command map to work on
     * @param refreshClients called after unregistering, to resend command trees to players
     */
    public CommandManager(JavaPlugin plugin, String fallbackPrefix, Supplier<CommandMap> commandMap,
                          Runnable refreshClients) {
        this.plugin = plugin;
        this.fallbackPrefix = Objects.requireNonNull(fallbackPrefix, "fallbackPrefix");
        this.commandMap = Objects.requireNonNull(commandMap, "commandMap");
        this.refreshClients = Objects.requireNonNull(refreshClients, "refreshClients");
    }

    /**
     * Returns the owning plugin.
     *
     * @return the plugin
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * Sets the format of every text rendered by the registered commands.
     *
     * @param format {@link TextFormat#LEGACY} or {@link TextFormat#MINIMESSAGE}
     * @return this manager
     */
    public CommandManager textFormat(TextFormat format) {
        this.textFormat = Objects.requireNonNull(format, "format");
        return this;
    }

    /**
     * Returns the text format of this manager.
     *
     * @return the format
     */
    public TextFormat getTextFormat() {
        return textFormat;
    }

    /**
     * Sets the shared error texts, written in {@link #getTextFormat()}. Blank values keep the
     * current text.
     *
     * @param noPermission sent when a sender lacks a command's permission
     * @param playersOnly  sent when the console runs a player-only command
     * @return this manager
     */
    public CommandManager messages(String noPermission, String playersOnly) {
        if (noPermission != null && !noPermission.isBlank()) this.noPermissionMessage = noPermission;
        if (playersOnly != null && !playersOnly.isBlank()) this.playersOnlyMessage = playersOnly;
        return this;
    }

    /**
     * Returns the "no permission" text.
     *
     * @return the text set with {@link #messages}, or the global fallback
     */
    @SuppressWarnings("deprecation")
    public String getNoPermissionMessage() {
        return noPermissionMessage != null ? noPermissionMessage : Command.getNoPermissionMessage();
    }

    /**
     * Returns the "players only" text.
     *
     * @return the text set with {@link #messages}, or the global fallback
     */
    @SuppressWarnings("deprecation")
    public String getPlayersOnlyMessage() {
        return playersOnlyMessage != null ? playersOnlyMessage : Command.getPlayersOnlyMessage();
    }

    /**
     * Renders {@code text} in this manager's format.
     *
     * @param text the text
     * @return the component
     */
    public Component render(String text) {
        return CC.component(text, textFormat);
    }

    /**
     * Registers {@code command} and its aliases in the server command map.
     *
     * @param command the command
     * @return {@code true} if registered; failures are logged
     */
    public boolean register(Command command) {
        String key = command.getName().toLowerCase(Locale.ROOT);
        try {
            DynamicCommand dynamic = new DynamicCommand(this, command);
            boolean ownLabel = commandMap.get().register(fallbackPrefix, dynamic);
            if (!ownLabel) {
                LoggerUtils.logWarn("Command /" + key + " is taken by another plugin; it is only reachable as /"
                        + fallbackPrefix.toLowerCase(Locale.ROOT) + ":" + key + ".");
            }
            track(command, dynamic);
            return true;
        } catch (Exception e) {
            Diagnostics.logFailure("CommandManager#register(" + key + ")", e);
            return false;
        }
    }

    /**
     * Registers several commands.
     *
     * @param commands the commands
     * @return {@code true} if every command was registered
     */
    public boolean registerAll(Command... commands) {
        boolean all = true;
        for (Command command : commands) {
            all &= register(command);
        }
        return all;
    }

    /**
     * Routes a command declared in {@code plugin.yml} to {@code command}, with the same permission
     * check and texts as dynamically registered commands.
     *
     * @param pluginCommand the command from {@code plugin.getCommand(name)}
     * @param command       the implementation
     * @return {@code true} if bound; {@code false} if {@code pluginCommand} is {@code null}
     */
    public boolean bind(PluginCommand pluginCommand, Command command) {
        if (pluginCommand == null) {
            LoggerUtils.logError("Command '" + command.getName() + "' is missing from plugin.yml.");
            return false;
        }
        if (command.getPermission() != null && !command.getPermission().isEmpty()) {
            pluginCommand.setPermission(command.getPermission());
        }
        pluginCommand.permissionMessage(render(getNoPermissionMessage()));
        pluginCommand.setExecutor(new CommandExecutorAdapter(command));
        pluginCommand.setTabCompleter(new TabCompleterAdapter(command));
        track(command, pluginCommand);
        return true;
    }

    private void track(Command command, org.bukkit.command.Command bukkit) {
        String key = command.getName().toLowerCase(Locale.ROOT);
        registeredCommands.put(key, command);
        bukkitCommands.put(key, bukkit);
        for (String alias : command.aliases()) {
            registeredCommands.putIfAbsent(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    /**
     * Removes the command registered as {@code name} from the server command map, together with its
     * aliases and namespaced labels.
     *
     * <p>Only entries that point to this manager's command are removed, so another plugin's command
     * with the same label survives. Call {@link #unregisterAll()} or refresh the clients afterwards.
     *
     * @param name the command name used when registering
     */
    public void unregister(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        org.bukkit.command.Command bukkit = bukkitCommands.remove(key);
        Command command = registeredCommands.remove(key);
        if (command != null) {
            registeredCommands.values().removeIf(registered -> registered == command);
        }
        if (bukkit == null) return;

        try {
            CommandMap map = commandMap.get();
            map.getKnownCommands().values().removeIf(registered -> registered == bukkit);
            bukkit.unregister(map);
        } catch (Exception e) {
            Diagnostics.logFailure("CommandManager#unregister(" + key + ")", e);
        }
    }

    /**
     * Removes every command this manager registered and resends the command tree to online players.
     */
    public void unregisterAll() {
        for (String name : List.copyOf(bukkitCommands.keySet())) {
            unregister(name);
        }
        registeredCommands.clear();
        try {
            refreshClients.run();
        } catch (Exception e) {
            LoggerUtils.logDebug("CommandManager", "Could not refresh client command trees: " + e.getMessage());
        }
    }

    /**
     * Looks up a registered command by name or alias.
     *
     * @param name the label, any case
     * @return the command, or {@code null}
     */
    public Command getCommand(String name) {
        return registeredCommands.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns every registered label (names and aliases) mapped to its command.
     *
     * @return an unmodifiable view
     */
    public Map<String, Command> getRegisteredCommands() {
        return Collections.unmodifiableMap(registeredCommands);
    }

    /**
     * Returns the names of the commands currently registered in the server command map.
     *
     * @return the names, lower case
     */
    public Collection<String> trackedLabels() {
        return Collections.unmodifiableCollection(bukkitCommands.keySet());
    }

    private static void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.updateCommands();
            } catch (Exception ignored) {
                // A player disconnecting mid-loop is not worth a warning.
            }
        }
    }
}
