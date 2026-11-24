package org.glstudio.nexus.modules.menu.extra;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.MenuManager;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.utils.InventoryUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A menu the viewer puts items into, such as a restock or a delivery. The deposit slots hold the
 * viewer's items and are never cleared; every other slot is a button or decoration, and nothing can
 * be put there.
 *
 * <p>Only what {@link #accepts(ItemStack)} allows goes in, whether it is placed, swapped with a
 * hotbar key, dragged or shift clicked from the viewer's inventory; a shift click only ever fills the
 * deposit slots. When the menu closes, or {@link #finish(boolean)} is called, everything that is not
 * a button is taken out of the menu, once: the accepted items go to {@link #deposit(List)}, and what
 * it hands back, with anything else, goes back to the viewer, dropped at their feet if it does not
 * fit. A viewer who is leaving the server gets everything back without a deposit. The items always
 * leave the menu before {@link #deposit(List)} runs, so they never exist in two places.
 *
 * <p>The menu is drawn again on the tick after each change, so the buttons can show what is in it.
 */
public abstract class DepositMenu extends Menu {

    private final Set<Integer> depositSlots;
    private boolean finished;
    private boolean refreshQueued;

    /**
     * Creates the menu.
     *
     * @param manager      the plugin's menu manager
     * @param viewer       the player who deposits
     * @param title        the title
     * @param size         the size
     * @param depositSlots the slots items go into; slots outside the menu are ignored
     */
    protected DepositMenu(MenuManager manager, Player viewer, String title, int size, Collection<Integer> depositSlots) {
        super(manager, viewer, title, size);
        Set<Integer> slots = new TreeSet<>();
        for (int slot : depositSlots) {
            if (slot >= 0 && slot < getSize()) slots.add(slot);
        }
        this.depositSlots = Collections.unmodifiableSet(slots);
        // Items move freely; onClick and onPlayerInventoryClick decide what may happen.
        setAllowInteract(true);
    }

    /**
     * Returns whether a stack may go into the deposit slots.
     *
     * @param item the stack, never empty
     * @return {@code true} to accept it; anything by default
     */
    protected boolean accepts(ItemStack item) {
        return true;
    }

    /**
     * Returns the buttons and decoration. Those on deposit slots are left out.
     *
     * @return the buttons by slot, built again on every draw
     */
    protected abstract Map<Integer, Button> buttons();

    /**
     * Takes the deposited items. Called once, on the main thread, after they left the menu; never for
     * a viewer who is leaving the server, nor when nothing was deposited.
     *
     * @param items the accepted items, as stacks
     * @return what goes back to the viewer; empty if everything was taken
     */
    protected abstract List<ItemStack> deposit(List<ItemStack> items);

    /**
     * Returns the deposit slots.
     *
     * @return the slots, in order
     */
    public final Set<Integer> getDepositSlots() {
        return depositSlots;
    }

    /**
     * Returns copies of what is in the deposit slots now.
     *
     * @return the stacks, without empty slots
     */
    protected final List<ItemStack> deposited() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : depositSlots) {
            ItemStack stack = getInventory().getItem(slot);
            if (!isEmpty(stack)) items.add(stack.clone());
        }
        return items;
    }

    /**
     * Puts items into the deposit slots, filling stacks of the same item first.
     *
     * @param item the items; not changed
     * @return how many did not fit
     */
    protected final int addToDeposit(ItemStack item) {
        Inventory inventory = getInventory();
        int max = Math.max(1, Math.min(item.getMaxStackSize(), inventory.getMaxStackSize()));
        int left = item.getAmount();
        for (int slot : depositSlots) {
            if (left <= 0) break;
            ItemStack there = inventory.getItem(slot);
            if (isEmpty(there) || !there.isSimilar(item) || there.getAmount() >= max) continue;
            int add = Math.min(left, max - there.getAmount());
            there.setAmount(there.getAmount() + add);
            inventory.setItem(slot, there);
            left -= add;
        }
        for (int slot : depositSlots) {
            if (left <= 0) break;
            if (!isEmpty(inventory.getItem(slot))) continue;
            ItemStack stack = item.clone();
            stack.setAmount(Math.min(max, left));
            inventory.setItem(slot, stack);
            left -= stack.getAmount();
        }
        return left;
    }

    /**
     * Empties the menu, once; later calls, and the close that follows, do nothing.
     *
     * @param deposit {@code true} to hand the accepted items to {@link #deposit(List)}; {@code false}
     *                to give everything back
     */
    protected final void finish(boolean deposit) {
        if (finished) return;
        finished = true;
        Inventory inventory = getInventory();
        Map<Integer, Button> buttons = getButtons();
        List<ItemStack> accepted = new ArrayList<>();
        List<ItemStack> back = new ArrayList<>();
        for (int slot = 0; slot < getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isEmpty(stack)) continue;
            Button button = buttons.get(slot);
            if (button != null && !depositSlots.contains(slot) && stack.isSimilar(button.getItem())) continue;
            // Anything else is the viewer's, even outside the deposit slots.
            ItemStack taken = stack.clone();
            inventory.setItem(slot, null);
            (accepts(taken) ? accepted : back).add(taken);
        }
        if (deposit && viewer.isConnected() && !accepted.isEmpty()) {
            back.addAll(deposit(accepted));
        } else {
            back.addAll(accepted);
        }
        back.removeIf(DepositMenu::isEmpty);
        if (!back.isEmpty()) InventoryUtils.giveOrDrop(viewer, back.toArray(ItemStack[]::new));
    }

    /**
     * Returns whether the menu was emptied.
     *
     * @return {@code true} once {@link #finish(boolean)} ran
     */
    public final boolean isFinished() {
        return finished;
    }

    @Override
    protected final void render() {
        Map<Integer, Button> buttons = new HashMap<>(buttons());
        buttons.keySet().removeIf(depositSlots::contains);
        place(buttons);
    }

    @Override
    protected final void onClose() {
        finish(true);
    }

    @Override
    protected final void onClick(InventoryClickEvent event) {
        if (finished || !allowed(event)) {
            event.setCancelled(true);
            return;
        }
        refreshLater();
    }

    @Override
    protected final void onPlayerInventoryClick(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        // A double click gathers matching items from the whole view, buttons included.
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (action != InventoryAction.MOVE_TO_OTHER_INVENTORY) return;
        // Left alone, a shift click puts the stack in any empty slot of the menu; place it here instead.
        event.setCancelled(true);
        ItemStack moving = event.getCurrentItem();
        Inventory clicked = event.getClickedInventory();
        if (finished || isEmpty(moving) || clicked == null || !accepts(moving)) return;
        int left = addToDeposit(moving);
        if (left == moving.getAmount()) return;
        ItemStack rest = moving.clone();
        rest.setAmount(left);
        clicked.setItem(event.getSlot(), left > 0 ? rest : null);
        refreshLater();
    }

    @Override
    public final boolean shouldCancelDrag(InventoryDragEvent event) {
        boolean intoMenu = false;
        for (int raw : event.getRawSlots()) {
            if (raw >= getSize()) continue;
            if (finished || !depositSlots.contains(raw) || !isAccepted(event.getOldCursor())) return true;
            intoMenu = true;
        }
        if (intoMenu) refreshLater();
        return false;
    }

    /** Whether a click on a slot of the menu may go ahead. */
    private boolean allowed(InventoryClickEvent event) {
        if (!depositSlots.contains(event.getRawSlot())) return false;
        return switch (event.getAction()) {
            case NOTHING, PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, DROP_ALL_SLOT, DROP_ONE_SLOT,
                 MOVE_TO_OTHER_INVENTORY -> true;
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> isAccepted(event.getCursor());
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> isAccepted(hotbarItem(event));
            // Bundles, creative clones and anything new: refused rather than guessed.
            default -> false;
        };
    }

    private ItemStack hotbarItem(InventoryClickEvent event) {
        if (event.getClick() == ClickType.SWAP_OFFHAND) return viewer.getInventory().getItemInOffHand();
        int button = event.getHotbarButton();
        return button < 0 ? null : viewer.getInventory().getItem(button);
    }

    private boolean isAccepted(ItemStack item) {
        return isEmpty(item) || accepts(item);
    }

    private void refreshLater() {
        if (refreshQueued || !plugin.isEnabled()) return;
        refreshQueued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshQueued = false;
            if (!isDestroyed() && !finished) update();
        });
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
