package org.glstudio.nexus.network;

/**
 * A message exchanged between servers. Its fields are serialised to JSON by {@link PacketCodec};
 * methods are not, so implementing this interface does not change what travels on the wire.
 */
public interface NetworkPacket {

    /**
     * Returns the type written in the message header, used to pick the class when decoding.
     *
     * @return the type name, e.g. {@code CACHE_INVALIDATE}
     */
    String type();

    /**
     * Returns the server this packet is addressed to.
     *
     * @return the server name for a directed delivery, or {@code null} to broadcast
     */
    default String destinationServer() {
        return null;
    }
}
