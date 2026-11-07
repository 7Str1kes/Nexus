package org.glstudio.nexus.network;

import java.util.UUID;

/**
 * A packet with an id and a send time, so {@link PacketDeduplicator} can drop duplicates and
 * packets that arrive too late to still mean anything.
 */
public interface TrackedPacket extends NetworkPacket {

    /**
     * Returns the unique id of this packet.
     *
     * @return the id
     */
    UUID getPacketId();

    /**
     * Returns when the packet was created.
     *
     * @return epoch milliseconds on the sending server
     */
    long getSentAt();

    /**
     * Returns whether the packet was sent within {@code toleranceMillis} of now, in either direction
     * (clocks between servers drift).
     *
     * @param toleranceMillis the allowed age
     * @return {@code true} if fresh
     */
    default boolean isFresh(long toleranceMillis) {
        return Math.abs(System.currentTimeMillis() - getSentAt()) <= toleranceMillis;
    }
}
