package org.glstudio.nexus.modules.command.executor;

import org.glstudio.nexus.modules.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TabCompleterAdapter implements TabCompleter {

    private final Command instance;

    public TabCompleterAdapter(Command instance) {
        this.instance = instance;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        return instance.onTabComplete(sender, args);
    }
}