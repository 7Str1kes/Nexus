package org.glstudio.nexus.modules.command;

import org.glstudio.nexus.utils.CC;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public abstract class Command {

    protected final JavaPlugin plugin;
    protected final CommandManager manager;
    private final String name;
    private final String permission;
    private final String description;

    public Command(CommandManager manager, String name, String permission) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.name = name;
        this.permission = permission;
        this.description = "";
    }

    public Command(CommandManager manager, String name, String permission, String description) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.name = name;
        this.permission = permission;
        this.description = description;
    }

    protected void sendMessage(CommandSender sender, String msg) {
        if (sender == null || msg == null) return;
        sender.sendMessage(CC.t(msg));
    }

    protected void sendMessage(Player player, String msg) {
        if (player == null || !player.isOnline() || msg == null) return;
        player.sendMessage(CC.t(msg));
    }

    protected void sendUsage(CommandSender sender) {
        if (sender == null || usage() == null) return;
        usage().forEach(line -> sendMessage(sender, line));
    }

    protected void sendUsage(Player player) {
        if (player == null || !player.isOnline() || usage() == null) return;
        usage().forEach(line -> sendMessage(player, line));
    }

    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    protected boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        sendMessage(sender, "&cThis command can only be executed by a player.");
        return false;
    }

    protected boolean requirePermission(CommandSender sender) {
        if (permission == null || permission.isEmpty()) return true;
        if (sender.hasPermission(permission)) return true;
        sendMessage(sender, "&cYou don't have permission to execute this command.");
        return false;
    }

    protected boolean requireArgs(CommandSender sender, String[] args, int minArgs) {
        if (args.length >= minArgs) return true;
        usage().forEach(line -> sendMessage(sender, line));
        return false;
    }

    protected List<String> filterTabCompletion(List<String> options, String arg) {
        if (arg == null || arg.isEmpty()) return options;
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(arg.toLowerCase()))
                .collect(Collectors.toList());
    }

    protected List<String> getOnlinePlayerNames() {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    public abstract List<String> aliases();

    public abstract List<String> usage();

    public abstract void execute(CommandSender sender, String[] args);

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}