package org.glstudio.nexus.network;

import com.google.gson.Gson;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.nexus.utils.KeyStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final PacketCodec<TestPacket> codec = new PacketCodec<>(new Gson(),
            type -> "NICK_CHANGE".equals(type) ? NickPacket.class : null);

    abstract static class TestPacket extends AbstractPacket {
    }

    static final class NickPacket extends TestPacket {
        private final String nick;

        NickPacket(String nick) {
            this.nick = nick;
        }

        @Override
        public String type() {
            return "NICK_CHANGE";
        }
    }

    @Test
    void signedPacketsRoundTrip() {
        NickPacket sent = new NickPacket("Steve");
        String message = codec.encode(sent, SECRET);
        assertTrue(message.startsWith("NICK_CHANGE:"));

        NickPacket received = (NickPacket) codec.decode(message, SECRET);
        assertEquals("Steve", received.nick);
        assertEquals(sent.getPacketId(), received.getPacketId());
        assertEquals(sent.getSentAt(), received.getSentAt());
    }

    @Test
    void wireFormatMatchesPreviousReleases() {
        NickPacket sent = new NickPacket("Alex");
        String[] parts = codec.encode(sent, SECRET).split(":", 3);
        String json = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"packetId\""));
        assertTrue(json.contains("\"sentAt\""));
        assertTrue(json.contains("\"nick\":\"Alex\""));
        assertEquals(PacketCodec.sign(parts[0] + ":" + parts[1], SECRET), parts[2]);
    }

    @Test
    void tamperedOrForeignPacketsAreRejected() {
        String message = codec.encode(new NickPacket("Steve"), SECRET);
        String[] parts = message.split(":", 3);
        String forged = parts[0] + ":" + Base64.getEncoder().encodeToString(
                "{\"nick\":\"Admin\"}".getBytes(StandardCharsets.UTF_8)) + ":" + parts[2];

        assertThrows(SecurityException.class, () -> codec.decode(forged, SECRET));
        assertThrows(SecurityException.class, () -> codec.decode(message, SECRET + "x"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("garbage", SECRET));
        String unknownType = "OTHER:" + parts[1];
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unknownType + ":" + PacketCodec.sign(unknownType, SECRET), SECRET));
    }

    @Test
    void decodeUnverifiedIgnoresTheSignatureButNotTheShape() {
        String wire = codec.encode(new NickPacket("Steve"), SECRET);

        assertEquals("NICK_CHANGE", codec.decodeUnverified(wire).type());
        assertEquals("NICK_CHANGE", codec.decodeUnverified(wire.substring(0, wire.lastIndexOf(':') + 1) + "forged").type());
        assertThrows(IllegalArgumentException.class, () -> codec.decodeUnverified("not a packet"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeUnverified("UNKNOWN:e30=:"));
    }

    @Test
    void connectRefusesAMissingPasswordOrAWeakSecretBeforeTouchingTheNetwork() {
        NetworkSettings noPassword = new NetworkSettings(true, "a", null, "c", "127.0.0.1", 1, "", false, SECRET, 3, 100);
        NetworkSettings weakSecret = new NetworkSettings(true, "a", null, "c", "127.0.0.1", 1, "pw", false, "short", 3, 100);

        assertThrows(IllegalStateException.class, () -> new RedisConnector(noPassword, 4).connect("forge"));
        assertThrows(IllegalStateException.class, () -> new RedisConnector(weakSecret, 4).connect("forge"));
    }

    @Test
    void aWeakSecretOnlyWarnsWhenItIsNotRequired() {
        NetworkSettings weakSecret = new NetworkSettings(true, "a", null, "c", "127.0.0.1", 1, "pw", false, "short", 3, 100);

        // Nothing listens on port 1, so it gets as far as the network and fails there instead.
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> new RedisConnector(weakSecret, 4, 2, 1, 200).connect("forge", false));
        assertFalse(failure instanceof IllegalStateException);
    }

    @Test
    void borrowingBeforeConnectingFailsClearly() {
        NetworkSettings settings = new NetworkSettings(true, "a", null, "c", "127.0.0.1", 1, "pw", false, SECRET, 3, 100);

        assertThrows(IllegalStateException.class, () -> new RedisConnector(settings, 4).resource());
    }

    @Test
    void emptySecretMeansEmptySignature() {
        String message = codec.encode(new NickPacket("Steve"), "");
        assertTrue(message.endsWith(":"));
        assertEquals("Steve", ((NickPacket) codec.decode(message, "")).nick);
        assertThrows(SecurityException.class, () -> codec.decode(message, SECRET));
    }

    @Test
    void constantTimeComparison() {
        assertTrue(PacketCodec.constantTimeEquals("abc", "abc"));
        assertFalse(PacketCodec.constantTimeEquals("abc", "abd"));
        assertFalse(PacketCodec.constantTimeEquals("abc", "abcd"));
    }

    @Test
    void deduplicatorDropsRepeatsAndStalePackets() {
        PacketDeduplicator dedup = new PacketDeduplicator(1_000);
        NickPacket packet = new NickPacket("x");
        assertTrue(dedup.accept(packet));
        assertFalse(dedup.accept(packet));

        TrackedPacket stale = new TrackedPacket() {
            private final UUID id = UUID.randomUUID();

            @Override
            public UUID getPacketId() {
                return id;
            }

            @Override
            public long getSentAt() {
                return System.currentTimeMillis() - 10_000;
            }

            @Override
            public String type() {
                return "STALE";
            }
        };
        assertFalse(dedup.accept(stale));
    }

    @Test
    void settingsReadBothKeyStylesAndEnvironmentPlaceholders() throws Exception {
        YamlConfiguration kebab = new YamlConfiguration();
        kebab.loadFromString("""
                enabled: true
                server-name: lobby
                secret: "%s"
                redis:
                  host: redis.local
                  port: 6380
                  password: pw
                  channel: gl-test
                retry:
                  attempts: 5
                """.formatted(SECRET));
        NetworkSettings settings = NetworkSettings.fromSection(kebab, KeyStyle.ANY, "default");
        assertTrue(settings.enabled());
        assertEquals("lobby", settings.serverName());
        assertEquals("redis.local", settings.host());
        assertEquals(6380, settings.port());
        assertEquals("gl-test", settings.channel());
        assertEquals(5, settings.retryAttempts());
        assertTrue(settings.hasStrongSecret());
        assertTrue(settings.isLocalServer("LOBBY"));
        assertTrue(settings.isLocalServer(" "));

        YamlConfiguration upper = new YamlConfiguration();
        upper.loadFromString("ENABLED: true\nSERVER_NAME: hub\nREDIS:\n  SECRET: s3cr3t\n  HOST: h\n");
        NetworkSettings legacy = NetworkSettings.fromSection(upper, KeyStyle.ANY, "fallback");
        assertEquals("hub", legacy.serverName());
        assertEquals("s3cr3t", legacy.secret());
        assertEquals("fallback", legacy.channel());
        assertFalse(legacy.hasStrongSecret());

        assertFalse(NetworkSettings.fromSection(null, KeyStyle.ANY, "c").enabled());
        assertEquals("pre--post", NetworkSettings.resolveEnv("pre-${NEXUS_TEST_SURELY_UNSET_VARIABLE}-post"));
    }

    @Test
    void resolvedSettingsFallBackToTheJarKeyByKey(@TempDir Path folder) throws Exception {
        String bundled = """
                multi-server:
                  enabled: false
                  server-name: server-1
                  redis:
                    host: localhost
                    port: 6379
                  retry:
                    attempts: 3
                """;
        Files.writeString(folder.resolve("config.yml"), """
                multi-server:
                  enabled: true
                  redis:
                    port: 6380
                """, StandardCharsets.UTF_8);
        ConfigFile file = new ConfigFile(plugin(folder, bundled), "config.yml");

        NetworkSettings settings = NetworkSettings.fromConfig(file.resolved(), "multi-server", KeyStyle.KEBAB, "gl-test");
        assertTrue(settings.enabled());
        assertEquals("server-1", settings.serverName());
        assertEquals(6380, settings.port());
        assertEquals("localhost", settings.host());
        assertEquals("gl-test", settings.channel());
        assertEquals(3, settings.retryAttempts());
    }

    private static Plugin plugin(Path dataFolder, String bundledConfig) {
        Logger logger = Logger.getLogger("NetworkTest");
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDataFolder" -> dataFolder.toFile();
                    case "getLogger" -> logger;
                    case "getName" -> "NetworkTest";
                    case "getResource" -> "config.yml".equals(args[0])
                            ? new ByteArrayInputStream(bundledConfig.getBytes(StandardCharsets.UTF_8)) : null;
                    case "isEnabled" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "NetworkTest";
                    default -> null;
                });
    }
}
