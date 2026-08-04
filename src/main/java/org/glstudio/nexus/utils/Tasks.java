package org.glstudio.nexus.utils;

import org.glstudio.nexus.Nexus;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class Tasks {
    private final JavaPlugin explicitPlugin;

    public Tasks() {
        this.explicitPlugin = null;
    }

    public Tasks(JavaPlugin plugin) {
        this.explicitPlugin = plugin;
    }

    public JavaPlugin getPlugin() {
        JavaPlugin plugin = explicitPlugin != null ? explicitPlugin : Nexus.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("Tasks has no owning plugin. Construct it with "
                    + "new Tasks(plugin), or call Nexus.init(plugin) in onEnable().");
        }
        return plugin;
    }

    public void executeAsync(Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskAsynchronously(getPlugin(), runnable);
    }

    public void execute(Runnable runnable) {
        Bukkit.getServer().getScheduler().runTask(getPlugin(), runnable);
    }

    public void runOnMain(Runnable runnable) {
        JavaPlugin plugin = getPlugin();
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getServer().getScheduler().runTask(plugin, runnable);
    }

    public void executeLater(long ticks, Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskLater(getPlugin(), runnable, ticks);
    }

    public void executeLaterAsync(long ticks, Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(getPlugin(), runnable, ticks);
    }

    public void executeScheduledAsync(long ticks, Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(getPlugin(), runnable, 0L, ticks);
    }

    public BukkitTask executeScheduled(long ticks, Runnable runnable) {
        return Bukkit.getServer().getScheduler().runTaskTimer(getPlugin(), runnable, 0L, ticks);
    }
}
