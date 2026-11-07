package org.glstudio.nexus.utils.serializer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the framing of {@link ItemCodec} with a backend that needs no server: slots are encoded
 * as a 4-byte count (every slot empty), so tests can count what comes back.
 */
class ItemCodecTest {

    private static final byte[] GARBAGE = "not an item payload".getBytes(StandardCharsets.UTF_8);

    private final ItemCodec codec = new ItemCodec(new CountingBackend());

    @Test
    void emptyPayloadIsAnIntactEmptyInventory() {
        ItemCodec.Decoded<ItemStack[]> decoded = codec.decodeArray("", 27);
        assertTrue(decoded.intact());
        assertEquals(ItemCodec.Format.EMPTY, decoded.format());
        assertEquals(27, decoded.value().length);

        assertTrue(codec.decodeArray(null, -1).intact());
        assertEquals(0, codec.decodeArray(null, -1).value().length);
        assertTrue(codec.decode(null).intact());
    }

    @Test
    void corruptPayloadIsNotIntact() {
        ItemCodec.Decoded<ItemStack[]> binary = codec.decodeArray(ItemCodec.PREFIX_BINARY
                + Base64.getEncoder().encodeToString(GARBAGE), 27);
        assertFalse(binary.intact());
        assertEquals(ItemCodec.Format.UNREADABLE, binary.format());
        assertEquals(27, binary.value().length);

        assertFalse(codec.decodeArray(ItemCodec.PREFIX_BINARY + "!!!not base64", 9).intact());
        assertFalse(codec.decodeArray(ItemCodec.PREFIX_GZIP + "AAAA", 9).intact());
        assertFalse(codec.decodeArray("just some text", 9).intact());
        assertFalse(codec.decode("just some text").intact());
        assertFalse(codec.decodeBytes(GARBAGE).intact());
    }

    @Test
    void binaryRoundTripKeepsTheSlotCount() {
        String plain = codec.encode(new ItemStack[5], false);
        String compressed = codec.encode(new ItemStack[5], true);
        assertTrue(plain.startsWith(ItemCodec.PREFIX_BINARY));
        assertTrue(compressed.startsWith(ItemCodec.PREFIX_GZIP));

        ItemCodec.Decoded<ItemStack[]> fromPlain = codec.decodeArray(plain, -1);
        ItemCodec.Decoded<ItemStack[]> fromCompressed = codec.decodeArray(compressed, -1);
        assertTrue(fromPlain.intact());
        assertTrue(fromCompressed.intact());
        assertEquals(ItemCodec.Format.BINARY, fromPlain.format());
        assertEquals(ItemCodec.Format.BINARY_GZIP, fromCompressed.format());
        assertEquals(5, fromPlain.value().length);
        assertEquals(5, fromCompressed.value().length);
        assertEquals("", codec.encode((ItemStack[]) null, false));
    }

    @Test
    void morePayloadSlotsThanExpectedIsReportedAsTruncated() {
        ItemCodec.Decoded<ItemStack[]> decoded = codec.decodeArray(codec.encode(new ItemStack[41], false), 36);
        assertFalse(decoded.intact());
        assertEquals(36, decoded.value().length);
    }

    @Test
    void bytesRoundTrip() {
        byte[] encoded = codec.encodeBytes(new ItemStack[3]);
        ItemCodec.Decoded<ItemStack[]> decoded = codec.decodeBytes(encoded);
        assertTrue(decoded.intact());
        assertEquals(3, decoded.value().length);
        assertArrayEquals(new byte[0], codec.encodeBytes(new ItemStack[0]));
    }

    @Test
    void legacyObjectStreamsAreRecognised() throws IOException {
        ByteArrayOutputStream array = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(array)) {
            out.writeInt(4);
            for (int i = 0; i < 4; i++) out.writeObject(null);
        }
        ItemCodec.Decoded<ItemStack[]> slots = codec.decodeArray(Base64.getEncoder().encodeToString(array.toByteArray()), -1);
        assertTrue(slots.intact());
        assertEquals(ItemCodec.Format.LEGACY_STREAM, slots.format());
        assertEquals(4, slots.value().length);

        ItemCodec.Decoded<ItemStack[]> raw = codec.decodeBytes(array.toByteArray());
        assertTrue(raw.intact());
        assertEquals(4, raw.value().length);

        ByteArrayOutputStream single = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(single)) {
            out.writeObject(null);
        }
        ItemCodec.Decoded<ItemStack> one = codec.decode(Base64.getEncoder().encodeToString(single.toByteArray()));
        assertTrue(one.intact());
        assertEquals(ItemCodec.Format.LEGACY_STREAM, one.format());
        assertNull(one.value());
    }

    @Test
    void legacyStreamWithImplausibleCountIsNotIntact() throws IOException {
        ByteArrayOutputStream array = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(array)) {
            out.writeInt(-1);
        }
        assertFalse(codec.decodeArray(Base64.getEncoder().encodeToString(array.toByteArray()), 9).intact());
    }

    @Test
    void encodingFailuresAreThrownNotStored() {
        ItemCodec failing = new ItemCodec(new CountingBackend() {
            @Override
            public byte[] encode(ItemStack[] items) {
                throw new IllegalStateException("broken item");
            }
        });
        assertThrows(ItemCodec.ItemCodecException.class, () -> failing.encode(new ItemStack[1], false));
    }

    private static class CountingBackend implements ItemCodec.Backend {
        @Override
        public byte[] encode(ItemStack[] items) {
            return ByteBuffer.allocate(4).putInt(items.length).array();
        }

        @Override
        public ItemStack[] decode(byte[] bytes) {
            if (bytes.length != 4) throw new IllegalArgumentException("Not a payload");
            return new ItemStack[ByteBuffer.wrap(bytes).getInt()];
        }

        @Override
        public ObjectInputStream objectStream(ByteArrayInputStream in) throws IOException {
            return new ObjectInputStream(in);
        }

        @Override
        public ItemStack yamlItem(YamlConfiguration yaml, String key) {
            return null;
        }
    }
}
