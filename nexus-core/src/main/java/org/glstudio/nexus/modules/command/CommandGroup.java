package org.glstudio.nexus.modules.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A command made of {@link SubCommand}s, e.g. {@code /clan create|invite|leave}.
 *
 * <p>Dispatch and tab completion are final: the group looks the subcommand up by name or alias,
 * checks its permission, strips the label from the arguments and delegates. Subclasses only decide
 * what happens with no arguments, an unknown label or a missing permission.
 *
 * <p>A subcommand without its own permission inherits the group's permission.
 *
 * @param <C> context passed to every subcommand; use {@link Void} when there is none
 */
public abstract class CommandGroup<C> extends Command {

    private final Map<String, SubCommand<C>> byLabel = new LinkedHashMap<>();
    private final List<SubCommand<C>> ordered = new ArrayList<>();

    /**
     * Creates a group.
     *
     * @param manager    the manager that will register it
     * @param name       the root label
     * @param permission the permission of the root command, or empty for none
     */
    protected CommandGroup(CommandManager manager, String name, String permission) {
        super(manager, name, permission);
    }

    /**
     * Adds a subcommand. The first registration of a label wins.
     *
     * @param subCommand the subcommand
     */
    protected final void register(SubCommand<C> subCommand) {
        ordered.add(subCommand);
        byLabel.putIfAbsent(subCommand.key(), subCommand);
        for (String alias : subCommand.getAliases()) {
            byLabel.putIfAbsent(alias.toLowerCase(Locale.ROOT), subCommand);
        }
    }

    /**
     * Returns the subcommands in registration order, for help listings.
     *
     * @return an unmodifiable list
     */
    public final List<SubCommand<C>> getSubCommands() {
        return Collections.unmodifiableList(ordered);
    }

    /**
     * Looks a subcommand up by name or alias.
     *
     * @param label the label, any case
     * @return the subcommand, or {@code null}
     */
    public final SubCommand<C> getSubCommand(String label) {
        return label == null ? null : byLabel.get(label.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the context handed to every subcommand for this execution.
     *
     * @param sender who ran the command
     * @return the context; {@code null} by default
     */
    protected C context(CommandSender sender) {
        return null;
    }

    /**
     * Called when the root command runs without arguments. Sends the usage by default.
     *
     * @param sender  who ran it
     * @param context the context
     */
    protected void onNoArgs(CommandSender sender, C context) {
        sendUsage(sender);
    }

    /**
     * Called when the first argument is not a known subcommand. Sends the usage by default.
     *
     * @param sender  who ran it
     * @param context the context
     * @param label   what was typed
     */
    protected void onUnknown(CommandSender sender, C context, String label) {
        sendUsage(sender);
    }

    /**
     * Called when the sender lacks the subcommand's permission. Sends the manager's
     * "no permission" text by default.
     *
     * @param sender     who ran it
     * @param subCommand the subcommand they tried
     */
    protected void onNoPermission(CommandSender sender, SubCommand<C> subCommand) {
        sendMessage(sender, manager.getNoPermissionMessage());
    }

    /**
     * Returns whether {@code sender} may run {@code subCommand}.
     *
     * @param sender     the sender
     * @param subCommand the subcommand
     * @return {@code true} if it has the subcommand's permission, or the group's when the
     * subcommand has none
     */
    protected boolean isAllowed(CommandSender sender, SubCommand<C> subCommand) {
        String permission = subCommand.getPermission().isEmpty() ? getPermission() : subCommand.getPermission();
        return permission == null || permission.isEmpty() || sender.hasPermission(permission);
    }

    /**
     * Returns suggestions for the first argument besides the subcommand names, for groups that also
     * accept something else there, such as a player name.
     *
     * @param sender  who is typing
     * @param context the context
     * @return the extra suggestions, empty by default
     */
    protected List<String> extraCompletions(CommandSender sender, C context) {
        return List.of();
    }

    @Override
    public final void execute(CommandSender sender, String[] args) {
        C context = context(sender);
        if (args.length == 0) {
            onNoArgs(sender, context);
            return;
        }
        SubCommand<C> subCommand = getSubCommand(args[0]);
        if (subCommand == null) {
            onUnknown(sender, context, args[0]);
            return;
        }
        if (!isAllowed(sender, subCommand)) {
            onNoPermission(sender, subCommand);
            return;
        }
        subCommand.execute(sender, context, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public final List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> names = new ArrayList<>();
            for (SubCommand<C> subCommand : ordered) {
                if (isAllowed(sender, subCommand)) names.add(subCommand.getName());
            }
            names.addAll(extraCompletions(sender, context(sender)));
            return filterTabCompletion(names, args.length == 0 ? "" : args[0]);
        }
        SubCommand<C> subCommand = getSubCommand(args[0]);
        if (subCommand == null || !isAllowed(sender, subCommand)) return List.of();
        List<String> completions = subCommand.complete(sender, context(sender), Arrays.copyOfRange(args, 1, args.length));
        return completions == null ? List.of() : completions;
    }
}
