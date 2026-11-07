package org.glstudio.nexus.network;

import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Receives and sends raw messages over Redis: a pub/sub channel every server listens to, and an
 * optional per-server stream for messages addressed to one server, which survive that server being
 * briefly offline.
 *
 * <p>Both receivers run on their own daemon thread and reconnect with exponential backoff
 * (5 s doubling up to 60 s) after a connection loss. Received messages are passed to the consumer on
 * that thread.
 */
public final class PacketChannel {

    private static final long INITIAL_BACKOFF_MS = 5_000L;
    private static final long MAX_BACKOFF_MS = 60_000L;
    private static final String STREAM_FIELD = "p";
    private static final long STREAM_MAX_LENGTH = 2_000L;
    private static final int STREAM_BATCH = 32;
    private static final int STREAM_BLOCK_MS = 2_000;

    private final RedisConnector connector;
    private final String channel;
    private final String threadPrefix;

    private volatile boolean running;
    private volatile JedisPubSub subscription;
    private Thread subscriberThread;
    private Thread streamThread;

    /**
     * Creates a channel.
     *
     * @param connector    a connected Redis connector
     * @param channel      the pub/sub channel, also the prefix of stream keys
     * @param threadPrefix prefix of the receiver thread names, e.g. {@code GL-Teleports}
     */
    public PacketChannel(RedisConnector connector, String channel, String threadPrefix) {
        this.connector = Objects.requireNonNull(connector, "connector");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.threadPrefix = Objects.requireNonNull(threadPrefix, "threadPrefix");
    }

    /**
     * Starts listening on the pub/sub channel.
     *
     * @param onMessage receives each message, on the subscriber thread
     */
    public synchronized void subscribe(Consumer<String> onMessage) {
        if (subscriberThread != null) return;
        running = true;
        subscriberThread = new Thread(() -> {
            long backoff = INITIAL_BACKOFF_MS;
            while (running) {
                try {
                    connector.withJedis(jedis -> {
                        LoggerUtils.logInfo("Redis subscriber connected");
                        subscription = new JedisPubSub() {
                            @Override
                            public void onMessage(String ignored, String message) {
                                deliver(onMessage, message);
                            }
                        };
                        jedis.subscribe(subscription, channel);
                        return null;
                    });
                    backoff = INITIAL_BACKOFF_MS;
                } catch (Exception e) {
                    if (!running) break;
                    LoggerUtils.logWarn("Redis subscriber disconnected: " + e.getMessage()
                            + " (retrying in " + backoff / 1000 + "s)");
                }
                if (!running || !sleep(backoff)) break;
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }, threadPrefix + "-Redis-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    /**
     * Starts consuming this server's stream with a consumer group, acknowledging each entry after
     * handling it. New groups start after the last entry, so a fresh server does not replay history.
     *
     * @param serverName this server's name
     * @param group      the consumer group, e.g. {@code gl-teleports}
     * @param onMessage  receives each message, on the stream thread
     */
    public synchronized void consumeStream(String serverName, String group, Consumer<String> onMessage) {
        if (streamThread != null) return;
        running = true;
        String stream = streamKey(serverName);
        streamThread = new Thread(() -> {
            long backoff = INITIAL_BACKOFF_MS;
            while (running) {
                try {
                    connector.withJedis(jedis -> readLoop(jedis, stream, group, serverName, onMessage));
                    backoff = INITIAL_BACKOFF_MS;
                } catch (Exception e) {
                    if (!running) break;
                    LoggerUtils.logWarn("Redis stream consumer stopped: " + e.getMessage()
                            + " (retrying in " + backoff / 1000 + "s)");
                    if (!sleep(backoff)) break;
                    backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
                }
            }
        }, threadPrefix + "-Redis-Stream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private StreamEntryID readLoop(Jedis jedis, String stream, String group, String consumer, Consumer<String> onMessage) {
        ensureGroup(jedis, stream, group);
        // Start with this consumer's pending entries (left unacknowledged by a crash), then move on to
        // new ones once the backlog is empty.
        StreamEntryID cursor = new StreamEntryID(0, 0);
        while (running) {
            List<Map.Entry<String, List<StreamEntry>>> batches = jedis.xreadGroup(group, consumer,
                    XReadGroupParams.xReadGroupParams().count(STREAM_BATCH).block(STREAM_BLOCK_MS),
                    Map.of(stream, cursor));
            int handled = 0;
            if (batches != null) {
                for (Map.Entry<String, List<StreamEntry>> batch : batches) {
                    for (StreamEntry entry : batch.getValue()) {
                        handled++;
                        try {
                            String payload = entry.getFields().get(STREAM_FIELD);
                            if (payload != null) deliver(onMessage, payload);
                        } finally {
                            jedis.xack(stream, group, entry.getID());
                        }
                    }
                }
            }
            if (handled == 0 && !StreamEntryID.UNRECEIVED_ENTRY.equals(cursor)) {
                cursor = StreamEntryID.UNRECEIVED_ENTRY;
            }
        }
        return cursor;
    }

    private static void ensureGroup(Jedis jedis, String stream, String group) {
        try {
            jedis.xgroupCreate(stream, group, StreamEntryID.XGROUP_LAST_ENTRY, true);
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) throw e;
        }
    }

    /**
     * Publishes a message to every server.
     *
     * @param message the message
     * @return how many servers received it
     */
    public long publish(String message) {
        return connector.publish(channel, message);
    }

    /**
     * Appends a message to {@code destination}'s stream, trimmed to about 2000 entries.
     *
     * @param destination the target server
     * @param message     the message
     */
    public void sendTo(String destination, String message) {
        connector.withJedis(jedis -> jedis.xadd(streamKey(destination),
                XAddParams.xAddParams().maxLen(STREAM_MAX_LENGTH).approximateTrimming(),
                Map.of(STREAM_FIELD, message)));
    }

    /**
     * Returns the stream key of a server: {@code <channel>:stream:<server>}.
     *
     * @param server the server name
     * @return the key
     */
    public String streamKey(String server) {
        return channel + ":stream:" + server.toLowerCase(Locale.ROOT);
    }

    /**
     * Stops both receivers. Does not close the connector.
     */
    public synchronized void close() {
        running = false;
        JedisPubSub current = subscription;
        if (current != null && current.isSubscribed()) {
            try {
                current.unsubscribe();
            } catch (Exception ignored) {
                // Already disconnected: the thread exits on its own.
            }
        }
        if (subscriberThread != null) subscriberThread.interrupt();
        if (streamThread != null) streamThread.interrupt();
        subscriberThread = null;
        streamThread = null;
    }

    private void deliver(Consumer<String> onMessage, String message) {
        try {
            onMessage.accept(message);
        } catch (Exception e) {
            Diagnostics.logFailure("PacketChannel#deliver(" + channel + ")", e);
        }
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
