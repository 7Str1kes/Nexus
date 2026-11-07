package org.glstudio.nexus.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.util.function.Supplier;

/**
 * Fires a plugin's public API events.
 *
 * <p>A listener that throws must not take the operation down with it: a broken third-party plugin
 * should cost its own feature, not everyone's. An exception therefore counts as "not cancelled".
 */
public final class EventUtils {

    private EventUtils() {
    }

    /**
     * Fires {@code event} and reports whether the operation may proceed.
     *
     * @param event the event
     * @return {@code false} only if the event is cancellable and a listener cancelled it
     */
    public static boolean call(Event event) {
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (Exception e) {
            Diagnostics.logFailure("A listener of " + event.getEventName() + " threw", e);
            return true;
        }
        return !(event instanceof Cancellable cancellable) || !cancellable.isCancelled();
    }

    /**
     * Fires {@code event} and, if a listener cancelled it leaving a message, tells {@code sender}.
     *
     * @param event         the event
     * @param sender        who to tell, may be {@code null}
     * @param cancelMessage literal text left by the listener, may be {@code null}
     * @param format        how the message is written
     * @return {@code true} if the operation may proceed
     */
    public static boolean callAndExplain(Event event, CommandSender sender, String cancelMessage, TextFormat format) {
        if (call(event)) return true;
        if (sender != null && cancelMessage != null && !cancelMessage.isBlank()) {
            sender.sendMessage(CC.component(cancelMessage, format));
        }
        return false;
    }

    /**
     * Fires an event and, if it was cancelled, tells {@code sender} why. The message is read after the
     * event fired, so a listener that cancels it can set it.
     *
     * @param event         the event
     * @param sender        who to tell, may be {@code null}
     * @param cancelMessage supplies the message once the event has fired; a blank message is not sent
     * @param format        the format of the message
     * @return {@code false} if the event was cancelled
     */
    public static boolean callAndExplain(Event event, CommandSender sender, Supplier<String> cancelMessage,
                                         TextFormat format) {
        if (call(event)) return true;
        String message = sender == null ? null : cancelMessage.get();
        if (message != null && !message.isBlank()) {
            sender.sendMessage(CC.component(message, format));
        }
        return false;
    }
}
