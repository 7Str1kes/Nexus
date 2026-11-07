package org.glstudio.nexus.network;

import org.glstudio.nexus.utils.LoggerUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.params.SetParams;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Owns the Redis connection pool of a plugin, plus the key-value operations the plugins share.
 *
 * <p>Every operation borrows a connection and returns it, and is blocking: call them off the main
 * thread.
 */
public final class RedisConnector {

    private static final int TIMEOUT_MS = 2_000;

    private final NetworkSettings settings;
    private final int maxConnections;
    private final int maxIdle;
    private final int minIdle;
    private final int timeoutMs;
    private volatile JedisPool pool;

    /**
     * Creates a connector; nothing connects until {@link #connect(String)}.
     *
     * @param settings       the network settings
     * @param maxConnections pool size, e.g. 16
     */
    public RedisConnector(NetworkSettings settings, int maxConnections) {
        this(settings, Math.max(2, maxConnections), Math.max(1, Math.max(2, maxConnections) / 2), 2, TIMEOUT_MS);
    }

    /**
     * Creates a connector with its own pool limits, for plugins that let the administrator tune them.
     *
     * @param settings       the network settings
     * @param maxConnections maximum connections
     * @param maxIdle        maximum idle connections
     * @param minIdle        minimum idle connections
     * @param timeoutMs      connection and socket timeout in milliseconds
     */
    public RedisConnector(NetworkSettings settings, int maxConnections, int maxIdle, int minIdle, int timeoutMs) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.maxConnections = maxConnections;
        this.maxIdle = maxIdle;
        this.minIdle = minIdle;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Opens the pool and pings Redis. Blocking.
     *
     * <p>Refuses a Redis without password, and a secret shorter than
     * {@value NetworkSettings#MINIMUM_SECRET_LENGTH} characters, unless insecure mode is allowed;
     * without a secret any client able to publish to Redis could forge packets.
     *
     * @param secretUse what a forged packet could do, for the error message (e.g. "move players")
     * @throws IllegalStateException if the settings are insecure
     * @throws RuntimeException      if Redis cannot be reached
     */
    public void connect(String secretUse) {
        connect(secretUse, true);
    }

    /**
     * Connects like {@link #connect(String)}, optionally accepting a weak secret with only a warning.
     * For plugins whose releases already allowed that: refusing it now would take working networks
     * offline on update. New code should use {@link #connect(String)}.
     *
     * @param secretUse     what a forged packet could do, used in the error or warning
     * @param requireSecret {@code false} to warn about a weak secret instead of refusing it
     * @throws IllegalStateException if the settings are refused
     */
    public void connect(String secretUse, boolean requireSecret) {
        if (settings.password().isEmpty() && !settings.allowInsecure()) {
            throw new IllegalStateException("Redis has no password. Set the Redis password, or allow-insecure: "
                    + "true to override (not recommended).");
        }
        if (!settings.hasStrongSecret()) {
            if (requireSecret && !settings.allowInsecure()) {
                throw new IllegalStateException("The multi-server secret must be at least "
                        + NetworkSettings.MINIMUM_SECRET_LENGTH + " characters and shared by every server on the "
                        + "channel; without it any client that can publish to Redis could " + secretUse
                        + ". Set it, or allow-insecure: true to override (not recommended).");
            }
            LoggerUtils.logWarn("The multi-server secret is empty or too short; packets are unsigned (anyone "
                    + "able to publish to Redis could " + secretUse + ").");
        }

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(maxConnections);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);

        JedisPool created = settings.password().isEmpty()
                ? new JedisPool(config, settings.host(), settings.port(), timeoutMs)
                : new JedisPool(config, settings.host(), settings.port(), timeoutMs, settings.password());
        try (Jedis jedis = created.getResource()) {
            jedis.ping();
        } catch (RuntimeException e) {
            created.close();
            throw e;
        }
        this.pool = created;
        LoggerUtils.logSuccess("Redis connection established");
    }

    /**
     * Returns whether the pool is open.
     *
     * @return {@code true} after a successful {@link #connect} and before {@link #close()}
     */
    public boolean isConnected() {
        JedisPool current = pool;
        return current != null && !current.isClosed();
    }

    /**
     * Returns the settings.
     *
     * @return the settings
     */
    public NetworkSettings settings() {
        return settings;
    }

    /**
     * Borrows a connection for several commands; closing it gives it back.
     *
     * @return the connection
     * @throws IllegalStateException if not connected
     */
    public Jedis resource() {
        JedisPool current = pool;
        if (current == null || current.isClosed()) throw new IllegalStateException("Redis is not connected");
        return current.getResource();
    }

    /**
     * Runs {@code work} with a pooled connection.
     *
     * @param work the work
     * @param <T>  the result type
     * @return what the work returned
     * @throws IllegalStateException if not connected
     */
    public <T> T withJedis(Function<Jedis, T> work) {
        JedisPool current = pool;
        if (current == null || current.isClosed()) throw new IllegalStateException("Redis is not connected");
        try (Jedis jedis = current.getResource()) {
            return work.apply(jedis);
        }
    }

