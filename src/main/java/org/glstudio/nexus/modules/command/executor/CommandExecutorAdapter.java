package org.glstudio.nexus.modules.command.executor;

import org.glstudio.nexus.modules.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class CommandExecutorAdapter implements CommandExecutor {

    private final Command instance;

    public CommandExecutorAdapter(Command instance) {
        this.instance = instance;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String label, String @NotNull [] args) {
        instance.execute(sender, args);
        return true;
    }
}