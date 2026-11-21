package org.glstudio.nexus.utils.serializer;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Writes one item as text for a database row or a file: a plain item as its material name, anything
 * with meta encoded with {@link ItemCodec}, so rows stay readable by hand for common items. The
 * amount is not kept: a decoded item is always one.
 */
public final class ItemText {

    private static final ItemCodec CODEC = ItemCodec.paper();

    private ItemText() {
    }

    /**
     * Writes an item.
     *
     * @param item the item
     * @return its material name, or the encoded item
     */
    public static String encode(ItemStack item) {
        if (!item.hasItemMeta()) return item.getType().name();
        return CODEC.encode(item, false);
    }

    /**
     * Reads an item written by {@link #encode(ItemStack)}.
     *
     * @param raw the text
     * @return one of the item, or {@code null} if it cannot be read or is air
     */
    public static @Nullable ItemStack decode(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        ItemStack item;
        if (raw.startsWith("v2")) {
            ItemCodec.Decoded<ItemStack> decoded = CODEC.decode(raw);
            item = decoded.intact() ? decoded.value() : null;
        } else {
            Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            item = material == null || !material.isItem() ? null : new ItemStack(material);
        }
        if (item == null || item.getType().isAir()) return null;
        item.setAmount(1);
        return item;
    }
}
