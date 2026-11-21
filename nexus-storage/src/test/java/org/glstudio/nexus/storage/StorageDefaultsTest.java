package org.glstudio.nexus.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageDefaultsTest {

    @Test
    void theUsualNamesOfAPlugin() {
        assertEquals(new StorageDefaults("GL-Bounties", "gl_bounties", "bounties", "glbounties_"),
                StorageDefaults.of("GL-Bounties", "bounties"));
    }

    @Test
    void unsupportedTypesAreWarnedAbout() {
        List<String> warnings = new ArrayList<>();
        DatabaseSettings.warnIfUnsupported("oracle", warnings::add);
        DatabaseSettings.warnIfUnsupported("yaml", warnings::add);
        DatabaseSettings.warnIfUnsupported("mariadb", warnings::add);
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("oracle"));
    }
}
