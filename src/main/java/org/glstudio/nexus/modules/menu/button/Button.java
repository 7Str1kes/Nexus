package org.glstudio.nexus.modules.menu.button;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * One clickable (or purely decorative) item placed at a slot inside a {@link org.glstudio.nexus.modules.menu.Menu}.
 */
public class Button {

    private final ItemStack item;
    private final Consumer<InventoryClickEvent> onClick;
    private final boolean cancelClick;

    public Button(ItemStack item, Consumer<InventoryClickEvent> onClick, boolean cancelClick) {
        this.item = item;
        this.onClick = onClick != null ? onClick : event -> {
        };
        this.cancelClick = cancelClick;
    }

    public static Button of(ItemStack item) {
        return new Button(item, null, true);
    }

    public static Button of(ItemStack item, Runnable action) {
        return new Button(item, event -> action.run(), true);
    }

    public static Button of(ItemStack item, Consumer<InventoryClickEvent> action) {
        return new Button(item, action, true);
    }

    /** Alias of {@code new ButtonBuilder()} — several plugins' own Button used this name. */
    public static ButtonBuilder builder() {
        return new ButtonBuilder();
    }

    /** A clone, so placing the same Button in several slots (e.g. a filler) never aliases meta. */
    public ItemStack getItem() {
        return item.clone();
    }

    public boolean isCancelClick() {
        return cancelClick;
    }

    public void handleClick(InventoryClickEvent event) {
        if (cancelClick) event.setCancelled(true);
        onClick.accept(event);
    }
}
