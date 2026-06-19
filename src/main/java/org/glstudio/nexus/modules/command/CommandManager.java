package org.glstudio.nexus.modules.command;

import org.glstudio.nexus.modules.command.executor.CommandExecutorAdapter;
import org.glstudio.nexus.modules.command.executor.DynamicCommand;
import org.glstudio.nexus.modules.command.executor.TabCompleterAdapter;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
public class CommandManager {

    private final JavaPlugin plugin;
    private final Map<String, Command> registeredCommands = new HashMap<>();

    public CommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Command command) {
        try {
            DynamicCommand dynamicCommand = new DynamicCommand(
                    command.getName(),
                    command.getDescription(),
                    command.getPermission(),
                    command.aliases().toArray(new String[0])
            );

            dynamicCommand.setExecutor(new CommandExecutorAdapter(command));
            dynamicCommand.setTabCompleter(new TabCompleterAdapter(command));

            getCommandMap().register(plugin.getName(), dynamicCommand);
            registeredCommands.put(command.getName().toLowerCase(), command);

            command.aliases().forEach(alias ->
                    registeredCommands.putIfAbsent(alias.toLowerCase(), command));

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register command '" + command.getName() + "': " + e.getMessage());
        }
    }

    public void unregister(String name) {
        registeredCommands.remove(name.toLowerCase());
    }

    public void unregisterAll() {
        registeredCommands.clear();
    }

    public Command getCommand(String name) {
        return registeredCommands.get(name.toLowerCase());
    }

    public Map<String, Command> getRegisteredCommands() {
        return Collections.unmodifiableMap(registeredCommands);
    }

    private CommandMap getCommandMap() throws Exception {
        Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
        field.setAccessible(true);
        return (CommandMap) field.get(Bukkit.getServer());
    }
}