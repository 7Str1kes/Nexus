package org.glstudio.nexus.network;

import java.util.UUID;

/**
 * Base class for tracked packets. The field names {@code packetId} and {@code sentAt} are part of
 * the wire format shared with older releases of the GL plugins and must not change.
 */
public abstract class AbstractPacket implements TrackedPacket {

    private final UUID packetId = UUID.randomUUID();
    private final long sentAt = System.currentTimeMillis();

    @Override
    public UUID getPacketId() {
        return packetId;
    }

    @Override
    public long getSentAt() {
        return sentAt;
    }
}
