package org.glstudio.nexus.modules.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies a Bukkit {@link Inventory} as belonging to a Nexus {@link Menu}, so the shared
 * listener can recognise a menu by holder identity rather than by title matching.
 *
 * <p>Built before the inventory it will back exists — {@link org.bukkit.Bukkit#createInventory}
 * needs the holder up front — so {@link #setInventory(Inventory)} is called immediately after.
 */
public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private Inventory inventory;

    MenuHolder(Menu menu) {
        this.menu = menu;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Menu getMenu() {
        return menu;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
