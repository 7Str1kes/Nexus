package org.glstudio.nexus.utils;

import org.glstudio.nexus.Nexus;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Scheduling helpers bound to a plugin.
 *
 * <p>The owning plugin is resolved when a task is actually scheduled, not when this object is
 * constructed. That matters because {@link org.glstudio.nexus.api.NexusAPI} builds a Tasks
 * eagerly in its own constructor: with the plugin captured at construction time, any plugin
 * that shades Nexus without calling {@link Nexus#init(JavaPlugin)} — or that touches the API
 * before doing so — ended up with a Tasks whose plugin was null, and every method on it failed
 * inside {@code Bukkit.getScheduler()} with nothing to say why.
 *
 * <p>Prefer {@link #Tasks(JavaPlugin)}, which needs no global state at all.
 */
public class Tasks {

    private final JavaPlugin explicitPlugin;

    /** Uses whichever plugin called {@link Nexus#init(JavaPlugin)}. */
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

    /**
     * Runs on the main thread, immediately when the caller is already there.
     *
     * <p>For work that can arrive from either thread — a chat listener, a Redis subscriber —
     * where bouncing through the scheduler when already on the main thread would only add a
     * tick of latency. A no-op once the plugin is disabled, when scheduling would throw.
     */
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
