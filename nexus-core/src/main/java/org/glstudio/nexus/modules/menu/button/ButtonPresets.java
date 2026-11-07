package org.glstudio.nexus.modules.menu.button;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.glstudio.nexus.modules.menu.paginated.PaginatedMenu;

import java.util.List;

/**
 * Buttons every menu needs. Texts are parameters, so they come from the plugin's language or menu
 * files rather than from here.
 */
public final class ButtonPresets {

    private ButtonPresets() {
    }

    /**
     * A nameless decorative item with every tooltip extra hidden.
     *
     * @param material the material; {@code null} for black stained glass
     * @return the button
     */
    public static Button filler(Material material) {
        return ButtonBuilder.of(material == null ? Material.BLACK_STAINED_GLASS_PANE : material)
                .name(" ").hideAll().build();
    }

    /**
     * A placeholder shown while asynchronous content loads.
     *
     * @param name legacy name, e.g. {@code &eLoading...}
     * @param lore legacy lore lines
     * @return the button
     */
    public static Button loading(String name, String... lore) {
        ButtonBuilder builder = ButtonBuilder.of(Material.CLOCK).name(name).hideAll();
        if (lore.length > 0) builder.lore(lore);
        return builder.build();
    }

    /**
     * A button that closes the menu.
     *
     * @param icon the item shown
     * @return the button
     */
    public static Button close(ItemStack icon) {
        return ButtonBuilder.of(icon).onClickPlayer(Player::closeInventory).build();
    }

    /**
     * A button that goes to the previous page.
     *
     * @param menu the menu to page
     * @param icon the item shown
     * @return the button
     */
    public static Button previous(PaginatedMenu<?> menu, ItemStack icon) {
        return ButtonBuilder.of(icon).onClick(menu::previousPage).build();
    }

    /**
     * A button that goes to the next page.
     *
     * @param menu the menu to page
     * @param icon the item shown
     * @return the button
     */
    public static Button next(PaginatedMenu<?> menu, ItemStack icon) {
        return ButtonBuilder.of(icon).onClick(menu::nextPage).build();
    }

    /**
     * An item with a legacy name and lore.
     *
     * @param material the material
     * @param name     the name
     * @param lore     the lore lines, may be {@code null}
     * @return the item
     */
    public static ItemStack item(Material material, String name, List<String> lore) {
        ButtonBuilder builder = ButtonBuilder.of(material).name(name);
        if (lore != null) builder.lore(lore);
        return builder.toItemStack();
    }
}
