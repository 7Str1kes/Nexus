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
import org.glstudio.nexus.utils.Diagnostics;

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
 * {@link org.glstudio.nexus.modules.menu.listener.MenuListener} recognises it by inventory identity,
 * and tracked by a {@link MenuManager} so it can be looked up, refreshed and torn down from outside a
 * click event.
 *
 * <p>Subclasses implement {@link #render()} to (re)populate the buttons, typically via
 * {@link #place(int, Button)}, every time the menu is opened or {@link #update()} is called.
 */
public abstract class Menu {

    /** Default minimum time between two handled clicks of the same player. */
    public static final long DEFAULT_CLICK_COOLDOWN_MILLIS = 200L;

    /** The owning plugin. */
    protected final JavaPlugin plugin;
    /** The manager tracking this menu. */
    protected final MenuManager manager;
    /** The only player who sees this menu. */
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

    /**
     * Creates the menu and its inventory. Nothing is shown until {@link #open()}.
     *
     * @param manager the plugin's menu manager
     * @param viewer  the player who will see it
     * @param title   the title, legacy colour codes allowed
     * @param size    the size; rounded to a multiple of 9 between 9 and 54
     */
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

    /**
     * Renders the menu and shows it to the viewer. Not final: a subclass may add a pre-open guard
     * (permission, cooldown) and then call {@code super.open()}.
     */
    public void open() {
        if (destroyed) return;
        render();
        manager.register(viewer.getUniqueId(), this);
        viewer.openInventory(inventory);
        try {
            onOpen();
        } catch (Exception ex) {
            Diagnostics.logFailure("Menu#onOpen (" + getClass().getSimpleName() + ")", ex);
        }
        startAutoRefreshIfNeeded();
    }

    /**
     * Opens on the next tick. Required when opening from inside another menu's click handler:
     * opening while the click is still being processed leaves client and server disagreeing about
     * what is open.
     */
    public final void openLater() {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, this::open);
    }

    /**
     * Re-renders the open menu in place. Destroys it instead if the viewer went offline.
     */
    public final void update() {
        if (destroyed) return;
        if (!viewer.isOnline()) {
            // A queued update (auto-refresh tick, a late async callback) can fire after the viewer
            // disconnected; destroy instead of rendering into a dead session.
            manager.unregister(viewer.getUniqueId());
            return;
        }
        render();
        viewer.updateInventory();
    }

    /**
     * Closes the viewer's inventory, which destroys this menu.
     */
    public final void close() {
        viewer.closeInventory();
    }

    /**
     * Called only by {@link MenuManager}, once, when this menu stops being the viewer's active menu.
     */
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
            Diagnostics.logFailure("Menu#onClose (" + getClass().getSimpleName() + ")", ex);
        }
        buttons.clear();
    }

    /**
     * Called after the inventory is shown.
     */
    protected void onOpen() {
    }

    /**
     * Called once when the menu is closed, replaced or its viewer leaves.
     */
    protected void onClose() {
    }

    /**
     * Called before button dispatch on every click inside the menu, for cross-cutting logic.
     *
     * @param event the click
     */
    protected void onClick(InventoryClickEvent event) {
    }

    /**
     * Gate for the auto-refresh ticker; override to skip a redraw when nothing changed.
     *
     * @return {@code true} to redraw on this tick
     */
    protected boolean shouldRefresh() {
        return true;
    }

    /**
     * Minimum time between two handled clicks of the viewer; faster clicks are cancelled. Override
     * to tune it for menus whose buttons start slow work.
     *
     * @return the cooldown in milliseconds, {@code 0} to disable
     */
    public long getClickCooldownMillis() {
        return DEFAULT_CLICK_COOLDOWN_MILLIS;
    }

    /**
     * Rebuilds the buttons (and any decorative content) from scratch. Called by {@link #open()} and
     * {@link #update()}.
     */
    protected abstract void render();

    // ─── click/drag dispatch (called by MenuListener) ───────────────────────────

    /**
     * Runs {@link #onClick(InventoryClickEvent)} and the clicked button. Called by the listener.
     *
     * @param event the click inside the menu
     */
    public final void dispatchClick(InventoryClickEvent event) {
        try {
            onClick(event);
            Button button = buttons.get(event.getRawSlot());
            if (button != null) {
                button.handleClick(event);
            }
        } catch (Exception ex) {
            Diagnostics.logFailure("Menu click (" + getClass().getSimpleName() + ")", ex);
        }
    }

    /**
     * Decides whether a drag is cancelled. Not final: a subclass with its own per-slot drag rule may
     * override.
     *
     * @param event the drag
     * @return {@code true} to cancel it
     */
    public boolean shouldCancelDrag(InventoryDragEvent event) {
        if (allowInteract) return false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < inventory.getSize() && !interactiveSlots.contains(rawSlot)) return true;
        }
        return false;
    }

    // ─── interaction policy ──────────────────────────────────────────────────────

    /**
     * Returns whether clicks and drags are never cancelled automatically.
     *
     * @return {@code true} if the menu allows free interaction
     */
    public boolean isAllowInteract() {
        return allowInteract;
    }

    /**
     * Lets the viewer move items freely; clicks and drags are no longer cancelled automatically.
     *
     * @param allowInteract {@code true} to allow it
     */
    protected void setAllowInteract(boolean allowInteract) {
        this.allowInteract = allowInteract;
    }

    /**
     * Returns whether {@code slot} is exempt from automatic cancellation.
     *
     * @param slot the raw slot
     * @return {@code true} if interactive
     */
    public boolean isInteractiveSlot(int slot) {
        return interactiveSlots.contains(slot);
    }

    /**
     * Exempts slots from automatic cancellation without opening the whole menu.
     *
     * @param slots raw slots
     */
    protected void addInteractiveSlots(int... slots) {
        for (int slot : slots) interactiveSlots.add(slot);
    }

    // ─── auto refresh ────────────────────────────────────────────────────────────

    /**
     * Enables or disables periodic {@link #update()} with the current period.
     *
     * @param enabled {@code true} to refresh
     */
    protected final void setAutoRefresh(boolean enabled) {
        setAutoRefresh(enabled, refreshPeriodTicks);
    }

    /**
     * Enables or disables periodic {@link #update()}.
     *
     * @param enabled     {@code true} to refresh
     * @param periodTicks the period, in ticks
     */
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

    /**
     * Removes every button and item.
     */
    protected final void clear() {
        buttons.clear();
        inventory.clear();
    }

    /**
     * Places a button. Out-of-range slots are skipped with a one-time warning.
     *
     * @param slot   the slot
     * @param button the button
     */
    protected final void place(int slot, Button button) {
        if (slot < 0 || slot >= inventory.getSize()) {
            manager.warnOnce(getClass(), slot);
            return;
        }
        buttons.put(slot, button);
        inventory.setItem(slot, button.getItem());
    }

    /**
     * Places the same button in several slots.
     *
     * @param slots  the slots
     * @param button the button
     */
    protected final void place(Collection<Integer> slots, Button button) {
        for (int slot : slots) place(slot, button);
    }

    /**
     * Places several buttons.
     *
     * @param slotted buttons by slot
     */
    protected final void place(Map<Integer, Button> slotted) {
        slotted.forEach(this::place);
    }

    /**
     * Fills every empty slot with a decorative item.
     *
     * @param fillerItem the item
     */
    protected final void fillEmpty(ItemStack fillerItem) {
        Button filler = Button.of(fillerItem);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) place(i, filler);
        }
    }

    /**
     * Fills the outer border with a decorative item.
     *
     * @param fillerItem the item
     */
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

    /**
     * Fills a row with a decorative item.
     *
     * @param row  the row, from 0
     * @param item the item
     */
    protected final void setRow(int row, ItemStack item) {
        Button button = Button.of(item);
        for (int col = 0; col < 9; col++) place(row * 9 + col, button);
    }

    /**
     * Fills a column with a decorative item.
     *
     * @param col  the column, from 0
     * @param item the item
     */
    protected final void setColumn(int col, ItemStack item) {
        int rows = inventory.getSize() / 9;
        Button button = Button.of(item);
        for (int row = 0; row < rows; row++) place(row * 9 + col, button);
    }

    /**
     * Returns the interior slots (excluding the border), a sensible default content area.
     *
     * @return the slots, row by row
     */
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

    /**
     * Returns the backing inventory.
     *
     * @return the inventory
     */
    public final Inventory getInventory() {
        return inventory;
    }

    /**
     * Returns the size in slots.
     *
     * @return the size
     */
    public final int getSize() {
        return inventory.getSize();
    }

    /**
     * Returns the viewer.
     *
     * @return the player
     */
    public final Player getViewer() {
        return viewer;
    }

    /**
     * Returns the owning plugin.
     *
     * @return the plugin
     */
    public final JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * Returns the manager tracking this menu.
     *
     * @return the manager
     */
    public final MenuManager getManager() {
        return manager;
    }

    /**
     * Returns the placed buttons.
     *
     * @return an unmodifiable view, by slot
     */
    public final Map<Integer, Button> getButtons() {
        return Collections.unmodifiableMap(buttons);
    }

    /**
     * Returns whether the menu was closed and torn down.
     *
     * @return {@code true} once destroyed
     */
    public final boolean isDestroyed() {
        return destroyed;
    }
}
