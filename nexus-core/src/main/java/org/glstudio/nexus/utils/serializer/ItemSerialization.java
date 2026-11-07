package org.glstudio.nexus.utils.serializer;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.glstudio.nexus.utils.Diagnostics;

/**
 * Base64 item serialisation in the Bukkit object-stream format of Nexus 1.x.
 *
 * @deprecated a failed read is indistinguishable from an empty inventory here. Use {@link ItemCodec},
 * which reads this format too and reports {@link ItemCodec.Decoded#intact()}.
 */
@Deprecated(since = "2.0.0")
public final class ItemSerialization {

    private ItemSerialization() {
    }

    /**
     * Encodes one stack.
     *
     * @param item the stack
     * @return the payload, {@code ""} on failure
     * @deprecated use {@link ItemCodec#encodeLegacy(ItemStack)}
     */
    @Deprecated(since = "2.0.0")
    public static String serialize(ItemStack item) {
        try {
            return ItemCodec.paper().encodeLegacy(item);
        } catch (Exception e) {
            Diagnostics.logFailure("ItemSerialization#serialize", e);
            return "";
        }
    }

    /**
     * Decodes one stack.
     *
     * @param data the payload
     * @return the stack, {@code null} if empty or unreadable
     * @deprecated use {@link ItemCodec#decode(String)}
     */
    @Deprecated(since = "2.0.0")
    public static ItemStack deserialize(String data) {
        return ItemCodec.paper().decode(data).value();
    }

    /**
     * Encodes slots.
     *
     * @param contents the slots
     * @return the payload, {@code ""} on failure
     * @deprecated use {@link ItemCodec#encodeLegacy(ItemStack[])}
     */
    @Deprecated(since = "2.0.0")
    public static String serializeInventory(ItemStack[] contents) {
        try {
            return ItemCodec.paper().encodeLegacy(contents);
        } catch (Exception e) {
            Diagnostics.logFailure("ItemSerialization#serializeInventory", e);
            return "";
        }
    }

    /**
     * Decodes slots.
     *
     * @param data the payload
     * @return the slots, empty if empty or unreadable
     * @deprecated use {@link ItemCodec#decodeArray(String, int)}
     */
    @Deprecated(since = "2.0.0")
    public static ItemStack[] deserializeInventory(String data) {
        ItemCodec.Decoded<ItemStack[]> decoded = ItemCodec.paper().decodeArray(data, -1);
        return decoded.intact() ? decoded.value() : new ItemStack[0];
    }

    /**
     * Encodes the contents of an inventory.
     *
     * @param inventory the inventory
     * @return the payload
     * @deprecated use {@link ItemCodec#encodeLegacy(ItemStack[])}
     */
    @Deprecated(since = "2.0.0")
    public static String serializeInventory(Inventory inventory) {
        return serializeInventory(inventory.getContents());
    }

    /**
     * Replaces the contents of an inventory with decoded slots, unless nothing was decoded.
     *
     * @param inventory the inventory
     * @param data      the payload
     * @deprecated use {@link ItemCodec#decodeArray(String, int)}
     */
    @Deprecated(since = "2.0.0")
    public static void deserializeIntoInventory(Inventory inventory, String data) {
        ItemStack[] contents = deserializeInventory(data);
        if (contents.length > 0) inventory.setContents(contents);
    }
}
