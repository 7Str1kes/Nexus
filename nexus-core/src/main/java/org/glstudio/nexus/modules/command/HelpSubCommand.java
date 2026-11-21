package org.glstudio.nexus.modules.command;

import org.bukkit.command.CommandSender;
import org.glstudio.nexus.modules.lang.LanguageFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@code help}: either fixed lines of the language file, with more lines for admins, or a listing
 * of the subcommands the sender may use.
 *
 * @param <C> the context of the command group
 */
public final class HelpSubCommand<C> extends SubCommand<C> {

    private final Consumer<CommandSender> sender;
    private Supplier<String> usage = () -> "";
    private Supplier<String> description = () -> "";

    private HelpSubCommand(String permission, Consumer<CommandSender> sender, String... aliases) {
        super("help", permission, aliases);
        this.sender = sender;
    }

    /**
     * Help made of lines of the language file.
     *
     * @param messages  the plugin's messages
     * @param playerKey the lines everyone gets, such as {@code help}
     * @param adminKey  the lines admins get after them, or {@code null}
     * @param isAdmin   who is an admin
     * @param aliases   other names of the subcommand
     * @param <C>       the context of the command group
     * @return the subcommand
     */
    public static <C> HelpSubCommand<C> lines(LanguageFile messages, String playerKey, @Nullable String adminKey,
                                              Predicate<CommandSender> isAdmin, String... aliases) {
        return new HelpSubCommand<>("", to -> sendLines(messages, to, playerKey, adminKey, isAdmin), aliases);
    }

    /**
     * Help listing the subcommands the sender may use.
     *
     * @param permission  the permission of {@code help} itself, empty for none
     * @param subCommands the subcommands, read each time
     * @param header      sends what comes before the list
     * @param line        sends one subcommand's line
     * @param aliases     other names of the subcommand
     * @param <C>         the context of the command group
     * @return the subcommand
     */
    public static <C> HelpSubCommand<C> listing(String permission, Supplier<List<SubCommand<C>>> subCommands,
                                                Consumer<CommandSender> header,
                                                BiConsumer<CommandSender, SubCommand<C>> line, String... aliases) {
        return new HelpSubCommand<>(permission, to -> {
            header.accept(to);
            for (SubCommand<C> sub : subCommands.get()) {
                if (!sub.getPermission().isEmpty() && !to.hasPermission(sub.getPermission())) continue;
                line.accept(to, sub);
            }
        }, aliases);
    }

    /**
     * Sends help made of lines, such as when a command is used with no arguments.
     *
     * @param messages  the plugin's messages
     * @param to        who asked
     * @param playerKey the lines everyone gets
     * @param adminKey  the lines admins get after them, or {@code null}
     * @param isAdmin   who is an admin
     */
    public static void sendLines(LanguageFile messages, CommandSender to, String playerKey, @Nullable String adminKey,
                                 Predicate<CommandSender> isAdmin) {
        messages.sendLines(to, playerKey);
        if (adminKey != null && isAdmin.test(to)) messages.sendLines(to, adminKey);
    }

    /**
     * Sets what a listing of subcommands shows for {@code help} itself, read each time, such as from
     * the language file.
     *
     * @param usage       its usage
     * @param description its description
     * @return this subcommand
     */
    public HelpSubCommand<C> describedAs(Supplier<String> usage, Supplier<String> description) {
        this.usage = usage;
        this.description = description;
        return this;
    }

    @Override
    public String getUsage() {
        return usage.get();
    }

    @Override
    public String getDescription() {
        return description.get();
    }

    /**
     * Sends this help to someone.
     *
     * @param to who asked
     */
    public void send(CommandSender to) {
        sender.accept(to);
    }

    @Override
    public void execute(CommandSender to, C context, String[] args) {
        send(to);
    }
}
