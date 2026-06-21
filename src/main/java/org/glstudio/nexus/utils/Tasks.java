package org.glstudio.nexus.utils;

import org.glstudio.nexus.Nexus;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

@Getter
public class Tasks {

    private final JavaPlugin plugin = Nexus.getInstance();

    public void executeAsync(Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    public void execute(Runnable runnable) {
        Bukkit.getServer().getScheduler().runTask(plugin, runnable);
    }

    public void executeLater(long ticks, Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskLater(plugin, runnable, ticks);
    }

    public void executeLaterAsync(long ticks, Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(plugin, runnable, ticks);
    }

    public void executeScheduledAsync(long ticks, Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(plugin, runnable, 0L, ticks);
    }

    public BukkitTask executeScheduled(long ticks, Runnable runnable) {
        return Bukkit.getServer().getScheduler().runTaskTimer(plugin, runnable, 0L, ticks);
    }
}