package org.glstudio.nexus.modules.command;

import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Base class of every command registered through a {@link CommandManager}.
 *
 * <p>Text sent through the helpers here ({@link #sendMessage}, {@link #sendUsage}, the
 * {@code require*} checks) is rendered with the manager's {@link org.glstudio.nexus.utils.TextFormat},
 * so it must be written in the same format as the plugin's language file.
 */
@Getter
public abstract class Command {

    private static String noPermissionMessage = "&cYou don't have permission to execute this command.";
    private static String playersOnlyMessage = "&cThis command can only be executed by a player.";

    /** The owning plugin. */
    protected final JavaPlugin plugin;
    /** The manager this command is registered with. */
    protected final CommandManager manager;
    /**
     * The label, without slash.
     */
    private final String name;
    /**
     * The permission checked before execution, empty for none.
     */
    private final String permission;
    /**
     * Short description shown by {@code /help}.
     */
    private final String description;

    /**
     * Creates a command without description.
     *
     * @param manager    the manager that will register it
     * @param name       the label, without slash
     * @param permission the permission checked before {@link #execute}, or empty for none
     */
    public Command(CommandManager manager, String name, String permission) {
        this(manager, name, permission, "");
    }

    /**
     * Creates a command.
     *
     * @param manager     the manager that will register it
     * @param name        the label, without slash
     * @param permission  the permission checked before {@link #execute}, or empty for none
     * @param description short description shown by {@code /help}
     */
    public Command(CommandManager manager, String name, String permission, String description) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.name = name;
        this.permission = permission;
        this.description = description;
    }

    /**
     * Sets the fallback "no permission" text used by managers without their own.
     *
     * @param message the text
     * @deprecated set it per plugin with {@link CommandManager#messages(String, String)}, which also
     * knows the text format
     */
    @Deprecated(since = "2.0.0")
    public static void setNoPermissionMessage(String message) {
        if (message != null && !message.isBlank()) noPermissionMessage = message;
    }

    /**
     * Sets the fallback "players only" text used by managers without their own.
     *
     * @param message the text
     * @deprecated set it per plugin with {@link CommandManager#messages(String, String)}, which also
     * knows the text format
     */
    @Deprecated(since = "2.0.0")
    public static void setPlayersOnlyMessage(String message) {
        if (message != null && !message.isBlank()) playersOnlyMessage = message;
    }

    /**
     * Returns the fallback "no permission" text.
     *
     * @return the text
     * @deprecated use {@link CommandManager#getNoPermissionMessage()}
     */
    @Deprecated(since = "2.0.0")
    public static String getNoPermissionMessage() {
        return noPermissionMessage;
    }

    /**
     * Returns the fallback "players only" text.
     *
     * @return the text
     * @deprecated use {@link CommandManager#getPlayersOnlyMessage()}
     */
    @Deprecated(since = "2.0.0")
    public static String getPlayersOnlyMessage() {
        return playersOnlyMessage;
    }

    /**
     * Sends {@code msg} rendered in the manager's text format. Does nothing for {@code null}.
     *
     * @param sender the recipient
     * @param msg    the text
     */
    protected void sendMessage(CommandSender sender, String msg) {
        if (sender == null || msg == null) return;
        sender.sendMessage(manager.render(msg));
    }

    /**
     * Sends {@code msg} to {@code player} if still online.
     *
     * @param player the recipient
     * @param msg    the text
     */
    protected void sendMessage(Player player, String msg) {
        if (player == null || !player.isOnline() || msg == null) return;
        player.sendMessage(manager.render(msg));
    }

    /**
     * Sends every line of {@link #usage()}.
     *
     * @param sender the recipient
     */
    protected void sendUsage(CommandSender sender) {
        if (sender == null || usage() == null) return;
        usage().forEach(line -> sendMessage(sender, line));
    }

    /**
     * Sends every line of {@link #usage()} if the player is online.
     *
     * @param player the recipient
     */
    protected void sendUsage(Player player) {
        if (player == null || !player.isOnline() || usage() == null) return;
        usage().forEach(line -> sendMessage(player, line));
    }

    /**
     * Returns whether {@code sender} is a player.
     *
     * @param sender the sender
     * @return {@code true} for players
     */
    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    /**
     * Checks that {@code sender} is a player, telling them otherwise.
     *
     * @param sender the sender
     * @return {@code true} for players
     */
    protected boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        sendMessage(sender, manager.getPlayersOnlyMessage());
        return false;
    }

    /**
     * Checks this command's permission, telling the sender if it is missing.
     *
     * @param sender the sender
     * @return {@code true} if allowed
     */
    protected boolean requirePermission(CommandSender sender) {
        if (permission == null || permission.isEmpty()) return true;
        if (sender.hasPermission(permission)) return true;
        sendMessage(sender, manager.getNoPermissionMessage());
        return false;
    }

    /**
     * Checks that there are at least {@code minArgs} arguments, sending the usage otherwise.
     *
     * @param sender  the sender
     * @param args    the arguments
     * @param minArgs the minimum count
     * @return {@code true} if there are enough
     */
    protected boolean requireArgs(CommandSender sender, String[] args, int minArgs) {
        if (args.length >= minArgs) return true;
        sendUsage(sender);
        return false;
    }

    /**
     * Keeps the options that start with {@code arg}, ignoring case.
     *
     * @param options the candidates
     * @param arg     what the sender typed so far
     * @return the matching options
     */
    protected List<String> filterTabCompletion(List<String> options, String arg) {
        if (arg == null || arg.isEmpty()) return options;
        String lower = arg.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    /**
     * Returns the names of every online player.
     *
     * <p>Prefer {@link org.glstudio.nexus.utils.Players#visibleNames(CommandSender)} for tab
     * completion: it hides vanished players.
     *
     * @return online player names
     */
    protected List<String> getOnlinePlayerNames() {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    /**
     * Returns the aliases registered next to the name.
     *
     * @return the aliases, never {@code null}
     */
    public abstract List<String> aliases();

    /**
     * Returns the usage lines, in the manager's text format.
     *
     * @return the lines, never {@code null}
     */
    public abstract List<String> usage();

    /**
     * Runs the command. The permission was already checked when registered through
     * {@link CommandManager}.
     *
     * @param sender who ran it
     * @param args   the arguments after the label
     */
    public abstract void execute(CommandSender sender, String[] args);

    /**
     * Suggests completions for the last argument.
     *
     * @param sender who is typing
     * @param args   the arguments typed so far
     * @return suggestions, never {@code null}
     */
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
