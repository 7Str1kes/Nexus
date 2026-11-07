package org.glstudio.nexus.modules.menu.button;

import org.bukkit.Material;
import org.glstudio.nexus.utils.Registries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialNamesTest {

    @Test
    void acceptsEverySpellingAnAdministratorMayWrite() {
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, ButtonBuilder.parseMaterial("GRAY_STAINED_GLASS_PANE", Material.STONE, null));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, ButtonBuilder.parseMaterial("gray_stained_glass_pane", Material.STONE, null));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, ButtonBuilder.parseMaterial("gray-stained-glass-pane", Material.STONE, null));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, Registries.material("gray-stained-glass-pane").orElseThrow());
    }

    @Test
    void anUnknownNameFallsBack() {
        assertEquals(Material.STONE, ButtonBuilder.parseMaterial("not-a-material", Material.STONE, null));
    }
}
