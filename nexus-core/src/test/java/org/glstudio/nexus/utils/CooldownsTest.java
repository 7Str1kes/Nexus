package org.glstudio.nexus.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownsTest {

    @Test
    void aCooldownBlocksUntilItEnds() throws InterruptedException {
        Cooldowns cooldowns = new Cooldowns();
        UUID player = UUID.randomUUID();
        assertTrue(cooldowns.tryUse(player, "preview", 60));
        assertFalse(cooldowns.tryUse(player, "preview", 60));
        assertTrue(cooldowns.tryUse(player, "other", 60), "keys are apart");
        assertTrue(cooldowns.tryUse(UUID.randomUUID(), "preview", 60), "players are apart");
        Thread.sleep(80);
        assertTrue(cooldowns.tryUse(player, "preview", 60));
    }

    @Test
    void cooldownsOfOneLengthNeedNoLengthEachTime() {
        Cooldowns throttle = new Cooldowns(Duration.ofMinutes(1));
        UUID player = UUID.randomUUID();
        assertTrue(throttle.tryUse(player, "message"));
        assertFalse(throttle.tryUse(player, "message"));
        assertTrue(throttle.remainingMillis(player, "message") > 59_000);
        assertTrue(throttle.tryUse(player, "other"), "keys are apart");
    }

    @Test
    void remainingClearAndForget() {
        Cooldowns cooldowns = new Cooldowns();
        UUID player = UUID.randomUUID();
        assertEquals(0, cooldowns.remainingMillis(player, "x"));
        cooldowns.start(player, "x", Duration.ofMinutes(1));
        assertTrue(cooldowns.remainingMillis(player, "x") > 59_000);
        assertTrue(cooldowns.isCooling(player, "x"));
        cooldowns.clear(player, "x");
        assertFalse(cooldowns.isCooling(player, "x"));
        cooldowns.start(player, "y", Duration.ofMinutes(1));
        cooldowns.clearKey("y");
        assertFalse(cooldowns.isCooling(player, "y"));
        cooldowns.start(player, "z", Duration.ofMinutes(1));
        cooldowns.forget(player);
        assertFalse(cooldowns.isCooling(player, "z"));
    }
}
