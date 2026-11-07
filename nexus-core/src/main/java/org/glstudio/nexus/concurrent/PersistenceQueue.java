package org.glstudio.nexus.concurrent;

import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

/**
 * A single writer thread for every database write of a plugin, so no write blocks a tick and two
 * writes of the same record cannot interleave.
 *
 * <p>It also watches the health of the backend: consecutive failures are counted and reported with
 * a plugin-specific warning, and writes that take longer than a threshold are logged. During
 * shutdown, or if the queue rejects a task, the write runs inline so it still reaches the disk.
 */
public final class PersistenceQueue {

    /** Number of consecutive failures after which {@link #isUnhealthy()} returns {@code true}. */
    public static final int UNHEALTHY_THRESHOLD = 5;

    private final ExecutorService executor;
    private final long slowWarnMillis;
    private final Duration shutdownGrace;
    private final IntFunction<String> failureWarning;
    private final BiFunction<String, Long, String> slowWarning;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile boolean accepting = true;

    private PersistenceQueue(Builder builder) {
        String threadName = builder.threadName;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            // Non-daemon on purpose: queued writes must finish before the JVM can exit.
            thread.setDaemon(false);
            return thread;
        });
        this.slowWarnMillis = builder.slowWarnMillis;
        this.shutdownGrace = builder.shutdownGrace;
        this.failureWarning = builder.failureWarning;
        this.slowWarning = builder.slowWarning;
    }

    /**
     * Starts configuring a queue.
     *
     * @param threadName name of the writer thread, e.g. {@code "GL-Clans-Persistence"}
     * @return a builder with a 50 ms slow-write threshold and a 15 s shutdown grace
     */
    public static Builder builder(String threadName) {
        return new Builder(threadName);
    }

    /**
     * Queues a write. Runs it inline if the queue is draining or rejects it.
     *
     * @param description what is written, used in log lines
     * @param task        the write
     */
    public void submit(String description, ThrowingTask task) {
        if (!accepting) {
            runGuarded(description, task);
            return;
        }
        try {
            executor.execute(() -> runGuarded(description, task));
        } catch (Exception rejected) {
            LoggerUtils.logWarn("Persistence queue rejected '" + description + "'; running inline.");
            runGuarded(description, task);
        }
    }

    /**
     * Runs a write on the calling thread with the same failure accounting as {@link #submit}.
     *
     * @param description what is written, used in log lines
     * @param task        the write
     * @return {@code true} if the write succeeded
     */
    public boolean runGuarded(String description, ThrowingTask task) {
        long startedAt = System.nanoTime();
        try {
            task.run();
            consecutiveFailures.set(0);
            warnIfSlow(description, startedAt);
            return true;
        } catch (Exception e) {
            int failures = consecutiveFailures.incrementAndGet();
            Diagnostics.logFailure(description, e);
            if (failures == UNHEALTHY_THRESHOLD || failures % 25 == 0) {
                LoggerUtils.logError(failureWarning.apply(failures));
            }
            return false;
        }
    }

    private void warnIfSlow(String description, long startedAt) {
        long millis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (millis >= slowWarnMillis) {
            LoggerUtils.logWarn(slowWarning.apply(description, millis));
        }
    }

    /**
     * Returns whether the backend has failed {@value #UNHEALTHY_THRESHOLD} or more times in a row.
     *
     * @return {@code true} if writes are currently failing
     */
    public boolean isUnhealthy() {
        return consecutiveFailures.get() >= UNHEALTHY_THRESHOLD;
    }

    /**
     * Returns the number of consecutive failed writes.
     *
     * @return failures since the last successful write
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Stops queueing, waits for queued writes and stops the writer thread. Later writes run inline.
     *
     * @return {@code true} if every queued write finished within the grace period
     */
    public boolean shutdownAndDrain() {
        accepting = false;
        executor.shutdown();
        try {
            if (executor.awaitTermination(shutdownGrace.toMillis(), TimeUnit.MILLISECONDS)) return true;
            LoggerUtils.logError("Database writes did not finish within " + shutdownGrace.toSeconds()
                    + "s. Some state may not have been saved.");
            executor.shutdownNow();
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            return false;
        }
    }

    /**
     * A write that may throw.
     */
    @FunctionalInterface
    public interface ThrowingTask {
        /**
         * Performs the write.
         *
         * @throws Exception if the write fails
         */
        void run() throws Exception;
    }

    /**
     * Configures a {@link PersistenceQueue}.
     */
    public static final class Builder {
        private final String threadName;
        private long slowWarnMillis = 50L;
        private Duration shutdownGrace = Duration.ofSeconds(15);
        private IntFunction<String> failureWarning = failures -> "Persistence has failed " + failures
                + " times in a row. State is NOT reaching the database.";
        private BiFunction<String, Long, String> slowWarning = (description, millis) -> "Write '" + description
                + "' took " + millis + "ms. If this happens often the database round-trip is hurting throughput.";

        private Builder(String threadName) {
            this.threadName = Objects.requireNonNull(threadName, "threadName");
        }

        /**
         * Sets the duration above which a write is logged as slow.
         *
         * @param millis threshold in milliseconds
         * @return this builder
         */
        public Builder slowWarnMillis(long millis) {
            this.slowWarnMillis = millis;
            return this;
        }

        /**
         * Sets how long {@link PersistenceQueue#shutdownAndDrain()} waits.
         *
         * @param grace the grace period
         * @return this builder
         */
        public Builder shutdownGrace(Duration grace) {
            this.shutdownGrace = Objects.requireNonNull(grace, "grace");
            return this;
        }

        /**
         * Sets the error logged after 5 consecutive failures and every 25 after that.
         *
         * @param warning receives the failure count and returns the message
         * @return this builder
         */
        public Builder failureWarning(IntFunction<String> warning) {
            this.failureWarning = Objects.requireNonNull(warning, "warning");
            return this;
        }

        /**
         * Sets the warning logged for a slow write.
         *
         * @param warning receives the description and the duration in milliseconds
         * @return this builder
         */
        public Builder slowWarning(BiFunction<String, Long, String> warning) {
            this.slowWarning = Objects.requireNonNull(warning, "warning");
            return this;
        }

        /**
         * Creates the queue and its writer thread.
         *
         * @return the queue
         */
        public PersistenceQueue build() {
            return new PersistenceQueue(this);
        }
    }
}
