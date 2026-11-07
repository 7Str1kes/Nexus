package org.glstudio.nexus.network;

import com.google.gson.Gson;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Function;

/**
 * Encodes packets as {@code TYPE:base64(json):signature}, where the signature is an HMAC-SHA256 of
 * {@code TYPE:base64(json)} with the secret shared by every server on the channel.
 *
 * <p>This is the wire format of every GL plugin since multi-server support was added, so servers
 * running older releases keep understanding newer ones. With an empty secret the signature is empty;
 * {@link NetworkSettings} refuses that unless insecure mode is allowed explicitly.
 *
 * @param <P> the plugin's packet type
 */
public final class PacketCodec<P extends NetworkPacket> {

    private static final String SEPARATOR = ":";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final Gson gson;
    private final Function<String, Class<? extends P>> types;

    /**
     * Creates a codec.
     *
     * @param gson  the JSON mapper
     * @param types maps a header type to its class, returning {@code null} for unknown types
     */
    public PacketCodec(Gson gson, Function<String, Class<? extends P>> types) {
        this.gson = Objects.requireNonNull(gson, "gson");
        this.types = Objects.requireNonNull(types, "types");
    }

    /**
     * Encodes and signs a packet.
     *
     * @param packet the packet
     * @param secret the shared secret, may be empty
     * @return the message to publish
     */
    public String encode(P packet, String secret) {
        String json = gson.toJson(packet);
        String payload = packet.type() + SEPARATOR
                + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return payload + SEPARATOR + sign(payload, secret);
    }

    /**
     * Verifies and decodes a message.
     *
     * @param message the received message
     * @param secret  the shared secret, may be empty
     * @return the packet
     * @throws SecurityException        if the signature does not match
     * @throws IllegalArgumentException if the message is malformed or its type unknown
     */
    public P decode(String message, String secret) {
        String[] parts = message.split(SEPARATOR, 3);
        if (parts.length != 3) throw new IllegalArgumentException("Malformed packet");

        String type = parts[0];
        String base64Json = parts[1];
        String signature = parts[2];

        String expected = sign(type + SEPARATOR + base64Json, secret);
        if (!constantTimeEquals(expected, signature)) {
            throw new SecurityException("Packet signature verification failed");
        }

        Class<? extends P> clazz = types.apply(type);
        if (clazz == null) throw new IllegalArgumentException("Unknown packet type " + type);
        String json = new String(Base64.getDecoder().decode(base64Json), StandardCharsets.UTF_8);
        return gson.fromJson(json, clazz);
    }

    /**
     * Reads a packet without checking its signature, for networks that opted out of signing while
     * some servers may still sign.
     *
     * @param message the wire text
     * @return the packet
     * @throws IllegalArgumentException if the text is malformed or the type unknown
     */
    public P decodeUnverified(String message) {
        String[] parts = message.split(SEPARATOR, 3);
        if (parts.length != 3) throw new IllegalArgumentException("Malformed packet");

        Class<? extends P> clazz = types.apply(parts[0]);
        if (clazz == null) throw new IllegalArgumentException("Unknown packet type " + parts[0]);
        return gson.fromJson(new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8), clazz);
    }

    /**
     * Signs {@code payload} with HMAC-SHA256.
     *
     * @param payload the text to sign
     * @param secret  the shared secret
     * @return the Base64 signature, empty for an empty secret
     */
    public static String sign(String payload, String secret) {
        if (secret == null || secret.isEmpty()) return "";
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign packet", e);
        }
    }

    /**
     * Compares two strings in time independent of where they differ, so a forged signature cannot
     * be guessed byte by byte.
     *
     * @param a the first string
     * @param b the second string
     * @return {@code true} if equal
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
