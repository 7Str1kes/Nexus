package org.glstudio.nexus.network;

import org.glstudio.nexus.utils.LoggerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drops packets that arrive twice or too late. Redis pub/sub may deliver a message more than once
 * during reconnects, and a packet delayed for long enough describes a state that no longer exists.
 */
public final class PacketDeduplicator {

    /** Default maximum age, in milliseconds. */
    public static final long DEFAULT_MAX_AGE_MS = 30_000L;

    private static final int PURGE_THRESHOLD = 512;

    private final long maxAgeMillis;
    private final Map<UUID, Long> seen = new ConcurrentHashMap<>();

    /**
     * Creates a deduplicator with the default 30 s maximum age.
     */
    public PacketDeduplicator() {
        this(DEFAULT_MAX_AGE_MS);
    }

    /**
     * Creates a deduplicator.
     *
     * @param maxAgeMillis packets older than this, in either direction, are dropped
     */
    public PacketDeduplicator(long maxAgeMillis) {
        this.maxAgeMillis = maxAgeMillis;
    }

    /**
     * Returns whether a packet should be handled: fresh and not seen before.
     *
     * @param packet the packet
     * @return {@code true} to handle it
     */
    public boolean accept(TrackedPacket packet) {
        if (!packet.isFresh(maxAgeMillis)) {
            LoggerUtils.logWarn("Discarded a " + packet.type() + " packet that is "
                    + Math.abs(System.currentTimeMillis() - packet.getSentAt()) / 1000
                    + "s out of date. Check that the servers' clocks agree.");
            return false;
        }
        purge();
        return seen.putIfAbsent(packet.getPacketId(), packet.getSentAt()) == null;
    }

    private void purge() {
        if (seen.size() < PURGE_THRESHOLD) return;
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        seen.values().removeIf(sentAt -> sentAt < cutoff);
    }
}
