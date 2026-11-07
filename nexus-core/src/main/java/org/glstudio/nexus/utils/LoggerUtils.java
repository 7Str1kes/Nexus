package org.glstudio.nexus.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coloured console logging shared by every class of the plugin that bundles Nexus.
 *
 * <p>Each plugin shades its own copy of Nexus without relocation and Paper resolves a plugin's
 * classes from its own jar first, so the static state here (prefix, debug flag, banner guards)
 * belongs to the plugin that calls {@link #configure(String)}.
 */
public final class LoggerUtils {

    private static final String LINE = "&#8b5cf6&m                                   ";
    private static final String DEFAULT_NAME = "Nexus";

    private static final AtomicBoolean ENABLE_ANNOUNCED = new AtomicBoolean(false);
    private static final AtomicBoolean DISABLE_ANNOUNCED = new AtomicBoolean(false);

    private static volatile String name = DEFAULT_NAME;
    private static volatile String prefix = buildPrefix(DEFAULT_NAME);
    private static volatile boolean debugEnabled = false;

    private static ConsoleCommandSender console;

    private LoggerUtils() {
    }

    /**
     * Sets the name shown in front of every console line.
     *
     * <p>{@link org.glstudio.nexus.Nexus#init(JavaPlugin)} calls this with the plugin name.
     *
     * @param pluginName the name to show; blank values fall back to {@code Nexus}
     */
    public static void configure(String pluginName) {
        String resolved = pluginName == null || pluginName.isBlank() ? DEFAULT_NAME : pluginName;
        name = resolved;
        prefix = buildPrefix(resolved);
    }

    /**
     * Returns the name currently used as console prefix.
     *
     * @return the configured plugin name, or {@code Nexus} if none was configured
     */
    public static String getName() {
        return name;
    }

    /**
     * Enables or disables {@link #logDebug(String, String)} output.
     *
     * @param enabled {@code true} to print debug lines
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /**
     * Returns whether debug output is enabled.
     *
     * @return {@code true} if {@link #logDebug(String, String)} prints
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Allows the enable and disable banners to be printed again.
     *
     * <p>Called by {@link org.glstudio.nexus.Nexus#init(JavaPlugin)} so a plugin re-enabled in the
     * same class loader still announces itself once.
     */
    public static void resetAnnouncements() {
        ENABLE_ANNOUNCED.set(false);
        DISABLE_ANNOUNCED.set(false);
    }

    private static String buildPrefix(String pluginName) {
        return "&#8b5cf6[&#c084fc" + pluginName + "&#8b5cf6] ";
    }

    private static void send(String message) {
        try {
            if (console == null) console = Bukkit.getConsoleSender();
            console.sendMessage(CC.t(message));
        } catch (Throwable t) {
            // No server yet (unit tests, early class loading): stdout is the only sink left.
            System.out.println(CC.strip(message));
        }
    }

    /**
     * Logs a plain line.
     *
     * @param message the text, may contain colour codes
     */
    public static void log(String message) {
        send(prefix + "&#a78bfa" + message);
    }

    /**
     * Logs an informational line.
     *
     * @param message the text, may contain colour codes
     */
    public static void logInfo(String message) {
        send(prefix + "&#c084fc[INFO] &#e0b0ff" + message);
    }

    /**
     * Logs a warning.
     *
     * @param message the text, may contain colour codes
     */
    public static void logWarn(String message) {
        send(prefix + "&#fbbf24[WARN] &#fde68a" + message);
    }

    /**
     * Logs an error without a stack trace.
     *
     * @param message the text, may contain colour codes
     */
    public static void logError(String message) {
        send(prefix + "&#ef4444[ERROR] &#fca5a5" + message);
    }

    /**
     * Logs a success line.
     *
     * @param message the text, may contain colour codes
     */
    public static void logSuccess(String message) {
        send(prefix + "&#10b981[SUCCESS] &#86efac" + message);
    }

    /**
     * Logs a debug line if debug output is enabled.
     *
     * @param category short label of the component that logs
     * @param message  the text, may contain colour codes
     */
    public static void logDebug(String category, String message) {
        if (!debugEnabled) return;
        send(prefix + "&#3b82f6[DEBUG] [" + category + "] &#93c5fd" + message);
    }

    /**
     * Logs an exception with its stack trace.
     *
     * <p>Prefer {@link Diagnostics#logFailure(String, Throwable)}, which also unwraps
     * {@code CompletionException} and summarises the cause chain.
     *
     * @param context where it happened, usually {@code "Class#method"}
     * @param t       the failure
     */
    public static void logException(String context, Throwable t) {
        logError("Exception in [" + context + "]: " + t.getMessage());
        for (StackTraceElement el : t.getStackTrace()) {
            send(prefix + "&#ef4444  at &#fca5a5" + el.toString());
        }
    }

    private static void sendPluginMessage(JavaPlugin plugin, String statusLine) {
        String authors = String.join(", ", plugin.getPluginMeta().getAuthors());
        try {
            log(LINE);
            log("");
            log("&#c084fc&l" + plugin.getName());
            log("");
            log("&#a78bfa» &#f0e8ffAuthor: &#c084fc" + authors);
            log("&#a78bfa» &#f0e8ffVersion: &#c084fc" + plugin.getPluginMeta().getVersion());
            log("");
            log(statusLine);
            log(LINE);
        } catch (Exception e) {
            logException("LoggerUtils#sendPluginMessage", e);
        }
    }

    /**
     * Prints the "enabled" banner, at most once per enable.
     *
     * @param plugin the plugin being enabled
     */
    public static void sendEnable(JavaPlugin plugin) {
        if (!ENABLE_ANNOUNCED.compareAndSet(false, true)) return;
        sendPluginMessage(plugin, "&#10b981&lPlugin enabled successfully.");
    }

    /**
     * Prints the "disabled" banner, at most once per disable.
     *
     * @param plugin the plugin being disabled
     */
    public static void sendDisable(JavaPlugin plugin) {
        if (!DISABLE_ANNOUNCED.compareAndSet(false, true)) return;
        sendPluginMessage(plugin, "&#fd7988&lPlugin disabled successfully.");
    }
}
