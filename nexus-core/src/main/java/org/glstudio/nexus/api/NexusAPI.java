package org.glstudio.nexus.api;

import org.bukkit.plugin.java.JavaPlugin;
import org.glstudio.nexus.Nexus;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.modules.menu.MenuManager;
import org.glstudio.nexus.modules.menu.chat.ChatInputManager;

/**
 * Factories for the per-plugin Nexus services.
 *
 * <p>Every factory also calls {@link Nexus#init(JavaPlugin)}, so a plugin that only uses these
 * factories still gets its own console prefix.
 */
public final class NexusAPI {

    private static final NexusAPI INSTANCE = new NexusAPI();

    private NexusAPI() {
    }

    /**
     * Returns the shared factory instance.
     *
     * @return the singleton
     */
    public static NexusAPI get() {
        return INSTANCE;
    }

    /**
     * Creates the command manager of {@code plugin}.
     *
     * @param plugin the owning plugin
     * @return a new command manager
     */
    public CommandManager createCommandManager(JavaPlugin plugin) {
        Nexus.init(plugin);
        return new CommandManager(plugin);
    }

    /**
     * Creates the menu manager of {@code plugin} and registers its listener.
     *
     * @param plugin the owning plugin
     * @return a new menu manager
     */
    public MenuManager createMenuManager(JavaPlugin plugin) {
        Nexus.init(plugin);
        return new MenuManager(plugin);
    }

    /**
     * Creates the chat input manager of {@code plugin} and registers its listener.
     *
     * @param plugin the owning plugin
     * @return a new chat input manager
     */
    public ChatInputManager createChatInputManager(JavaPlugin plugin) {
        Nexus.init(plugin);
        return new ChatInputManager(plugin);
    }
}
