package org.glstudio.nexus.storage;

import java.util.Objects;

/**
 * HikariCP pool settings. The defaults are the values the GL plugins already used: 10 connections,
 * 30 s connection timeout, 30 min max lifetime.
 *
 * @param poolName           name shown in logs and thread dumps
 * @param maximumPoolSize    maximum connections
 * @param minimumIdle        idle connections kept open, {@code -1} for Hikari's default
 * @param connectionTimeout  milliseconds to wait for a connection
 * @param idleTimeout        milliseconds before an idle connection is closed, {@code -1} for default
 * @param maxLifetime        milliseconds before a connection is recycled
 * @param keepaliveTime      milliseconds between keepalive pings, {@code -1} for default
 * @param leakDetection      milliseconds before a held connection is reported, {@code 0} to disable
 * @param driverClassName    explicit driver class, {@code null} to let JDBC pick it
 */
public record PoolOptions(String poolName, int maximumPoolSize, int minimumIdle, long connectionTimeout,
                          long idleTimeout, long maxLifetime, long keepaliveTime, long leakDetection,
                          String driverClassName) {

    /**
     * Validates the options.
     *
     * @param poolName          name shown in logs
     * @param maximumPoolSize   maximum connections
     * @param minimumIdle       idle connections kept open
     * @param connectionTimeout milliseconds to wait for a connection
     * @param idleTimeout       milliseconds before an idle connection is closed
     * @param maxLifetime       milliseconds before a connection is recycled
     * @param keepaliveTime     milliseconds between keepalive pings
     * @param leakDetection     milliseconds before a held connection is reported
     * @param driverClassName   explicit driver class, may be {@code null}
     */
    public PoolOptions {
        Objects.requireNonNull(poolName, "poolName");
        maximumPoolSize = Math.max(1, maximumPoolSize);
    }

    /**
     * Returns the default options with {@code poolName}.
     *
     * @param poolName the pool name, e.g. {@code GL-Teleports-MySQL}
     * @return the options
     */
    public static PoolOptions named(String poolName) {
        return new PoolOptions(poolName, 10, -1, 30_000L, -1, 1_800_000L, -1, 0, null);
    }

    /**
     * Returns a copy with another maximum pool size.
     *
     * @param size the size, at least 1
     * @return the new options
     */
    public PoolOptions maximumPoolSize(int size) {
        return new PoolOptions(poolName, size, minimumIdle, connectionTimeout, idleTimeout, maxLifetime,
                keepaliveTime, leakDetection, driverClassName);
    }

    /**
     * Returns a copy with another minimum idle count.
     *
     * @param idle the count, {@code -1} for Hikari's default
     * @return the new options
     */
    public PoolOptions minimumIdle(int idle) {
        return new PoolOptions(poolName, maximumPoolSize, idle, connectionTimeout, idleTimeout, maxLifetime,
                keepaliveTime, leakDetection, driverClassName);
    }

    /**
     * Returns a copy with other timeouts.
     *
     * @param connectionTimeout milliseconds to wait for a connection
     * @param idleTimeout       milliseconds before an idle connection is closed, {@code -1} for default
     * @param maxLifetime       milliseconds before a connection is recycled
     * @param keepaliveTime     milliseconds between keepalive pings, {@code -1} for default
     * @return the new options
     */
    public PoolOptions timeouts(long connectionTimeout, long idleTimeout, long maxLifetime, long keepaliveTime) {
        return new PoolOptions(poolName, maximumPoolSize, minimumIdle, connectionTimeout, idleTimeout, maxLifetime,
                keepaliveTime, leakDetection, driverClassName);
    }

    /**
     * Returns a copy with leak detection.
     *
     * @param millis threshold, {@code 0} to disable
     * @return the new options
     */
    public PoolOptions leakDetection(long millis) {
        return new PoolOptions(poolName, maximumPoolSize, minimumIdle, connectionTimeout, idleTimeout, maxLifetime,
                keepaliveTime, millis, driverClassName);
    }

    /**
     * Returns a copy with an explicit driver class.
     *
     * @param className the driver, e.g. {@code com.mysql.cj.jdbc.Driver}
     * @return the new options
     */
    public PoolOptions driver(String className) {
        return new PoolOptions(poolName, maximumPoolSize, minimumIdle, connectionTimeout, idleTimeout, maxLifetime,
                keepaliveTime, leakDetection, className);
    }
}
