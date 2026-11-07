package org.glstudio.nexus.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CCTest {

    @Test
    void legacyCodesBecomeTags() {
        assertEquals("<red>Hi <bold>there", CC.legacyToMiniMessage("&cHi &lthere"));
        assertEquals("<green>ok", CC.legacyToMiniMessage("§aok"));
        assertEquals("<reset>x", CC.legacyToMiniMessage("&rx"));
    }

    @Test
    void hexFormsBecomeColorTags() {
        assertEquals("<color:#ff0000>red", CC.legacyToMiniMessage("&#ff0000red"));
        assertEquals("<color:#a1b2c3>x", CC.legacyToMiniMessage("&x&a&1&b&2&c&3x"));
        assertEquals("<color:#A1B2C3>x", CC.legacyToMiniMessage("§x§A§1§B§2§C§3x"));
    }

    @Test
    void textWithoutCodesIsUnchanged() {
        assertEquals("<gray>already mini</gray>", CC.legacyToMiniMessage("<gray>already mini</gray>"));
        assertEquals("rock & roll", CC.legacyToMiniMessage("rock & roll"));
        assertEquals("ends with &", CC.legacyToMiniMessage("ends with &"));
        assertEquals("", CC.legacyToMiniMessage(null));
    }

    @Test
    void incompleteHexIsLeftAlone() {
        assertEquals("&#ff00 short", CC.legacyToMiniMessage("&#ff00 short"));
    }

    @Test
    void legacyTranslationHandlesHex() {
        assertEquals("§x§f§f§0§0§0§0red", CC.t("&#ff0000red"));
        assertEquals("§cred", CC.t("&cred"));
        assertEquals("", CC.t((String) null));
        assertEquals("plain", CC.strip("&cplain"));
    }
}
