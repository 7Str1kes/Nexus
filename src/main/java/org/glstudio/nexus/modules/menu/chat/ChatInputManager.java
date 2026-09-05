package org.glstudio.nexus.modules.menu.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures one line of a player's next chat message as free-text input for a menu flow that can't
 * be expressed as a click — a search query, a name, an amount. Conventionally the caller closes
 * its menu before calling {@link #prompt}, sends its own prompt text, and reopens a menu once the
 * input (or cancellation) arrives.
 *
 * <p>One instance per plugin, created alongside its {@link org.glstudio.nexus.modules.menu.MenuManager}.
 */
public final class ChatInputManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> onCancel = new ConcurrentHashMap<>();

    public ChatInputManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void prompt(UUID uuid, Consumer<String> onInput) {
        prompt(uuid, onInput, null);
    }

    public void prompt(UUID uuid, Consumer<String> onInput, Runnable cancelHandler) {
        pending.put(uuid, onInput);
        if (cancelHandler != null) onCancel.put(uuid, cancelHandler);
        else onCancel.remove(uuid);
    }

    /** Cancels a pending prompt (if any) and runs its cancel handler. */
    public void cancel(UUID uuid) {
        if (pending.remove(uuid) != null) {
            Runnable handler = onCancel.remove(uuid);
            if (handler != null) handler.run();
        }
    }

    public boolean isAwaitingInput(UUID uuid) {
        return pending.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Consumer<String> handler = pending.remove(uuid);
        if (handler == null) return;

        onCancel.remove(uuid);
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        Bukkit.getScheduler().runTask(plugin, () -> handler.accept(message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
        onCancel.remove(uuid);
    }

    public void shutdown() {
        pending.clear();
        onCancel.clear();
        HandlerList.unregisterAll(this);
    }
}
