package org.glstudio.nexus.modules.menu.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Captures a player's next chat message as free-text input for a flow that cannot be expressed as a
 * click: a search query, a name, an amount. The message is cancelled so nobody else sees it, and
 * the callbacks always run on the main thread.
 *
 * <p>The text is read from {@link AsyncChatEvent#originalMessage()}: another plugin's chat listener
 * may already have rewritten {@code message()} (colours, emojis) before this one runs.
 *
 * <p>One instance per plugin, created alongside its {@link org.glstudio.nexus.modules.menu.MenuManager}.
 */
public final class ChatInputManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /**
     * Creates the manager and registers its listener.
     *
     * @param plugin the owning plugin
     */
    public ChatInputManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Starts building a request for {@code player}'s next message.
     *
     * @param player the player to ask
     * @return a request; nothing happens until {@link Request#start()}
     */
    public Request request(Player player) {
        return new Request(player);
    }

    /**
     * Waits for the next message of {@code uuid}, passing it untrimmed to {@code onInput}.
     *
     * @param uuid    the player
     * @param onInput receives the text on the main thread
     */
    public void prompt(UUID uuid, Consumer<String> onInput) {
        prompt(uuid, onInput, null);
    }

    /**
     * Waits for the next message of {@code uuid}, passing it untrimmed to {@code onInput}.
     *
     * @param uuid          the player
     * @param onInput       receives the text on the main thread
     * @param cancelHandler run by {@link #cancel(UUID)}, may be {@code null}
     */
    public void prompt(UUID uuid, Consumer<String> onInput, Runnable cancelHandler) {
        replace(uuid, new Pending(onInput, cancelHandler, Set.of(), false, null));
    }

    /**
     * Cancels the pending request of {@code uuid}, if any, and runs its cancel handler.
     *
     * @param uuid the player
     */
    public void cancel(UUID uuid) {
        Pending removed = pending.remove(uuid);
        if (removed == null) return;
        removed.stopTimeout();
        if (removed.onCancel() != null) removed.onCancel().run();
    }

    /**
     * Returns whether {@code uuid} has a pending request.
     *
     * @param uuid the player
     * @return {@code true} if their next message will be captured
     */
    public boolean isAwaitingInput(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /**
     * Captures the message of a player with a pending request.
     *
     * @param event the chat event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Pending request = pending.remove(uuid);
        if (request == null) return;

        event.setCancelled(true);
        request.stopTimeout();

        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        String input = request.trim() ? message.trim() : message;
        boolean cancelled = request.cancelWords().contains(input.toLowerCase(Locale.ROOT));

        // The event fires on the chat thread; callbacks touch menus and the Bukkit API.
        runOnMain(() -> {
            if (cancelled) {
                if (request.onCancel() != null) request.onCancel().run();
            } else {
                request.onInput().accept(input);
            }
        });
    }

    /**
     * Drops the pending request of a player who leaves; no callback runs.
     *
     * @param event the quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Pending removed = pending.remove(event.getPlayer().getUniqueId());
        if (removed != null) removed.stopTimeout();
    }

    /**
     * Drops every pending request and unregisters the listener.
     */
    public void shutdown() {
        pending.values().forEach(Pending::stopTimeout);
        pending.clear();
        HandlerList.unregisterAll(this);
    }

    private void replace(UUID uuid, Pending request) {
        Pending previous = pending.put(uuid, request);
        if (previous != null) previous.stopTimeout();
    }

    private void runOnMain(Runnable task) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private record Pending(Consumer<String> onInput, Runnable onCancel, Set<String> cancelWords, boolean trim,
                           BukkitTask timeoutTask) {
        void stopTimeout() {
            if (timeoutTask != null) timeoutTask.cancel();
        }
    }

    /**
     * A request being configured. Defaults: input trimmed, no cancel words, no timeout, inventory
     * left open.
     */
    public final class Request {
        private final Player player;
        private Component prompt;
        private Set<String> cancelWords = Set.of();
        private Duration timeout;
        private Runnable onTimeout;
        private boolean closeInventory;
        private boolean trim = true;
        private Consumer<String> onInput = input -> {
        };
        private Runnable onCancel;

        private Request(Player player) {
            this.player = Objects.requireNonNull(player, "player");
        }

        /**
         * Sets the message sent to the player when the request starts.
         *
         * @param prompt the message, or {@code null} for none
         * @return this request
         */
        public Request prompt(Component prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Sets words that cancel instead of being taken as input, compared ignoring case.
         *
         * @param words the words, e.g. {@code "cancel"}
         * @return this request
         */
        public Request cancelWords(String... words) {
            this.cancelWords = Stream.of(words)
                    .filter(Objects::nonNull)
                    .map(word -> word.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            return this;
        }

        /**
         * Gives up after {@code timeout}, running the timeout handler (or the cancel handler if none).
         *
         * @param timeout how long to wait
         * @return this request
         */
        public Request timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets what runs when the timeout expires.
         *
         * @param onTimeout the handler, on the main thread
         * @return this request
         */
        public Request onTimeout(Runnable onTimeout) {
            this.onTimeout = onTimeout;
            return this;
        }

        /**
         * Closes the player's open inventory when the request starts, so the chat box is usable.
         *
         * @param close {@code true} to close it
         * @return this request
         */
        public Request closeInventory(boolean close) {
            this.closeInventory = close;
            return this;
        }

        /**
         * Sets whether surrounding spaces are removed from the input.
         *
         * @param trim {@code true} to trim
         * @return this request
         */
        public Request trim(boolean trim) {
            this.trim = trim;
            return this;
        }

        /**
         * Sets what receives the input.
         *
         * @param onInput the handler, on the main thread
         * @return this request
         */
        public Request onInput(Consumer<String> onInput) {
            this.onInput = Objects.requireNonNull(onInput, "onInput");
            return this;
        }

        /**
         * Sets what runs on a cancel word or {@link ChatInputManager#cancel(UUID)}.
         *
         * @param onCancel the handler, on the main thread
         * @return this request
         */
        public Request onCancel(Runnable onCancel) {
            this.onCancel = onCancel;
            return this;
        }

        /**
         * Starts waiting, replacing any earlier request of the same player.
         */
        public void start() {
            UUID uuid = player.getUniqueId();
            if (closeInventory) player.closeInventory();
            if (prompt != null) player.sendMessage(prompt);

            BukkitTask timeoutTask = null;
            if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                Runnable expired = onTimeout != null ? onTimeout : onCancel;
                timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Pending current = pending.get(uuid);
                    if (current == null || current.onInput() != onInput) return;
                    pending.remove(uuid);
                    if (expired != null) expired.run();
                }, Math.max(1L, timeout.toMillis() / 50L));
            }
            replace(uuid, new Pending(onInput, onCancel, cancelWords, trim, timeoutTask));
        }
    }
}
