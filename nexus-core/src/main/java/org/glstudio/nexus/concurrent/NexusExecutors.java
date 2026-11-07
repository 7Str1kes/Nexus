package org.glstudio.nexus.concurrent;

import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The thread pools a plugin owns for blocking work (database, files, network).
 *
 * <p>Blocking work does not belong on {@code ForkJoinPool.commonPool()}: it is sized for short CPU
 * work and shared by the whole server, and the plugin cannot drain it on shutdown. Each pool here
 * has named daemon threads ({@code GL-<Plugin>-IO-1}...) so it shows up in a thread dump, and
 * {@link #shutdown(Duration)} waits for queued work before giving up.
 *
 * <p>Pools:
 * <ul>
 *   <li>{@link #io()}: fixed pool for independent work.</li>
 *   <li>{@link #ordered(String)}: single-thread executors for work whose order matters, such as
 *       full-snapshot writes of the same record.</li>
 *   <li>{@link #scheduler()}: single-thread scheduler for retries and periodic background jobs.</li>
 * </ul>
 */
public final class NexusExecutors {

    private final String prefix;
    private final ThreadPoolExecutor io;
    private final Map<String, ExecutorService> ordered;
    private final ScheduledExecutorService scheduler;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong failures = new AtomicLong();
    private volatile boolean accepting = true;

    private NexusExecutors(Builder builder) {
        this.prefix = builder.prefix;
        int threads = Math.max(1, builder.ioThreads);
        this.io = new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), factory(prefix + "-IO"));
        this.io.allowCoreThreadTimeOut(true);

        Map<String, ExecutorService> queues = new LinkedHashMap<>();
        for (String name : builder.ordered) {
            queues.put(name, Executors.newSingleThreadExecutor(factory(prefix + "-" + capitalize(name))));
        }
        this.ordered = Map.copyOf(queues);
        this.scheduler = builder.scheduler
                ? Executors.newSingleThreadScheduledExecutor(factory(prefix + "-Scheduler"))
                : null;
    }

    /**
     * Starts building the executors of a plugin.
     *
     * @param pluginName the plugin name, used for thread names
     * @return a builder with one IO thread, no ordered queues and no scheduler
     */
    public static Builder builder(String pluginName) {
        return new Builder(pluginName);
    }

    /**
     * Returns the pool for independent blocking work.
     *
     * @return the IO executor
     */
    public Executor io() {
        return io;
    }

    /**
     * Returns the single-thread executor registered under {@code name}.
     *
     * @param name a name passed to {@link Builder#ordered(String...)}
     * @return the executor
     * @throws IllegalArgumentException if no such queue was configured
     */
    public Executor ordered(String name) {
        ExecutorService executor = ordered.get(name);
        if (executor == null) throw new IllegalArgumentException("No ordered executor named '" + name + "'");
        return executor;
    }

    /**
     * Returns an executor that runs on the IO pool after a delay, for non-blocking retry backoff.
     *
     * @param delay the delay
     * @param unit  its unit
     * @return the delayed executor
     */
    public Executor delayed(long delay, TimeUnit unit) {
        return CompletableFuture.delayedExecutor(delay, unit, io);
    }

    /**
     * Returns the background scheduler.
     *
     * @return the scheduler
     * @throws IllegalStateException if the builder did not enable it
     */
    public ScheduledExecutorService scheduler() {
        if (scheduler == null) throw new IllegalStateException("The scheduler was not enabled in the builder");
        return scheduler;
    }

    /**
     * Runs {@code supplier} on the IO pool, logging and counting a failure instead of letting it
     * vanish into an unobserved future.
     *
     * @param context  where it was started, usually {@code "Class#method"}
     * @param supplier the work
     * @param <T>      the result type
     * @return a future with the result; failed if the work throws or the pools are shutting down
     */
    public <T> CompletableFuture<T> supply(String context, Supplier<T> supplier) {
        return supply(io, context, supplier);
    }

    /**
     * Runs {@code runnable} on the IO pool, logging and counting a failure.
     *
     * @param context  where it was started, usually {@code "Class#method"}
     * @param runnable the work
     * @return a future that completes when the work ends
     */
    public CompletableFuture<Void> run(String context, Runnable runnable) {
        return supply(io, context, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Runs {@code supplier} on {@code executor}, logging and counting a failure.
     *
     * @param executor one of this object's executors
     * @param context  where it was started, usually {@code "Class#method"}
     * @param supplier the work
     * @param <T>      the result type
     * @return a future with the result
     */
    public <T> CompletableFuture<T> supply(Executor executor, String context, Supplier<T> supplier) {
        if (!accepting) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException("Shutting down; rejected: " + context));
        }
        inFlight.incrementAndGet();
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable t) {
                    failures.incrementAndGet();
                    Diagnostics.logFailure(context, t);
                    future.completeExceptionally(t);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            failures.incrementAndGet();
            Diagnostics.logFailure(context, e);
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Returns the number of tasks submitted through {@code supply}/{@code run} that have not
     * finished, plus anything waiting in the IO queue.
     *
     * @return pending task count
     */
    public int pendingTasks() {
        return inFlight.get() + io.getQueue().size();
    }

    /**
     * Returns how many tasks submitted through {@code supply}/{@code run} have failed.
     *
     * @return failure count since startup
     */
    public long failureCount() {
        return failures.get();
    }

    /**
     * Records a failure observed elsewhere, so it shows in {@link #failureCount()}.
     */
    public void recordFailure() {
        failures.incrementAndGet();
    }

    /**
     * Returns whether new work is still accepted.
     *
     * @return {@code false} once {@link #stopAccepting()} or {@link #shutdown(Duration)} was called
     */
    public boolean isAccepting() {
        return accepting;
    }

    /**
     * Rejects new work through {@code supply}/{@code run}; queued work still runs.
     */
    public void stopAccepting() {
        accepting = false;
    }

    /**
     * Stops accepting work, lets queued work finish and then stops every pool.
     *
     * @param timeout how long to wait for each pool
     * @return {@code true} if every pool finished in time
     */
    public boolean shutdown(Duration timeout) {
        accepting = false;
        List<ExecutorService> pools = new ArrayList<>();
        if (scheduler != null) pools.add(scheduler);
        pools.add(io);
        pools.addAll(ordered.values());

        boolean clean = true;
        for (ExecutorService pool : pools) {
            pool.shutdown();
        }
        for (ExecutorService pool : pools) {
            clean &= await(pool, timeout);
        }
        return clean;
    }

    private boolean await(ExecutorService pool, Duration timeout) {
        try {
            if (pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) return true;
            LoggerUtils.logWarn(prefix + " executor did not finish in " + timeout.toSeconds() + "s; forcing shutdown");
            pool.shutdownNow();
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            return false;
        }
    }

    private static ThreadFactory factory(String name) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> Diagnostics.logFailure("Thread " + t.getName(), e));
            return thread;
        };
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Configures a {@link NexusExecutors}.
     */
    public static final class Builder {
        private final String prefix;
        private int ioThreads = 1;
        private final List<String> ordered = new ArrayList<>();
        private boolean scheduler;

        private Builder(String pluginName) {
            Objects.requireNonNull(pluginName, "pluginName");
            this.prefix = pluginName.startsWith("GL-") ? pluginName : "GL-" + pluginName;
        }

        /**
         * Sets the size of the IO pool.
         *
         * @param threads number of threads, at least 1
         * @return this builder
         */
        public Builder ioThreads(int threads) {
            this.ioThreads = Math.max(1, threads);
            return this;
        }

        /**
         * Adds single-thread executors, one per name.
         *
         * @param names queue names, e.g. {@code "storage"}
         * @return this builder
         */
        public Builder ordered(String... names) {
            ordered.addAll(List.of(names));
            return this;
        }

        /**
         * Enables or disables the background scheduler.
         *
         * @param enabled {@code true} to create it
         * @return this builder
         */
        public Builder scheduler(boolean enabled) {
            this.scheduler = enabled;
            return this;
        }

        /**
         * Creates the executors.
         *
         * @return the configured executors
         */
        public NexusExecutors build() {
            return new NexusExecutors(this);
        }
    }
}
