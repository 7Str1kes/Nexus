package org.glstudio.nexus.modules.command.executor;

import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.glstudio.nexus.modules.command.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Bukkit {@link TabCompleter} that delegates to a Nexus {@link Command}; used for commands declared
 * in {@code plugin.yml} (see {@link org.glstudio.nexus.modules.command.CommandManager#bind}).
 */
public class TabCompleterAdapter implements TabCompleter {

    private final Command instance;

    /**
     * Wraps {@code instance}.
     *
     * @param instance the command that completes
     */
    public TabCompleterAdapter(Command instance) {
        this.instance = instance;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command,
                                                @NotNull String alias, String @NotNull [] args) {
        return instance.onTabComplete(sender, args);
    }
}
