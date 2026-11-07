package org.glstudio.nexus.proxy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.glstudio.nexus.utils.LoggerUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Talks to the proxy over the legacy {@code BungeeCord} plugin-message channel, which BungeeCord and
 * Velocity (in its compatibility mode) both accept: sends players to other servers and lists the
 * servers behind the proxy.
 *
 * <p>Plugin messages need a player connection to travel over; {@link #servers(Duration)} uses any
 * online player and answers with an empty list when nobody is online.
 */
public final class ProxyMessenger implements PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";
    private static final String CONNECT = "Connect";
    private static final String GET_SERVERS = "GetServers";

    private final Plugin plugin;
    private volatile CompletableFuture<List<String>> pendingServers;
    private boolean registered;

    /**
     * Creates the messenger. Call {@link #register()} before using it.
     *
     * @param plugin the owning plugin
     */
    public ProxyMessenger(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the outgoing and incoming channel.
     */
    public void register() {
        if (registered) return;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        registered = true;
    }

    /**
     * Unregisters the channels and fails any pending server list request.
     */
    public void close() {
        if (!registered) return;
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        CompletableFuture<List<String>> pending = pendingServers;
        if (pending != null) pending.complete(List.of());
        registered = false;
    }

    /**
     * Asks the proxy to move {@code player} to {@code server}.
     *
     * @param player the player
     * @param server the server name as configured in the proxy
     * @return {@code true} if the request was sent; the proxy may still refuse it
     */
    public boolean connect(Player player, String server) {
        try {
            player.sendPluginMessage(plugin, CHANNEL, message(CONNECT, server));
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            LoggerUtils.logError("Failed to transfer " + player.getName() + " to " + server + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Asks the proxy for the names of its servers.
     *
     * @param timeout how long to wait for the answer
     * @return the names; empty if nobody is online to carry the request or the proxy did not answer
     */
    public CompletableFuture<List<String>> servers(Duration timeout) {
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) return CompletableFuture.completedFuture(List.of());

        CompletableFuture<List<String>> answer = new CompletableFuture<>();
        pendingServers = answer;
        try {
            carrier.sendPluginMessage(plugin, CHANNEL, message(GET_SERVERS));
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            pendingServers = null;
            return CompletableFuture.completedFuture(List.of());
        }
        return answer.completeOnTimeout(List.of(), timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(error -> List.of());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!GET_SERVERS.equals(in.readUTF())) return;
            CompletableFuture<List<String>> pending = pendingServers;
            if (pending == null) return;
            pendingServers = null;
            pending.complete(Arrays.stream(in.readUTF().split(", ")).filter(name -> !name.isBlank()).toList());
        } catch (IOException ignored) {
            // A malformed answer is equivalent to no answer: the request times out empty.
        }
    }

    private static byte[] message(String... parts) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            for (String part : parts) out.writeUTF(part);
            return bytes.toByteArray();
        }
    }
}
