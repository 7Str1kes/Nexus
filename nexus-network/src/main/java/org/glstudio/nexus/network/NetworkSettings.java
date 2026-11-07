package org.glstudio.nexus.network;

import org.bukkit.configuration.ConfigurationSection;
import org.glstudio.nexus.utils.KeyStyle;
import org.glstudio.nexus.utils.ResolvedConfig;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-server settings: this server's identity, the Redis connection and the shared secret.
 *
 * <p>String values may reference environment variables as {@code ${NAME}}, so secrets can stay out
 * of the configuration file.
 *
 * @param enabled        whether multi-server support is on
 * @param serverName     this server's name, as known by the proxy
 * @param instanceId     random id of this process, to recognise its own messages
 * @param channel        the pub/sub channel, also the prefix of stream keys
 * @param host           the Redis host
 * @param port           the Redis port
 * @param password       the Redis password, empty for none
 * @param allowInsecure  whether to accept Redis without password and packets without a strong secret
 * @param secret         the secret every server on the channel signs packets with
 * @param retryAttempts  publish attempts before giving up, at least 1
 * @param retryBackoffMs base delay between publish attempts
 */
public record NetworkSettings(boolean enabled, String serverName, String instanceId, String channel,
                              String host, int port, String password, boolean allowInsecure, String secret,
                              int retryAttempts, long retryBackoffMs) {

    /** Server name used when none is configured. */
    public static final String UNNAMED_SERVER = "unnamed-server";
    /** Minimum secret length outside insecure mode. */
    public static final int MINIMUM_SECRET_LENGTH = 32;

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    /**
     * Normalises the settings.
     *
     * @param enabled        whether multi-server support is on
     * @param serverName     this server's name
     * @param instanceId     random id of this process
     * @param channel        the pub/sub channel
     * @param host           the Redis host
     * @param port           the Redis port
     * @param password       the Redis password
     * @param allowInsecure  whether insecure mode is allowed
     * @param secret         the shared secret
     * @param retryAttempts  publish attempts
     * @param retryBackoffMs base delay between attempts
     */
    public NetworkSettings {
        serverName = orDefault(serverName, UNNAMED_SERVER);
        instanceId = orDefault(instanceId, UUID.randomUUID().toString());
        Objects.requireNonNull(channel, "channel");
        host = orDefault(host, "localhost");
        password = password == null ? "" : password;
        secret = secret == null ? "" : secret;
        retryAttempts = Math.max(1, retryAttempts);
        retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    /**
     * Reads the common layout:
     * <pre>
     * enabled, server-name, secret,
     * redis: { host, port, password, allow-insecure, channel },
     * retry: { attempts, backoff-ms }
     * </pre>
     * spelled in {@code style}. A {@code redis.secret} key is accepted when {@code secret} is absent.
     *
     * @param section        the multi-server section, may be {@code null} (multi-server off)
     * @param style          how keys are spelled
     * @param defaultChannel used when no channel is configured
     * @return the settings
     */
    public static NetworkSettings fromSection(ConfigurationSection section, KeyStyle style, String defaultChannel) {
        if (section == null) {
            return new NetworkSettings(false, null, null, defaultChannel, null, 6379, null, false, null, 3, 100);
        }
        String secretKey = style.resolve(section, "secret");
        if (!section.isSet(secretKey)) secretKey = style.resolve(section, "redis.secret");
        return new NetworkSettings(
                section.getBoolean(style.resolve(section, "enabled"), false),
                resolveEnv(section.getString(style.resolve(section, "server-name"), null)),
                null,
                orDefault(section.getString(style.resolve(section, "redis.channel"), null), defaultChannel),
                resolveEnv(section.getString(style.resolve(section, "redis.host"), null)),
                section.getInt(style.resolve(section, "redis.port"), 6379),
                resolveEnv(section.getString(style.resolve(section, "redis.password"), null)),
                section.getBoolean(style.resolve(section, "redis.allow-insecure"), false),
                resolveEnv(section.getString(secretKey, null)),
                section.getInt(style.resolve(section, "retry.attempts"), 3),
                section.getLong(style.resolve(section, "retry.backoff-ms"), 100));
    }

    /**
     * Reads the same layout as {@link #fromSection} from a resolved configuration, so a key missing
     * from the file on disk falls back to the copy bundled in the jar, key by key.
     *
     * @param config         the configuration
     * @param root           path of the multi-server section, e.g. {@code multi-server}
     * @param style          how keys are spelled
     * @param defaultChannel used when no channel is configured
     * @return the settings
     */
    public static NetworkSettings fromConfig(ResolvedConfig config, String root, KeyStyle style, String defaultChannel) {
        UnaryOperator<String> path = key -> {
            List<String> candidates = style.candidates(key);
            for (String candidate : candidates) {
                if (config.isSet(root + "." + candidate)) return root + "." + candidate;
            }
            return root + "." + candidates.getFirst();
        };
        String secretPath = path.apply("secret");
        if (!config.isSet(secretPath)) secretPath = path.apply("redis.secret");
        return new NetworkSettings(
                config.getBoolean(path.apply("enabled"), false),
                resolveEnv(config.getString(path.apply("server-name"), null)),
                null,
                orDefault(config.getString(path.apply("redis.channel"), null), defaultChannel),
                resolveEnv(config.getString(path.apply("redis.host"), null)),
                config.getInt(path.apply("redis.port"), 6379),
                resolveEnv(config.getString(path.apply("redis.password"), null)),
                config.getBoolean(path.apply("redis.allow-insecure"), false),
                resolveEnv(config.getString(secretPath, null)),
                config.getInt(path.apply("retry.attempts"), 3),
                config.getLong(path.apply("retry.backoff-ms"), 100));
    }

    /**
     * Returns whether {@code server} names this server; blank means "here".
     *
     * @param server a server name
     * @return {@code true} for this server
     */
    public boolean isLocalServer(String server) {
        return server == null || server.isBlank() || server.equalsIgnoreCase(serverName);
    }

    /**
     * Returns whether the server name is still the placeholder.
     *
     * @return {@code true} if no server name is configured
     */
    public boolean isUnnamed() {
        return UNNAMED_SERVER.equals(serverName);
    }

    /**
     * Returns whether packets are signed with a secret long enough to trust.
     *
     * @return {@code true} if the secret has at least {@value #MINIMUM_SECRET_LENGTH} characters
     */
    public boolean hasStrongSecret() {
        return secret.length() >= MINIMUM_SECRET_LENGTH;
    }

    /**
     * Replaces {@code ${NAME}} with the environment variable {@code NAME} (empty if unset).
     *
     * @param value the configured value, may be {@code null}
     * @return the resolved value
     */
    public static String resolveEnv(String value) {
        if (value == null || value.indexOf('$') < 0) return value;
        Matcher matcher = ENV_PATTERN.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String env = System.getenv(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(env == null ? "" : env));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
