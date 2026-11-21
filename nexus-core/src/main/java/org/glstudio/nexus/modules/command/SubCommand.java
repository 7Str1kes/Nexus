package org.glstudio.nexus.modules.command;

import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One subcommand of a {@link CommandGroup}, e.g. {@code create} in {@code /clan create}.
 *
 * @param <C> context passed by the group to every subcommand, e.g. the economy a currency command
 *            works on; use {@link Void} when there is none
 */
public abstract class SubCommand<C> {

    private final String name;
    private final String permission;
    private final List<String> aliases;

    /**
     * Creates a subcommand.
     *
     * @param name       the label after the root command
     * @param permission the permission it needs, or empty to fall back to the root command's
     * @param aliases    alternative labels
     */
    protected SubCommand(String name, String permission, String... aliases) {
        this.name = Objects.requireNonNull(name, "name");
        this.permission = permission == null ? "" : permission;
        this.aliases = List.of(aliases);
    }

    /**
     * Runs the subcommand.
     *
     * @param sender  who ran it
     * @param context the group's context
     * @param args    the arguments after the subcommand label
     */
    public abstract void execute(CommandSender sender, C context, String[] args);

    /**
     * Suggests completions for the arguments after the subcommand label.
     *
     * @param sender  who is typing
     * @param context the group's context
     * @param args    the arguments typed after the label
     * @return suggestions, never {@code null}
     */
    public List<String> complete(CommandSender sender, C context, String[] args) {
        return List.of();
    }

    /**
     * Returns the usage line shown in the group's help, in the manager's text format.
     *
     * @return the usage, empty if none
     */
    public String getUsage() {
        return "";
    }

    /**
     * Returns the description shown in the group's help, in the manager's text format.
     *
     * @return the description, empty if none
     */
    public String getDescription() {
        return "";
    }

    /**
     * Returns whether {@code label} is this subcommand's name or one of its aliases, ignoring case.
     *
     * @param label what the sender typed
     * @return {@code true} on a match
     */
    public boolean matches(String label) {
        if (name.equalsIgnoreCase(label)) return true;
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(label)) return true;
        }
        return false;
    }

    /**
     * Returns the label.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the own permission of this subcommand.
     *
     * @return the permission, empty if it relies on the root command's
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Returns the alternative labels.
     *
     * @return the aliases, lower and upper case preserved
     */
    public List<String> getAliases() {
        return aliases;
    }

    /**
     * Returns the lower-case name, the key under which the group stores it.
     *
     * @return the key
     */
    String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Keeps the options that start with what was typed, ignoring case.
     *
     * @param options the options
     * @param typed   what was typed so far
     * @return the matching options
     */
    protected static List<String> filter(Collection<String> options, String typed) {
        return Completions.filter(options, typed);
    }

    /**
     * Joins the arguments from one on, with spaces.
     *
     * @param args the arguments
     * @param from the first one to join
     * @return the text, empty when there are none
     */
    protected static String join(String[] args, int from) {
        return Completions.join(args, from);
    }
}
