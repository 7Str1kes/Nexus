package org.glstudio.nexus.modules.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.LoggerUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for a single-viewer inventory GUI, backed by a {@link MenuHolder} so the shared
 * {@link org.glstudio.nexus.modules.menu.listener.MenuListener} can recognise it by inventory
 * identity, and tracked by a {@link MenuManager} so it can be looked up, refreshed and torn down
 * from outside a click event.
 *
 * <p>Subclasses implement {@link #render()} to (re)populate {@link #getButtons()} — typically via
 * {@link #place(int, Button)} — every time the menu is opened or {@link #update()} is called.
 */
public abstract class Menu {

    protected final JavaPlugin plugin;
    protected final MenuManager manager;
    protected final Player viewer;

    private final MenuHolder holder;
    private final Inventory inventory;

    private final Map<Integer, Button> buttons = new HashMap<>();
    private final Set<Integer> interactiveSlots = new HashSet<>();
    private boolean allowInteract = false;

    private boolean autoRefreshEnabled = false;
    private long refreshPeriodTicks = 10L;
    private BukkitTask refreshTask;

    private boolean destroyed = false;

    protected Menu(MenuManager manager, Player viewer, String title, int size) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.viewer = viewer;

        int normalized = normalizeSize(size);
        this.holder = new MenuHolder(this);
        this.inventory = Bukkit.createInventory(holder, normalized, CC.component(title));
        this.holder.setInventory(inventory);
    }

    private int normalizeSize(int size) {
        int clamped = Math.max(9, Math.min(54, size));
        int rounded = ((clamped + 8) / 9) * 9;
        if (rounded != size) {
            plugin.getLogger().warning("Menu " + getClass().getSimpleName() + " requested an invalid size ("
                    + size + "), normalized to " + rounded + ".");
        }
        return rounded;
    }

    // ─── lifecycle ───────────────────────────────────────────────────────────────

    public final void open() {
        if (destroyed) return;
        render();
        manager.register(viewer.getUniqueId(), this);
        viewer.openInventory(inventory);
        try {
            onOpen();
        } catch (Exception ex) {
            LoggerUtils.logException("Menu#onOpen (" + getClass().getSimpleName() + ")", ex);
        }
        startAutoRefreshIfNeeded();
    }

    /** Opens next tick — required when opening a menu from inside another menu's click handler. */
    public final void openLater() {
        Bukkit.getScheduler().runTask(plugin, this::open);
    }

    public final void update() {
        if (destroyed) return;
        render();
        viewer.updateInventory();
    }

    public final void close() {
        viewer.closeInventory();
    }

    /** Called only by {@link MenuManager}, once, when this menu stops being the viewer's active menu. */
    final void destroy() {
        if (destroyed) return;
        destroyed = true;
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        try {
            onClose();
        } catch (Exception ex) {
            LoggerUtils.logException("Menu#onClose (" + getClass().getSimpleName() + ")", ex);
        }
        buttons.clear();
    }

    protected void onOpen() {
    }

    protected void onClose() {
    }

    /** Hook called before button dispatch on every click; override for cross-cutting per-menu logic. */
    protected void onClick(InventoryClickEvent event) {
    }

    /** Gate for the auto-refresh ticker; override to skip a redraw when nothing changed. */
    protected boolean shouldRefresh() {
        return true;
    }

    /** Rebuilds {@link #buttons} (and any decorative content) from scratch. Called by open()/update(). */
    protected abstract void render();

    // ─── click/drag dispatch (called by MenuListener) ───────────────────────────

    public final void dispatchClick(InventoryClickEvent event) {
        try {
            onClick(event);
            Button button = buttons.get(event.getRawSlot());
            if (button != null) {
                button.handleClick(event);
            }
        } catch (Exception ex) {
            LoggerUtils.logException("Menu click (" + getClass().getSimpleName() + ")", ex);
        }
    }

    public final boolean shouldCancelDrag(InventoryDragEvent event) {
        if (allowInteract) return false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < inventory.getSize() && !interactiveSlots.contains(rawSlot)) return true;
        }
        return false;
    }

    // ─── interaction policy ──────────────────────────────────────────────────────

    public boolean isAllowInteract() {
        return allowInteract;
    }

    /** When true, clicks/drags inside this menu are never auto-cancelled by the listener. */
    protected void setAllowInteract(boolean allowInteract) {
        this.allowInteract = allowInteract;
    }

    public boolean isInteractiveSlot(int slot) {
        return interactiveSlots.contains(slot);
    }

    /** Marks specific slots as exempt from auto-cancellation without opting the whole menu in. */
    protected void addInteractiveSlots(int... slots) {
        for (int slot : slots) interactiveSlots.add(slot);
    }

    // ─── auto refresh ────────────────────────────────────────────────────────────

    protected final void setAutoRefresh(boolean enabled) {
        setAutoRefresh(enabled, refreshPeriodTicks);
    }

    protected final void setAutoRefresh(boolean enabled, long periodTicks) {
        this.autoRefreshEnabled = enabled;
        this.refreshPeriodTicks = periodTicks;
        if (!enabled && refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void startAutoRefreshIfNeeded() {
        if (!autoRefreshEnabled || destroyed || refreshTask != null) return;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (destroyed) return;
            if (shouldRefresh()) update();
        }, refreshPeriodTicks, refreshPeriodTicks);
    }

    // ─── rendering helpers ───────────────────────────────────────────────────────

    protected final void clear() {
        buttons.clear();
        inventory.clear();
    }

    protected final void place(int slot, Button button) {
        if (slot < 0 || slot >= inventory.getSize()) {
            manager.warnOnce(getClass(), slot);
            return;
        }
        buttons.put(slot, button);
        inventory.setItem(slot, button.getItem());
    }

    protected final void place(Collection<Integer> slots, Button button) {
        for (int slot : slots) place(slot, button);
    }

    protected final void place(Map<Integer, Button> slotted) {
        slotted.forEach(this::place);
    }

    protected final void fillEmpty(ItemStack fillerItem) {
        Button filler = Button.of(fillerItem);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) place(i, filler);
        }
    }

    protected final void fillBorder(ItemStack fillerItem) {
        int size = inventory.getSize();
        int rows = size / 9;
        Button filler = Button.of(fillerItem);
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) place(i, filler);
        }
    }

    protected final void setRow(int row, ItemStack item) {
        Button button = Button.of(item);
        for (int col = 0; col < 9; col++) place(row * 9 + col, button);
    }

    protected final void setColumn(int col, ItemStack item) {
        int rows = inventory.getSize() / 9;
        Button button = Button.of(item);
        for (int row = 0; row < rows; row++) place(row * 9 + col, button);
    }

    /** The interior slots (excluding the outer border) — a sensible default content area. */
    protected final List<Integer> getCenterSlots() {
        int rows = inventory.getSize() / 9;
        List<Integer> result = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                result.add(row * 9 + col);
            }
        }
        return result;
    }

    // ─── accessors ───────────────────────────────────────────────────────────────

    public final Inventory getInventory() {
        return inventory;
    }

    public final Player getViewer() {
        return viewer;
    }

    public final JavaPlugin getPlugin() {
        return plugin;
    }

    public final MenuManager getManager() {
        return manager;
    }

    public final Map<Integer, Button> getButtons() {
        return Collections.unmodifiableMap(buttons);
    }

    public final boolean isDestroyed() {
        return destroyed;
    }
}
