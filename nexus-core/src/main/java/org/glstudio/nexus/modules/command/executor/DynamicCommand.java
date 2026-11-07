package org.glstudio.nexus.modules.command.executor;

import org.bukkit.command.CommandSender;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bukkit command registered in the command map for a Nexus {@link Command}.
 *
 * <p>Checks the permission silently and answers with the manager's "no permission" text, rendered
 * in the manager's format, before delegating.
 */
public class DynamicCommand extends org.bukkit.command.Command {

    private final CommandManager manager;
    private final Command command;

    /**
     * Wraps {@code command} for registration.
     *
     * @param manager the manager that registers it
     * @param command the implementation
     */
    public DynamicCommand(CommandManager manager, Command command) {
        super(command.getName());
        this.manager = manager;
        this.command = command;
        setDescription(command.getDescription() == null ? "" : command.getDescription());
        String permission = command.getPermission();
        setPermission(permission == null || permission.isEmpty() ? null : permission);
        setAliases(new ArrayList<>(command.aliases()));
    }

    /**
     * Returns the wrapped Nexus command.
     *
     * @return the command
     */
    public Command getCommand() {
        return command;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!testPermissionSilent(sender)) {
            sender.sendMessage(manager.render(manager.getNoPermissionMessage()));
            return true;
        }
        command.execute(sender, args);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
                                             String @NotNull [] args) {
        if (!testPermissionSilent(sender)) return new ArrayList<>();
        List<String> completions = command.onTabComplete(sender, args);
        return completions != null ? completions : new ArrayList<>();
    }
}
