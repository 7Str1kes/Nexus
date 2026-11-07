package org.glstudio.nexus.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.glstudio.nexus.Nexus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Thin layer over the Bukkit scheduler bound to one plugin, plus the bridge between
 * {@link CompletableFuture} chains and the main thread.
 */
public class Tasks {

    private final JavaPlugin explicitPlugin;
    private final Executor mainThread = this::runOnMain;

    /**
     * Creates a scheduler bound to the plugin passed to {@link Nexus#init(JavaPlugin)}.
     */
    public Tasks() {
        this.explicitPlugin = null;
    }

    /**
     * Creates a scheduler bound to {@code plugin}.
     *
     * @param plugin the owning plugin
     */
    public Tasks(JavaPlugin plugin) {
        this.explicitPlugin = plugin;
    }

    /**
     * Returns the owning plugin.
     *
     * @return the plugin passed to the constructor, or the one bound by {@link Nexus#init(JavaPlugin)}
     * @throws IllegalStateException if neither exists
     */
    public JavaPlugin getPlugin() {
        JavaPlugin plugin = explicitPlugin != null ? explicitPlugin : Nexus.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("Tasks has no owning plugin. Construct it with "
                    + "new Tasks(plugin), or call Nexus.init(plugin) in onEnable().");
        }
        return plugin;
    }

    /**
     * Returns an executor that runs on the main thread: inline if already there, otherwise on the
     * next tick. Tasks submitted after the plugin is disabled are dropped, because scheduling for a
     * disabled plugin throws and nobody is left to answer.
     *
     * <p>Use it as the executor of {@code thenAcceptAsync} and friends to touch the Bukkit API after
     * an asynchronous step.
     *
     * @return the main-thread executor
     */
    public Executor mainThread() {
        return mainThread;
    }

    /**
     * Returns a future that completes on the main thread with the same outcome as {@code future}.
     *
     * <p>If the plugin is disabled before the hop, the returned future completes with the original
     * outcome on the completing thread instead of never completing.
     *
     * @param future the future to follow
     * @param <T>    the result type
     * @return a future completed on the main thread
     */
    public <T> CompletableFuture<T> hop(CompletableFuture<T> future) {
        CompletableFuture<T> result = new CompletableFuture<>();
        future.whenComplete((value, error) -> {
            if (Bukkit.isPrimaryThread()) {
                complete(result, value, error);
                return;
            }
            try {
                Bukkit.getScheduler().runTask(getPlugin(), () -> complete(result, value, error));
            } catch (Exception rejected) {
                complete(result, value, error);
            }
        });
        return result;
    }

    private static <T> void complete(CompletableFuture<T> result, T value, Throwable error) {
        if (error != null) {
            result.completeExceptionally(error);
        } else {
            result.complete(value);
        }
    }

    /**
     * Runs {@code runnable} on the main thread: now if already there, otherwise next tick. Does
     * nothing if the plugin is disabled.
     *
     * @param runnable the work
     */
    public void runOnMain(Runnable runnable) {
        JavaPlugin plugin = getPlugin();
        if (!plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    /**
     * Runs {@code runnable} on the next tick.
     *
     * @param runnable the work
     * @return the scheduled task
     */
    public BukkitTask run(Runnable runnable) {
        return Bukkit.getScheduler().runTask(getPlugin(), runnable);
    }

    /**
     * Runs {@code runnable} off the main thread on the Bukkit async pool.
     *
     * @param runnable the work
     * @return the scheduled task
     */
    public BukkitTask runAsync(Runnable runnable) {
        return Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), runnable);
    }

    /**
     * Runs {@code runnable} on the main thread after {@code delayTicks}.
     *
     * @param delayTicks delay in ticks
     * @param runnable   the work
     * @return the scheduled task
     */
    public BukkitTask runLater(long delayTicks, Runnable runnable) {
        return Bukkit.getScheduler().runTaskLater(getPlugin(), runnable, delayTicks);
    }

    /**
     * Runs {@code runnable} off the main thread after {@code delayTicks}.
     *
     * @param delayTicks delay in ticks
     * @param runnable   the work
     * @return the scheduled task
     */
    public BukkitTask runLaterAsync(long delayTicks, Runnable runnable) {
        return Bukkit.getScheduler().runTaskLaterAsynchronously(getPlugin(), runnable, delayTicks);
    }

    /**
     * Runs {@code runnable} on the main thread every {@code periodTicks}, starting after
     * {@code delayTicks}.
     *
     * @param delayTicks  initial delay in ticks
     * @param periodTicks period in ticks
     * @param runnable    the work
     * @return the scheduled task, to cancel it later
     */
    public BukkitTask timer(long delayTicks, long periodTicks, Runnable runnable) {
        return Bukkit.getScheduler().runTaskTimer(getPlugin(), runnable, delayTicks, periodTicks);
    }

    /**
     * Runs {@code runnable} off the main thread every {@code periodTicks}, starting after
     * {@code delayTicks}.
     *
     * @param delayTicks  initial delay in ticks
     * @param periodTicks period in ticks
     * @param runnable    the work
     * @return the scheduled task, to cancel it later
     */
    public BukkitTask timerAsync(long delayTicks, long periodTicks, Runnable runnable) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), runnable, delayTicks, periodTicks);
    }

    /**
     * Runs {@code runnable} off the main thread.
     *
     * @param runnable the work
     * @deprecated returns nothing to cancel; use {@link #runAsync(Runnable)}
     */
    @Deprecated(since = "2.0.0")
    public void executeAsync(Runnable runnable) {
        runAsync(runnable);
    }

    /**
     * Runs {@code runnable} on the next tick.
     *
     * @param runnable the work
     * @deprecated returns nothing to cancel; use {@link #run(Runnable)}
     */
    @Deprecated(since = "2.0.0")
    public void execute(Runnable runnable) {
        run(runnable);
    }

    /**
     * Runs {@code runnable} on the main thread after {@code ticks}.
     *
     * @param ticks    delay in ticks
     * @param runnable the work
     * @deprecated returns nothing to cancel; use {@link #runLater(long, Runnable)}
     */
    @Deprecated(since = "2.0.0")
    public void executeLater(long ticks, Runnable runnable) {
        runLater(ticks, runnable);
    }

    /**
     * Runs {@code runnable} off the main thread after {@code ticks}.
     *
     * @param ticks    delay in ticks
     * @param runnable the work
     * @deprecated returns nothing to cancel; use {@link #runLaterAsync(long, Runnable)}
     */
    @Deprecated(since = "2.0.0")
    public void executeLaterAsync(long ticks, Runnable runnable) {
        runLaterAsync(ticks, runnable);
    }

    /**
     * Runs {@code runnable} off the main thread every {@code ticks}, starting now.
     *
     * @param ticks    period in ticks
     * @param runnable the work
     * @deprecated the task cannot be cancelled; use {@link #timerAsync(long, long, Runnable)}
     */
    @Deprecated(since = "2.0.0")
    public void executeScheduledAsync(long ticks, Runnable runnable) {
        timerAsync(0L, ticks, runnable);
    }

    /**
     * Runs {@code runnable} on the main thread every {@code ticks}, starting now.
     *
     * @param ticks    period in ticks
     * @param runnable the work
     * @return the scheduled task
     * @deprecated use {@link #timer(long, long, Runnable)}
     */
    @Deprecated(since = "2.0.0")
    public BukkitTask executeScheduled(long ticks, Runnable runnable) {
        return timer(0L, ticks, runnable);
    }
}
