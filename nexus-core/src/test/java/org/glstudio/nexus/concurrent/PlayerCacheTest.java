package org.glstudio.nexus.concurrent;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCacheTest {

    // Tasks are only needed to bring loads back to the main thread.
    private final PlayerCache<Integer> cache = new PlayerCache<>(null);

    @Test
    void updateChangesOnlyLoadedPlayers() {
        UUID loaded = UUID.randomUUID();
        UUID away = UUID.randomUUID();
        cache.put(loaded, 1);
        cache.update(loaded, wins -> wins + 1);
        cache.update(away, wins -> wins + 1);
        assertEquals(2, cache.get(loaded).orElseThrow());
        assertFalse(cache.isLoaded(away), "an update does not load anyone");
    }

    @Test
    void unloadGivesTheDataBackOnce() {
        UUID player = UUID.randomUUID();
        cache.put(player, 7);
        assertEquals(Set.of(player), cache.ids());
        assertEquals(7, cache.unload(player));
        assertNull(cache.unload(player));
        assertEquals(3, cache.getOrDefault(player, 3));
        assertTrue(cache.values().isEmpty());
    }
}
