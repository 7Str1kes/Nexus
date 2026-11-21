package org.glstudio.nexus.utils;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorsTest {

    @Test
    void everyWrittenFormIsRead() {
        assertEquals(Color.fromRGB(0x33CCFF), Colors.parse("#33ccff").orElseThrow());
        assertEquals(Color.fromARGB(0x80, 0, 0, 0), Colors.parse("#000000:128").orElseThrow());
        assertEquals(Color.fromARGB(0x40000000), Colors.parse("#40000000").orElseThrow());
        assertEquals(Color.fromRGB(10, 20, 255), Colors.parse("10, 20, 300").orElseThrow(), "channels are clamped");
        assertEquals(Color.fromRGB(0xFFAA00), Colors.parse("gold").orElseThrow(), "MiniMessage names");
        assertEquals(Color.fromRGB(0x00AAAA), Colors.parse("dark-aqua").orElseThrow());
        assertTrue(Colors.parse("sparkly").isEmpty());
        assertTrue(Colors.parse("#12345").isEmpty());
        assertTrue(Colors.parse(" ").isEmpty());
    }

    @Test
    void aPaletteComesFirst() {
        assertEquals(Color.AQUA, Colors.parse("AQUA", Colors.BUKKIT).orElseThrow(), "Bukkit's aqua, not MiniMessage's");
        assertEquals(Color.fromRGB(0x55FFFF), Colors.parse("aqua").orElseThrow());
        assertEquals(Color.fromRGB(1, 2, 3), Colors.parse("mine", Map.of("mine", Color.fromRGB(1, 2, 3))).orElseThrow());
        assertEquals(Color.fromRGB(0xFFAA00), Colors.parse("gold", Colors.BUKKIT).orElseThrow(), "then MiniMessage's");
    }

    @Test
    void writtenColoursReadBack() {
        Color translucent = Color.fromARGB(100, 1, 2, 3);
        assertEquals("#010203:100", Colors.format(translucent));
        assertEquals(translucent, Colors.parse(Colors.format(translucent)).orElseThrow());
        assertEquals("#FFAA00", Colors.format(Color.fromRGB(0xFFAA00)));
    }
}
