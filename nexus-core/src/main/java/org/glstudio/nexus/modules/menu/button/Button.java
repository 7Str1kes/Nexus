package org.glstudio.nexus.modules.menu.button;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * One clickable (or purely decorative) item placed at a slot of a
 * {@link org.glstudio.nexus.modules.menu.Menu}.
 */
public class Button {

    private final ItemStack item;
    private final Consumer<InventoryClickEvent> onClick;
    private final boolean cancelClick;

    /**
     * Creates a button.
     *
     * @param item        the item shown
     * @param onClick     the click handler, {@code null} for none
     * @param cancelClick {@code true} to cancel the click before the handler runs
     */
    public Button(ItemStack item, Consumer<InventoryClickEvent> onClick, boolean cancelClick) {
        this.item = item;
        this.onClick = onClick != null ? onClick : event -> {
        };
        this.cancelClick = cancelClick;
    }

    /**
     * Creates a decorative button.
     *
     * @param item the item shown
     * @return the button
     */
    public static Button of(ItemStack item) {
        return new Button(item, null, true);
    }

    /**
     * Creates a button that runs {@code action} when clicked.
     *
     * @param item   the item shown
     * @param action the action
     * @return the button
     */
    public static Button of(ItemStack item, Runnable action) {
        return new Button(item, event -> action.run(), true);
    }

    /**
     * Creates a button that passes the click event to {@code action}.
     *
     * @param item   the item shown
     * @param action the handler
     * @return the button
     */
    public static Button of(ItemStack item, Consumer<InventoryClickEvent> action) {
        return new Button(item, action, true);
    }

    /**
     * Starts a {@link ButtonBuilder}.
     *
     * @return a new builder
     */
    public static ButtonBuilder builder() {
        return new ButtonBuilder();
    }

    /**
     * Returns a copy of the item, so placing the same button in several slots never shares meta.
     *
     * @return a copy of the item
     */
    public ItemStack getItem() {
        return item.clone();
    }

    /**
     * Returns a copy of the item; an alias of {@link #getItem()}.
     *
     * @return a copy of the item
     */
    public ItemStack getItemStack() {
        return getItem();
    }

    /**
     * Returns whether the click is cancelled before the handler runs.
     *
     * @return {@code true} if cancelled
     */
    public boolean isCancelClick() {
        return cancelClick;
    }

    /**
     * Handles a click on this button.
     *
     * @param event the click
     */
    public void handleClick(InventoryClickEvent event) {
        if (cancelClick) event.setCancelled(true);
        onClick.accept(event);
    }
}
