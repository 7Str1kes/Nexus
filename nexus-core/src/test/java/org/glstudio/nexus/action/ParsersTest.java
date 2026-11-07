package org.glstudio.nexus.action;

import org.bukkit.configuration.file.YamlConfiguration;
import org.glstudio.nexus.modules.menu.config.MenuConfigUtils;
import org.glstudio.nexus.utils.KeyStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParsersTest {

    @Test
    void actionLinesFollowTheRewardFormat() {
        assertEquals(Optional.of(new ActionLine("console", "give %player% diamond 1")),
                ActionLine.parse("/give %player% diamond 1"));
        assertEquals(Optional.of(new ActionLine("player", "spawn")), ActionLine.parse("[PLAYER] /spawn"));
        assertEquals(Optional.of(new ActionLine("message", "&aWon")), ActionLine.parse("  [message]  &aWon  "));
        assertEquals(Optional.empty(), ActionLine.parse("[title]"));
        assertEquals(Optional.empty(), ActionLine.parse("[unclosed text"));
        assertEquals(Optional.empty(), ActionLine.parse("   "));
    }

    @Test
    void slotListsAcceptNumbersRangesAndLists() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("one: 4\nrange: '10-12'\nmixed: [1, '3-4', bad]\n");
        assertEquals(List.of(4), MenuConfigUtils.parseSlots(yaml, "one"));
        assertEquals(List.of(10, 11, 12), MenuConfigUtils.parseSlots(yaml, "range"));
        assertEquals(List.of(1, 3, 4), MenuConfigUtils.parseSlots(yaml, "mixed"));
        assertEquals(List.of(), MenuConfigUtils.parseSlots(yaml, "missing"));
    }

    @Test
    void keyStylesResolveTheSpellingInUse() throws Exception {
        YamlConfiguration upper = new YamlConfiguration();
        upper.loadFromString("CUSTOM_MODEL_DATA: 3\n");
        assertEquals("CUSTOM_MODEL_DATA", KeyStyle.ANY.resolve(upper, "custom-model-data"));
        assertEquals("custom-model-data", KeyStyle.KEBAB.resolve(upper, "custom-model-data"));
        assertEquals("REDIS.ALLOW_INSECURE", KeyStyle.UPPER_SNAKE.resolve(upper, "redis.allow-insecure"));
        assertEquals(List.of("slot", "SLOT"), KeyStyle.ANY.candidates("slot"));
    }
}
