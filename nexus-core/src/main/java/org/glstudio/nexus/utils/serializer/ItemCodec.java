package org.glstudio.nexus.utils.serializer;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.glstudio.nexus.utils.Diagnostics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Encodes item stacks for storage and reads back every format the GL plugins have written.
 *
 * <p>Written formats:
 * <ul>
 *   <li>{@code v2:} + Base64 of Paper's item codec ({@code ItemStack#serializeItemsAsBytes}), which
 *       carries Minecraft's data version so the server can upgrade old stacks after an update.</li>
 *   <li>{@code v2z:} + Base64 of the GZIP-compressed {@code v2:} text.</li>
 *   <li>Legacy: Base64 of a Bukkit object stream ({@link #encodeLegacy(ItemStack[])}), the format of
 *       Nexus 1.x {@link ItemSerialization}, kept for plugins that must stay readable by their older
 *       releases.</li>
 * </ul>
 *
 * <p>Every read reports whether it was complete through {@link Decoded#intact()}. An empty payload
 * is an intact empty inventory; a payload that cannot be read is <em>not</em>, so callers can refuse
 * to overwrite stored data with nothing.
 */
public final class ItemCodec {

    /** Prefix of the binary format. */
    public static final String PREFIX_BINARY = "v2:";
    /** Prefix of the compressed binary format. */
    public static final String PREFIX_GZIP = "v2z:";

    private static final byte[] BYTES_MAGIC = {'N', 'X', '2'};
    private static final int STREAM_MAGIC_0 = 0xAC;
    private static final int STREAM_MAGIC_1 = 0xED;
    private static final int TC_BLOCKDATA = 0x77;
    private static final int MAX_LEGACY_SLOTS = 54 * 100;

    private static final ItemCodec PAPER = new ItemCodec(new PaperBackend());

    private final Backend backend;

    /**
     * Creates a codec over a custom backend, for tests.
     *
     * @param backend the item (de)serialisation primitives
     */
    public ItemCodec(Backend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /**
     * Returns the codec backed by the running Paper server.
     *
     * @return the shared codec
     */
    public static ItemCodec paper() {
        return PAPER;
    }

    /**
     * Which format a payload was read from.
     */
    public enum Format {
        /** Nothing stored. */
        EMPTY,
        /** {@code v2:}. */
        BINARY,
        /** {@code v2z:}. */
        BINARY_GZIP,
        /** Bukkit object stream, Base64 or raw bytes. */
        LEGACY_STREAM,
        /** YAML document with one key per slot. */
        LEGACY_YAML,
        /** The payload could not be recognised. */
        UNREADABLE
    }

    /**
     * The outcome of a read.
     *
     * @param value  what could be read; empty slots are {@code null}
     * @param intact {@code false} if any part of the payload could not be read
     * @param format the format detected
     * @param <T>    {@code ItemStack} or {@code ItemStack[]}
     */
    public record Decoded<T>(T value, boolean intact, Format format) {
    }

    // ─── write ───────────────────────────────────────────────────────────────────

    /**
     * Encodes slots in the binary format. Empty slots are kept so the layout survives.
     *
     * @param items    the slots; {@code null} or a zero-length array encodes as {@code ""}
     * @param compress {@code true} for {@code v2z:}
     * @return the payload
     * @throws ItemCodecException if the stacks cannot be encoded; nothing broken is ever returned
     */
    public String encode(ItemStack[] items, boolean compress) {
        if (items == null || items.length == 0) return "";
        String encoded = Base64.getEncoder().encodeToString(binary(items));
        return compress ? PREFIX_GZIP + gzip(encoded) : PREFIX_BINARY + encoded;
    }

    /**
     * Encodes one stack in the binary format.
     *
     * @param item     the stack; {@code null} or air encodes as {@code ""}
     * @param compress {@code true} for {@code v2z:}
     * @return the payload
     * @throws ItemCodecException if the stack cannot be encoded
     */
    public String encode(ItemStack item, boolean compress) {
        if (item == null || item.getType().isAir()) return "";
        return encode(new ItemStack[]{item}, compress);
    }

    /**
     * Encodes slots as raw bytes for binary columns.
     *
     * @param items the slots; {@code null} or a zero-length array encodes as an empty array
     * @return the bytes
     * @throws ItemCodecException if the stacks cannot be encoded
     */
    public byte[] encodeBytes(ItemStack[] items) {
        if (items == null || items.length == 0) return new byte[0];
        byte[] payload = binary(items);
        byte[] result = Arrays.copyOf(BYTES_MAGIC, BYTES_MAGIC.length + payload.length);
        System.arraycopy(payload, 0, result, BYTES_MAGIC.length, payload.length);
        return result;
    }

    /**
     * Encodes slots in the legacy Base64 object-stream format.
     *
     * @param items the slots
     * @return the payload, {@code ""} for {@code null}
     * @throws ItemCodecException if the stacks cannot be encoded
     */
    public String encodeLegacy(ItemStack[] items) {
        if (items == null) return "";
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeInt(items.length);
                for (ItemStack item : items) out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            throw new ItemCodecException("Failed to serialize " + items.length + " item(s)", e);
        }
    }

    /**
     * Encodes one stack in the legacy Base64 object-stream format.
     *
     * @param item the stack
     * @return the payload, {@code ""} for {@code null}
     * @throws ItemCodecException if the stack cannot be encoded
     */
    public String encodeLegacy(ItemStack item) {
        if (item == null) return "";
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            throw new ItemCodecException("Failed to serialize an item", e);
        }
    }

    private byte[] binary(ItemStack[] items) {
        try {
            return backend.encode(items);
        } catch (Exception e) {
            // Refuse to store a payload already known to be broken: read back it would look like an
            // empty inventory.
            throw new ItemCodecException("Failed to serialize " + items.length + " item(s)", e);
        }
    }

    // ─── read ────────────────────────────────────────────────────────────────────

    /**
     * Reads slots from any known text format.
     *
     * @param data         the payload, may be {@code null}
     * @param expectedSize the slot count the caller needs, or {@code -1} for whatever was stored;
     *                     a payload with more slots is truncated and reported as not intact
     * @return the slots and whether they were fully read
     */
    public Decoded<ItemStack[]> decodeArray(String data, int expectedSize) {
        if (data == null || data.isEmpty()) {
            return new Decoded<>(new ItemStack[Math.max(0, expectedSize)], true, Format.EMPTY);
        }
        try {
            if (data.startsWith(PREFIX_GZIP)) {
                return fit(backend.decode(base64(gunzip(data.substring(PREFIX_GZIP.length())))),
                        expectedSize, Format.BINARY_GZIP);
            }
            if (data.startsWith(PREFIX_BINARY)) {
                return fit(backend.decode(base64(data.substring(PREFIX_BINARY.length()))),
                        expectedSize, Format.BINARY);
            }
            byte[] raw = tryBase64(data);
            if (raw != null && isObjectStream(raw)) {
                return fit(readLegacyStream(raw), expectedSize, Format.LEGACY_STREAM);
            }
            return legacyYaml(data, expectedSize);
        } catch (Exception e) {
            Diagnostics.logFailure("ItemCodec#decodeArray", e);
            return new Decoded<>(new ItemStack[Math.max(0, expectedSize)], false, Format.UNREADABLE);
        }
    }

    /**
     * Reads one stack from any known text format.
     *
     * @param data the payload, may be {@code null}
     * @return the stack ({@code null} when empty) and whether it was read
     */
    public Decoded<ItemStack> decode(String data) {
        if (data == null || data.isEmpty()) return new Decoded<>(null, true, Format.EMPTY);

        if (data.startsWith(PREFIX_GZIP) || data.startsWith(PREFIX_BINARY)) {
            Decoded<ItemStack[]> slots = decodeArray(data, -1);
            ItemStack first = slots.value().length == 0 ? null : slots.value()[0];
            return new Decoded<>(first, slots.intact(), slots.format());
        }
        byte[] raw = tryBase64(data);
        if (raw != null && isObjectStream(raw)) {
            try {
                ItemStack[] stacks = readLegacyStream(raw);
                return new Decoded<>(stacks.length == 0 ? null : stacks[0], true, Format.LEGACY_STREAM);
            } catch (Exception e) {
                Diagnostics.logFailure("ItemCodec#decode", e);
                return new Decoded<>(null, false, Format.UNREADABLE);
            }
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(data);
            ItemStack item = backend.yamlItem(yaml, "item");
            boolean intact = item != null || !yaml.contains("item");
            return new Decoded<>(item, intact, intact ? Format.LEGACY_YAML : Format.UNREADABLE);
        } catch (Exception e) {
            Diagnostics.logFailure("ItemCodec#decode", e);
            return new Decoded<>(null, false, Format.UNREADABLE);
        }
    }

    /**
     * Reads slots from raw bytes: {@link #encodeBytes} output or a raw Bukkit object stream.
     *
     * @param data the bytes, may be {@code null}
     * @return the slots and whether they were read
     */
    public Decoded<ItemStack[]> decodeBytes(byte[] data) {
        if (data == null || data.length == 0) return new Decoded<>(new ItemStack[0], true, Format.EMPTY);
        try {
            if (startsWith(data, BYTES_MAGIC)) {
                return new Decoded<>(backend.decode(Arrays.copyOfRange(data, BYTES_MAGIC.length, data.length)),
                        true, Format.BINARY);
            }
            if (isObjectStream(data)) {
                return new Decoded<>(readLegacyStream(data), true, Format.LEGACY_STREAM);
            }
            return new Decoded<>(new ItemStack[0], false, Format.UNREADABLE);
        } catch (Exception e) {
            Diagnostics.logFailure("ItemCodec#decodeBytes", e);
            return new Decoded<>(new ItemStack[0], false, Format.UNREADABLE);
        }
    }

    private Decoded<ItemStack[]> fit(ItemStack[] decoded, int expectedSize, Format format) {
        if (expectedSize < 0) return new Decoded<>(decoded, true, format);
        ItemStack[] target = new ItemStack[expectedSize];
        System.arraycopy(decoded, 0, target, 0, Math.min(decoded.length, expectedSize));
        return new Decoded<>(target, decoded.length <= expectedSize, format);
    }

    private ItemStack[] readLegacyStream(byte[] raw) throws Exception {
        // Byte 4 is the first content tag after the stream header: block data means the array
        // format (an int length first), an object tag means a single stack.
        boolean array = raw.length > 4 && (raw[4] & 0xFF) == TC_BLOCKDATA;
        try (ObjectInputStream in = backend.objectStream(new ByteArrayInputStream(raw))) {
            if (!array) return new ItemStack[]{(ItemStack) in.readObject()};
            int size = in.readInt();
            if (size < 0 || size > MAX_LEGACY_SLOTS) {
                throw new IOException("Implausible slot count " + size);
            }
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) items[i] = (ItemStack) in.readObject();
            return items;
        }
    }

    private Decoded<ItemStack[]> legacyYaml(String data, int expectedSize) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(data);
        } catch (Exception e) {
            Diagnostics.logFailure("ItemCodec#legacyYaml", e);
            return new Decoded<>(new ItemStack[Math.max(0, expectedSize)], false, Format.UNREADABLE);
        }
        int size = expectedSize;
        if (size < 0) {
            size = 0;
            for (String key : yaml.getKeys(false)) size = Math.max(size, parseSlot(key) + 1);
        }
        ItemStack[] target = new ItemStack[size];
        boolean intact = true;
        boolean anySlot = false;
        for (String key : yaml.getKeys(false)) {
            int index = parseSlot(key);
            if (index < 0) continue;
            anySlot = true;
            if (index >= target.length) {
                intact = false;
                continue;
            }
            ItemStack item = backend.yamlItem(yaml, key);
            if (item == null) {
                intact = false;
            } else {
                target[index] = item;
            }
        }
        if (!anySlot && !yaml.getKeys(false).isEmpty()) intact = false;
        return new Decoded<>(target, intact, intact ? Format.LEGACY_YAML : Format.UNREADABLE);
    }

    private static int parseSlot(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isObjectStream(byte[] raw) {
        return raw.length > 4 && (raw[0] & 0xFF) == STREAM_MAGIC_0 && (raw[1] & 0xFF) == STREAM_MAGIC_1;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static byte[] tryBase64(String data) {
        try {
            return Base64.getDecoder().decode(data.trim());
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }

    private static byte[] base64(String data) {
        return Base64.getDecoder().decode(data);
    }

    private static String gzip(String encoded) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(encoded.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new ItemCodecException("Failed to compress an item payload", e);
        }
    }

    private static String gunzip(String base64) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64)))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The primitives that need a running server; replaceable in tests.
     */
    public interface Backend {

        /**
         * Encodes slots with Paper's item codec.
         *
         * @param items the slots, {@code null} for empty
         * @return the bytes
         * @throws Exception if a stack cannot be encoded
         */
        byte[] encode(ItemStack[] items) throws Exception;

        /**
         * Decodes bytes produced by {@link #encode(ItemStack[])}.
         *
         * @param bytes the bytes
         * @return the slots, {@code null} for empty
         * @throws Exception if the bytes are not a valid payload
         */
        ItemStack[] decode(byte[] bytes) throws Exception;

        /**
         * Opens an object stream able to read Bukkit-serialised stacks.
         *
         * @param in the raw stream
         * @return the object stream
         * @throws IOException if the stream header is invalid
         */
        ObjectInputStream objectStream(ByteArrayInputStream in) throws IOException;

        /**
         * Reads one stack from a legacy YAML document.
         *
         * @param yaml the document
         * @param key  the key of the stack
         * @return the stack, or {@code null} if it cannot be read
         */
        ItemStack yamlItem(YamlConfiguration yaml, String key);
    }

    /**
     * Thrown when stacks cannot be encoded, so the write is aborted instead of storing junk.
     */
    public static final class ItemCodecException extends RuntimeException {

        /**
         * Creates the exception.
         *
         * @param message what failed
         * @param cause   the underlying failure
         */
        public ItemCodecException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class PaperBackend implements Backend {

        @Override
        public byte[] encode(ItemStack[] items) {
            // The codec rejects nulls: empty slots travel as air and come back as null.
            ItemStack[] normalised = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                normalised[i] = items[i] == null ? new ItemStack(Material.AIR) : items[i];
            }
            return ItemStack.serializeItemsAsBytes(normalised);
        }

        @Override
        public ItemStack[] decode(byte[] bytes) {
            ItemStack[] decoded = ItemStack.deserializeItemsFromBytes(bytes);
            for (int i = 0; i < decoded.length; i++) {
                if (decoded[i] != null && decoded[i].getType().isAir()) decoded[i] = null;
            }
            return decoded;
        }

        @Override
        @SuppressWarnings("deprecation")
        public ObjectInputStream objectStream(ByteArrayInputStream in) throws IOException {
            return new BukkitObjectInputStream(in);
        }

        @Override
        public ItemStack yamlItem(YamlConfiguration yaml, String key) {
            return yaml.getItemStack(key);
        }
    }
}
