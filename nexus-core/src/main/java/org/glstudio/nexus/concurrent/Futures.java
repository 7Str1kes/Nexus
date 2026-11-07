package org.glstudio.nexus.concurrent;

import org.bukkit.command.CommandSender;
import org.glstudio.nexus.utils.Diagnostics;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Terminal error handling for {@link CompletableFuture} chains.
 *
 * <p>A failed future that nobody observes is silent. Every chain whose result is not consumed
 * should end in {@link #log(CompletableFuture, String)} or one of the {@code guard} methods. None of
 * them swallow the failure: the returned future still completes exceptionally, so callers that
 * chain further work see it.
 */
public final class Futures {

    private static volatile BiConsumer<CommandSender, Throwable> failureNotifier = (sender, error) -> {
    };

    private Futures() {
    }

    /**
     * Sets what {@link #guard(CompletableFuture, String, CommandSender)} tells the sender when the
     * future fails, typically a translated {@code errors.database-error} message.
     *
     * <p>The notifier may run on any thread; sending a chat message is thread-safe on Paper.
     *
     * @param notifier receives the sender and the unwrapped failure
     */
    public static void setFailureNotifier(BiConsumer<CommandSender, Throwable> notifier) {
        failureNotifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Logs the failure of {@code future}, if any.
     *
     * @param future  the future to observe
     * @param context where it was started, usually {@code "Class#method"}
     * @param <T>     the result type
     * @return a future that completes like {@code future}
     */
    public static <T> CompletableFuture<T> log(CompletableFuture<T> future, String context) {
        return future.whenComplete((result, error) -> {
            if (error != null) Diagnostics.logFailure(context, error);
        });
    }

    /**
     * Logs the failure of {@code future}, if any, and then runs {@code onFailure}.
     *
     * @param future    the future to observe
     * @param context   where it was started, usually {@code "Class#method"}
     * @param onFailure receives the unwrapped failure; may run on any thread
     * @param <T>       the result type
     * @return a future that completes like {@code future}
     */
    public static <T> CompletableFuture<T> guard(CompletableFuture<T> future, String context,
                                                 Consumer<Throwable> onFailure) {
        return future.whenComplete((result, error) -> {
            if (error == null) return;
            Diagnostics.logFailure(context, error);
            if (onFailure != null) onFailure.accept(Diagnostics.unwrap(error));
        });
    }

    /**
     * Logs the failure of {@code future}, if any, and tells {@code sender} through the notifier set
     * with {@link #setFailureNotifier(BiConsumer)}.
     *
     * @param future  the future to observe
     * @param context where it was started, usually {@code "Class#method"}
     * @param sender  who to notify, or {@code null} to only log
     * @param <T>     the result type
     * @return a future that completes like {@code future}
     */
    public static <T> CompletableFuture<T> guard(CompletableFuture<T> future, String context,
                                                 CommandSender sender) {
        return guard(future, context, error -> {
            if (sender != null) failureNotifier.accept(sender, error);
        });
    }
}
