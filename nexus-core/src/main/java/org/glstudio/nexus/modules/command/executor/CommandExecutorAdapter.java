package org.glstudio.nexus.modules.command.executor;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.glstudio.nexus.modules.command.Command;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit {@link CommandExecutor} that runs a Nexus {@link Command}; used for commands declared in
 * {@code plugin.yml} (see {@link org.glstudio.nexus.modules.command.CommandManager#bind}).
 */
public class CommandExecutorAdapter implements CommandExecutor {

    private final Command instance;

    /**
     * Wraps {@code instance}.
     *
     * @param instance the command to run
     */
    public CommandExecutorAdapter(Command instance) {
        this.instance = instance;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        instance.execute(sender, args);
        return true;
    }
}
