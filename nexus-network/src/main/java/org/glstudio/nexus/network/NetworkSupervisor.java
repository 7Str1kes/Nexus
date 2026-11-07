package org.glstudio.nexus.network;

import org.glstudio.nexus.utils.LoggerUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Retries bringing multi-server support online after a failed start, with a delay that doubles from
 * 30 s up to 5 min, so a Redis outage at boot does not leave the server local-only until a restart.
 */
public final class NetworkSupervisor {

    private static final Duration INITIAL_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private final ScheduledExecutorService scheduler;
    private final String feature;
    private volatile boolean stopped;
    private volatile long delaySeconds = INITIAL_DELAY.toSeconds();

    /**
     * Creates a supervisor.
     *
     * @param scheduler runs the attempts
     * @param feature   what comes back online, for the log line (e.g. "Multi-server teleports")
     */
    public NetworkSupervisor(ScheduledExecutorService scheduler, String feature) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    /**
     * Schedules {@code attempt} after the current delay and doubles the delay for the next time.
     * The attempt should call {@link #reset()} on success and {@code scheduleRetry} again on failure.
     *
     * @param attempt tries to bring the feature online; runs on the scheduler thread
     */
    public void scheduleRetry(Runnable attempt) {
        if (stopped) return;
        long delay = delaySeconds;
        LoggerUtils.logInfo(feature + " is offline; retrying in " + delay + "s.");
        scheduler.schedule(() -> {
            if (!stopped) attempt.run();
        }, delay, TimeUnit.SECONDS);
        delaySeconds = Math.min(delaySeconds * 2, MAX_DELAY.toSeconds());
    }

    /**
     * Resets the delay after a successful attempt.
     */
    public void reset() {
        delaySeconds = INITIAL_DELAY.toSeconds();
    }

    /**
     * Stops scheduling attempts.
     */
    public void stop() {
        stopped = true;
    }

    /**
     * Returns whether {@link #stop()} was called.
     *
     * @return {@code true} once stopped
     */
    public boolean isStopped() {
        return stopped;
    }
}
