package org.glstudio.nexus.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.glstudio.nexus.testing.FakePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileTest {

    private static final String BUNDLED = """
            config-version: 2
            delay: 5
            name: bundled
            only-in-jar: 42
            limits:
              default: 3
            list-in-jar:
              - a
              - b
            """;

    private static final String ON_DISK = """
            config-version: 1
            delay: 10
            name: server
            limits:
              default: 7
            typo-key: true
            """;

    @TempDir
    Path folder;

    private ConfigFile load() throws IOException {
        Files.writeString(folder.resolve("config.yml"), ON_DISK, StandardCharsets.UTF_8);
        return new ConfigFile(FakePlugin.create(folder.toFile(), Map.of("config.yml", BUNDLED)), "config.yml");
    }

    @Test
    void gettersWithoutDefaultFallBackToTheJar() throws IOException {
        ConfigFile file = load();
        assertEquals(10, file.getInt("delay"));
        assertEquals(42, file.getInt("only-in-jar"));
        assertEquals(List.of("a", "b"), file.getStringList("list-in-jar"));
        assertNull(file.getString("nowhere"));
    }

    @Test
    void gettersWithDefaultOnlyLookAtTheFile() throws IOException {
        ConfigFile file = load();
        assertEquals(10, file.getInt("delay", -1));
        assertEquals(-1, file.getInt("only-in-jar", -1));
        assertEquals("fallback", file.getString("nowhere", "fallback"));
    }

    @Test
    void resolvedViewLooksAtFileThenJarThenDefault() throws IOException {
        ResolvedConfig resolved = load().resolved();
        assertEquals(10, resolved.getInt("delay", -1));
        assertEquals(42, resolved.getInt("only-in-jar", -1));
        assertEquals(-1, resolved.getInt("nowhere", -1));
        assertEquals("server", resolved.getString("name", "x"));
        assertEquals(List.of("a", "b"), resolved.getStringList("list-in-jar"));
        assertTrue(resolved.isSet("only-in-jar"));
        assertFalse(resolved.isSet("nowhere"));
        assertEquals(7, resolved.getSection("limits").getInt("default"));
        // The wrong type is treated as missing rather than coerced.
        assertEquals(5L, resolved.getLong("name", 5L));
    }

    @Test
    void sectionsMissingFromTheFileComeFromTheJar() throws IOException {
        Files.writeString(folder.resolve("config.yml"), "config-version: 2\n", StandardCharsets.UTF_8);
        ConfigFile file = new ConfigFile(FakePlugin.create(folder.toFile(), Map.of("config.yml", BUNDLED)), "config.yml");
        assertNotNull(file.getConfigurationSection("limits"));
        assertEquals(3, file.getConfigurationSection("limits").getInt("default"));
    }

    @Test
    void anOldFileIsStampedWithoutTouchingAnythingElse() throws IOException {
        String text = "# a comment of the owner\nvalue:   7   # odd spacing\nname: 'kept'\n";
        Files.writeString(folder.resolve("config.yml"), text);
        ConfigFile file = new ConfigFile(FakePlugin.create(folder.toFile(), Map.of("config.yml", BUNDLED)), "config.yml");
        List<String> warnings = new ArrayList<>();

        assertEquals(0, file.version());
        assertFalse(file.checkVersion(3, warnings::add));
        assertTrue(warnings.isEmpty());
        assertEquals(3, file.version());
        assertTrue(file.checkVersion(3, warnings::add));

        String written = Files.readString(folder.resolve("config.yml"));
        assertTrue(written.startsWith("config-version: 3"));
        assertTrue(written.endsWith(text));

        file.reload();
        assertEquals(3, file.version());
        assertEquals(7, file.getInt("value"));
    }

    @Test
    void aFileWithAnOlderVersionLineKeepsItsPlace() throws IOException {
        Files.writeString(folder.resolve("config.yml"), "# head\nconfig-version: 1\nvalue: 7\n");
        ConfigFile file = new ConfigFile(FakePlugin.create(folder.toFile(), Map.of("config.yml", BUNDLED)), "config.yml");

        assertFalse(file.checkVersion(2, ignored -> { }));
        assertEquals("# head\nconfig-version: 2\nvalue: 7\n", Files.readString(folder.resolve("config.yml")));
    }

    @Test
    void versionUnknownAndMissingKeys() throws IOException {
        ConfigFile file = load();
        assertEquals(1, file.version());
        List<String> warnings = new ArrayList<>();
        assertTrue(file.checkVersion(1, warnings::add));
        assertTrue(warnings.isEmpty());

        assertEquals(Set.of("typo-key"), file.unknownKeys(1, List.of()));
        assertEquals(Set.of(), file.unknownKeys(1, List.of("typo-key")));
        assertTrue(file.missingKeys(1).containsAll(Set.of("only-in-jar", "list-in-jar")));
    }

    @Test
    void missingFileIsCopiedFromTheJar() {
        ConfigFile file = new ConfigFile(FakePlugin.create(folder.toFile(), Map.of("menus/homes.yml", "title: Homes\n")),
                "menus", "homes.yml");
        assertTrue(new File(folder.toFile(), "menus/homes.yml").exists());
        assertEquals("Homes", file.getString("title"));
        assertEquals("menus/homes.yml", file.getFileName());
    }

    @Test
    void atomicSaveWritesWithoutLeavingTemporaryFiles() throws IOException {
        ConfigFile file = load();
        file.set("delay", 99);
        assertTrue(file.saveAtomically());

        YamlConfiguration reread = YamlConfiguration.loadConfiguration(folder.resolve("config.yml").toFile());
        assertEquals(99, reread.getInt("delay"));
        assertFalse(Files.exists(folder.resolve("config.yml.tmp")));
    }

    @Test
    void writeAtomicallyReplacesContentAndCreatesFolders() throws IOException {
        Path target = folder.resolve("data/nested/players.yml");
        YamlFiles.writeAtomically(target, "a: 1\n");
        YamlFiles.writeAtomically(target, "a: 2\n");
        assertEquals("a: 2\n", Files.readString(target));
        assertFalse(Files.exists(folder.resolve("data/nested/players.yml.tmp")));
    }

    @Test
    void reloadPicksUpChangesAndValidateReportsSyntaxErrors() throws IOException {
        ConfigFile file = load();
        Files.writeString(folder.resolve("config.yml"), "delay: 3\n", StandardCharsets.UTF_8);
        assertNull(file.validate());
        file.reload();
        assertEquals(3, file.getInt("delay", 0));

        Files.writeString(folder.resolve("config.yml"), "delay: [unclosed\n", StandardCharsets.UTF_8);
        assertNotNull(file.validate());
        file.reload();
        assertEquals(3, file.getInt("delay", 0));
    }

    @Test
    void migratorRunsPendingStepsAndKeepsValues() throws IOException {
        Files.writeString(folder.resolve("config.yml"), "config-version: 1\nold:\n  key: custom\n", StandardCharsets.UTF_8);
        ConfigFile file = new ConfigFile(FakePlugin.create(folder.toFile(), Map.of("config.yml", BUNDLED)), "config.yml");

        int from = ConfigMigrator.of(file)
                .step(1, cfg -> cfg.set("never", true))
                .step(2, cfg -> ConfigMigrator.move(cfg, "old.key", "new.key"))
                .migrate(2);

        assertEquals(1, from);
        assertEquals("custom", file.getString("new.key", null));
        assertFalse(file.contains("old.key"));
        assertFalse(file.contains("never"));
        assertEquals(2, YamlConfiguration.loadConfiguration(folder.resolve("config.yml").toFile()).getInt("config-version"));
    }

    @Test
    void problemsCollectorReportsOnlyPresentKeys() throws IOException {
        ConfigFile file = load();
        ConfigProblems problems = new ConfigProblems();
        problems.atLeast(file, "delay", 20);
        problems.atLeast(file, "nowhere", 20);
        problems.oneOf(file, "name", List.of("a", "b"));
        assertEquals(2, problems.list().size());
    }
}
