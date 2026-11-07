package org.glstudio.nexus;

import org.bukkit.plugin.java.JavaPlugin;
import org.glstudio.nexus.utils.LoggerUtils;

/**
 * Binds the bundled copy of Nexus to the plugin that shades it.
 *
 * <p>Call {@link #init(JavaPlugin)} first thing in {@code onEnable()} and {@link #shutdown(JavaPlugin)}
 * last thing in {@code onDisable()}. The enable banner is not printed here: the plugin prints it
 * with {@link LoggerUtils#sendEnable(JavaPlugin)} once its startup actually succeeded.
 */
public final class Nexus {

    private static volatile JavaPlugin instance;

    private Nexus() {
    }

    /**
     * Binds Nexus to {@code plugin} and names the console prefix after it.
     *
     * <p>Idempotent: calling it again with the same plugin does nothing.
     *
     * @param plugin the plugin that bundles this copy of Nexus
     */
    public static synchronized void init(JavaPlugin plugin) {
        if (instance == plugin) return;
        instance = plugin;
        LoggerUtils.configure(plugin.getName());
        LoggerUtils.resetAnnouncements();
    }

    /**
     * Prints the disable banner once and unbinds the plugin.
     *
     * @param plugin the plugin being disabled
     */
    public static synchronized void shutdown(JavaPlugin plugin) {
        LoggerUtils.sendDisable(plugin);
        if (instance == plugin) instance = null;
    }

    /**
     * Returns the plugin bound by {@link #init(JavaPlugin)}.
     *
     * @return the bound plugin, or {@code null} before {@code init} or after {@code shutdown}
     */
    public static JavaPlugin getInstance() {
        return instance;
    }

    /**
     * Returns whether {@link #init(JavaPlugin)} has been called.
     *
     * @return {@code true} if a plugin is bound
     */
    public static boolean isInitialized() {
        return instance != null;
    }
}
