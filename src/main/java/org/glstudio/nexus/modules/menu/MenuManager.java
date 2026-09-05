package org.glstudio.nexus.modules.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.glstudio.nexus.modules.menu.listener.MenuListener;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-plugin registry of "which {@link Menu} is this player currently looking at" plus the single
 * {@link MenuListener} that drives every menu the plugin opens. One instance per plugin is enough
 * — create it once (e.g. in {@code onEnable()}) and call {@link #closeAll()} from
 * {@code onDisable()}/reload so no menu, task or listener registration outlives the plugin.
 */
public class MenuManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Menu> menus = new ConcurrentHashMap<>();
    private final Set<String> warnedOnce = ConcurrentHashMap.newKeySet();

    public MenuManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), plugin);
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    /** Registers a menu as the viewer's active one, destroying whatever menu it replaces. */
    void register(UUID uuid, Menu menu) {
        Menu previous = menus.put(uuid, menu);
        if (previous != null && previous != menu) {
            previous.destroy();
        }
    }

    /** Removes and destroys the viewer's active menu, if any. Safe to call when there is none. */
    public void unregister(UUID uuid) {
        Menu menu = menus.remove(uuid);
        if (menu != null) menu.destroy();
    }

    public Menu getOpenMenu(UUID uuid) {
        return menus.get(uuid);
    }

    public Menu getOpenMenu(Player player) {
        return menus.get(player.getUniqueId());
    }

    /** Force-closes every menu this manager is tracking — call from onDisable()/reload. */
    public void closeAll() {
        for (UUID uuid : new ArrayList<>(menus.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            } else {
                unregister(uuid);
            }
        }
    }

    /** Resets the out-of-range-slot warning dedup, e.g. after a config reload changed layouts. */
    public void resetWarnings() {
        warnedOnce.clear();
    }

    void warnOnce(Class<?> menuClass, int slot) {
        if (warnedOnce.add(menuClass.getName() + ":" + slot)) {
            plugin.getLogger().warning("Menu " + menuClass.getSimpleName()
                    + " attempted to place a button at out-of-range slot " + slot + " — check its config.");
        }
    }
}
