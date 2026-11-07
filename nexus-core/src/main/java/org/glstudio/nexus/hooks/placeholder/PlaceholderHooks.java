package org.glstudio.nexus.hooks.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.glstudio.nexus.modules.menu.placeholder.Placeholders;
import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;

import java.util.function.Supplier;

/**
 * Registers PlaceholderAPI expansions only when PlaceholderAPI is installed. This class itself never
 * touches PlaceholderAPI types until that check passed, so it is safe to call on any server.
 */
public final class PlaceholderHooks {

    private PlaceholderHooks() {
    }

    /**
     * Creates and registers an expansion if PlaceholderAPI is installed.
     *
     * @param factory creates the expansion, e.g. {@code () -> new TeleportsExpansion(plugin)}; only
     *                called when PlaceholderAPI is present
     * @return {@code true} if registered
     */
    public static boolean registerIfPresent(Supplier<?> factory) {
        if (!Placeholders.isPapiPresent()) return false;
        try {
            boolean registered = Registrar.register(factory.get());
            if (registered) LoggerUtils.logSuccess("PlaceholderAPI expansion registered.");
            return registered;
        } catch (Throwable e) {
            Diagnostics.logFailure("PlaceholderHooks#registerIfPresent", e);
            return false;
        }
    }

    /**
     * Unregisters an expansion created by {@link #registerIfPresent(Supplier)}.
     *
     * @param expansion the expansion, may be {@code null}
     */
    public static void unregister(Object expansion) {
        if (expansion == null || !Placeholders.isPapiPresent()) return;
        try {
            Registrar.unregister(expansion);
        } catch (Throwable e) {
            Diagnostics.logFailure("PlaceholderHooks#unregister", e);
        }
    }

    private static final class Registrar {
        static boolean register(Object expansion) {
            return ((PlaceholderExpansion) expansion).register();
        }

        static void unregister(Object expansion) {
            PlaceholderExpansion placeholder = (PlaceholderExpansion) expansion;
            if (placeholder.isRegistered()) placeholder.unregister();
        }
    }
}
