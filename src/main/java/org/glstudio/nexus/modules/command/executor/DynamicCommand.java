package org.glstudio.nexus.modules.command.executor;

import lombok.Setter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Setter
public class DynamicCommand extends org.bukkit.command.Command {

    private CommandExecutor executor;
    private TabCompleter tabCompleter;

    public DynamicCommand(String name, String description, String permission, String[] aliases) {
        super(name);
        setDescription(description);
        setPermission(permission);
        setAliases(List.of(aliases));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (executor != null) {
            return executor.onCommand(sender, this, label, args);
        }
        return false;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) throws IllegalArgumentException {
        if (tabCompleter != null) {
            List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
            return completions != null ? completions : new ArrayList<>();
        }
        return super.tabComplete(sender, alias, args);
    }
}