    /**
     * Publishes a message.
     *
     * @param channel the channel
     * @param message the message
     * @return how many subscribers received it
     */
    public long publish(String channel, String message) {
        return withJedis(jedis -> jedis.publish(channel, message));
    }

    /**
     * Sets a value with an expiry.
     *
     * @param key        the key
     * @param value      the value
     * @param ttlSeconds expiry in seconds
     */
    public void set(String key, String value, long ttlSeconds) {
        withJedis(jedis -> jedis.setex(key, ttlSeconds, value));
    }

    /**
     * Sets a value with an expiry only if the key does not exist; the basis of a distributed lock.
     *
     * @param key       the key
     * @param value     the value, typically the owner
     * @param ttlMillis expiry in milliseconds
     * @return {@code true} if the key was set
     */
    public boolean setIfAbsent(String key, String value, long ttlMillis) {
        return "OK".equals(withJedis(jedis -> jedis.set(key, value, SetParams.setParams().nx().px(ttlMillis))));
    }

    /**
     * Reads a value.
     *
     * @param key the key
     * @return the value, or {@code null}
     */
    public String get(String key) {
        return withJedis(jedis -> jedis.get(key));
    }

    /**
     * Reads several values.
     *
     * @param keys the keys
     * @return the values in key order, {@code null} where missing
     */
    public List<String> getAll(List<String> keys) {
        if (keys.isEmpty()) return List.of();
        return withJedis(jedis -> jedis.mget(keys.toArray(new String[0])));
    }

    /**
     * Reads and deletes a value atomically.
     *
     * @param key the key
     * @return the value, or {@code null}
     */
    public String getAndDelete(String key) {
        return withJedis(jedis -> jedis.getDel(key));
    }

    /**
     * Deletes a key.
     *
     * @param key the key
     */
    public void delete(String key) {
        withJedis(jedis -> jedis.del(key));
    }

    /**
     * Deletes several keys.
     *
     * @param keys the keys
     */
    public void deleteAll(Collection<String> keys) {
        if (keys.isEmpty()) return;
        withJedis(jedis -> jedis.del(keys.toArray(new String[0])));
    }

    /**
     * Deletes a key only if it still holds {@code expected}; releases a lock without stealing one
     * re-acquired by someone else.
     *
     * @param key      the key
     * @param expected the value it must hold
     */
    public void deleteIfValueIs(String key, String expected) {
        withJedis(jedis -> jedis.eval(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
                List.of(key), List.of(expected)));
    }

    /**
     * Deletes a key only if its value ends with {@code ";" + owner}.
     *
     * @param key   the key
     * @param owner the owner suffix
     */
    public void deleteIfOwnedBy(String key, String owner) {
        withJedis(jedis -> jedis.eval(
                "local v = redis.call('GET', KEYS[1]) "
                        + "if v and string.sub(v, -string.len(ARGV[1])) == ARGV[1] then "
                        + "return redis.call('DEL', KEYS[1]) end return 0",
                List.of(key), List.of(";" + owner)));
    }

    /**
     * Returns the remaining time to live of a key.
     *
     * @param key the key
     * @return seconds, or {@code -1} if the key is missing or has no expiry
     */
    public long remainingTtl(String key) {
        long ttl = withJedis(jedis -> jedis.ttl(key));
        return ttl > 0 ? ttl : -1L;
    }

    /**
     * Returns the remaining time to live of several keys in one round trip.
     *
     * @param keys the keys
     * @return seconds by key, only for keys that exist and expire
     */
    public Map<String, Long> remainingTtls(Collection<String> keys) {
        if (keys.isEmpty()) return Map.of();
        return withJedis(jedis -> {
            Pipeline pipeline = jedis.pipelined();
            Map<String, Response<Long>> responses = new LinkedHashMap<>();
            for (String key : keys) responses.put(key, pipeline.ttl(key));
            pipeline.sync();
            Map<String, Long> found = new HashMap<>();
            responses.forEach((key, response) -> {
                Long ttl = response.get();
                if (ttl != null && ttl > 0) found.put(key, ttl);
            });
            return found;
        });
    }

    /**
     * Adds a member to a set.
     *
     * @param key    the set
     * @param member the member
     */
    public void addToSet(String key, String member) {
        withJedis(jedis -> jedis.sadd(key, member));
    }

    /**
     * Removes a member from a set.
     *
     * @param key    the set
     * @param member the member
     */
    public void removeFromSet(String key, String member) {
        withJedis(jedis -> jedis.srem(key, member));
    }

    /**
     * Returns the members of a set.
     *
     * @param key the set
     * @return the members
     */
    public Set<String> membersOf(String key) {
        return withJedis(jedis -> jedis.smembers(key));
    }

    /**
     * Closes the pool. Safe to call more than once.
     */
    public void close() {
        JedisPool current = pool;
        pool = null;
        if (current != null && !current.isClosed()) current.close();
    }
}
