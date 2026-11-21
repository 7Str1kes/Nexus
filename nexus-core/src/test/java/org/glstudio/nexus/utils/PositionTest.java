package org.glstudio.nexus.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionTest {

    @Test
    void writtenPositionsReadBack() {
        Position position = new Position("world", 1.5, -59, -17.25, 90, -10);
        YamlConfiguration yaml = new YamlConfiguration();
        position.write(yaml, "location");
        assertEquals(position, Position.read(yaml.getConfigurationSection("location")));
        assertNull(Position.read(null));
    }

    @Test
    void movingAndChunks() {
        Position position = new Position("world", -0.5, 64, 31.9, 0, 0);
        assertEquals(-1, position.chunkX());
        assertEquals(1, position.chunkZ());
        assertEquals(new Position("world", 1.5, 65, 31.9, 0, 0), position.add(2, 1, 0));
        assertEquals(45.1f, position.withRotation(45.12f, 0).yaw());
    }

    @Test
    void timeZones() {
        assertEquals(ZoneId.of("Europe/Madrid"), TimeZones.tryParse("Europe/Madrid").orElseThrow());
        assertEquals(ZoneId.systemDefault(), TimeZones.tryParse(" ").orElseThrow());
        assertTrue(TimeZones.tryParse("Mars/Base").isEmpty());
        StringBuilder warned = new StringBuilder();
        assertEquals(ZoneId.systemDefault(), TimeZones.parse("Mars/Base", warned::append));
        assertTrue(warned.toString().contains("Mars/Base"));
    }
}
