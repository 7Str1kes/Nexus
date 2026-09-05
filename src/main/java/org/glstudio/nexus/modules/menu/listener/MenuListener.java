package org.glstudio.nexus.modules.menu.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.MenuManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single Bukkit listener behind every {@link Menu} a {@link MenuManager} tracks. One instance
 * is registered per {@code MenuManager} (i.e. per plugin) — never per menu — so opening hundreds
 * of menus never registers hundreds of listeners.
 */
public final class MenuListener implements Listener {

    private static final long CLICK_COOLDOWN_MILLIS = 200L;

    private final MenuManager manager;
    private final Map<UUID, Long> lastClickAt = new ConcurrentHashMap<>();

    public MenuListener(MenuManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Menu menu = manager.getOpenMenu(player.getUniqueId());
        if (menu == null) return;

        if (!menu.getInventory().equals(event.getView().getTopInventory())) {
            // Stale registration — another inventory replaced this one without going through
            // the manager. Self-heal instead of acting on a menu that is no longer on screen.
            manager.unregister(player.getUniqueId());
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean insideMenu = rawSlot >= 0 && rawSlot < menu.getInventory().getSize();

        if (!menu.isAllowInteract() && !(insideMenu && menu.isInteractiveSlot(rawSlot))) {
            event.setCancelled(true);
        }

        if (!insideMenu) return;

        if (isClickingTooFast(player)) {
            event.setCancelled(true);
            return;
        }

        menu.dispatchClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Menu menu = manager.getOpenMenu(player.getUniqueId());
        if (menu == null) return;

        if (menu.shouldCancelDrag(event)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Menu menu = manager.getOpenMenu(player.getUniqueId());
        if (menu != null && menu.getInventory().equals(event.getInventory())) {
            manager.unregister(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        manager.unregister(uuid);
        lastClickAt.remove(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Defends against a stale menu surviving a fast relog with the same UUID.
        manager.unregister(event.getPlayer().getUniqueId());
    }

    private boolean isClickingTooFast(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastClickAt.put(player.getUniqueId(), now);
        return previous != null && (now - previous) < CLICK_COOLDOWN_MILLIS;
    }
}
