package org.glstudio.nexus.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory queries and item delivery that never loses items.
 */
public final class InventoryUtils {

    private InventoryUtils() {
    }

    /**
     * Outcome of handing items to a player.
     *
     * @param given     number of items that went into the inventory
     * @param leftovers stacks that did not fit; dropped at the player's feet by
     *                  {@link #giveOrDrop(Player, ItemStack...)}, returned untouched by
     *                  {@link #give(Player, ItemStack...)}
     */
    public record GiveResult(int given, List<ItemStack> leftovers) {

        /**
         * Creates a result with an immutable copy of the leftovers.
         *
         * @param given     items that went into the inventory
         * @param leftovers stacks that did not fit
         */
        public GiveResult {
            leftovers = List.copyOf(leftovers);
        }

        /**
         * Returns whether everything fit in the inventory.
         *
         * @return {@code true} if there are no leftovers
         */
        public boolean complete() {
            return leftovers.isEmpty();
        }

        /**
         * Returns the number of items that did not fit.
         *
         * @return total amount of the leftovers
         */
        public int leftoverAmount() {
            int total = 0;
            for (ItemStack stack : leftovers) total += stack.getAmount();
            return total;
        }
    }

    /**
     * Adds items to the player's inventory and returns what did not fit, without dropping it.
     *
     * @param player the recipient
     * @param items  the stacks; {@code null} and air are skipped. They are added as given, so pass
     *               clones if the caller keeps using them.
     * @return what was given and what is left
     */
    public static GiveResult give(Player player, ItemStack... items) {
        int requested = 0;
        List<ItemStack> valid = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
            requested += item.getAmount();
            valid.add(item);
        }
        if (valid.isEmpty()) return new GiveResult(0, List.of());

        List<ItemStack> leftovers = new ArrayList<>(
                player.getInventory().addItem(valid.toArray(new ItemStack[0])).values());
        int left = 0;
        for (ItemStack stack : leftovers) left += stack.getAmount();
        return new GiveResult(requested - left, leftovers);
    }

    /**
     * Adds items to the player's inventory and drops what does not fit at their feet.
     *
     * @param player the recipient
     * @param items  the stacks; {@code null} and air are skipped. One may hold more than a stack:
     *               what is dropped is split into stacks of the item's size.
     * @return what was given and what was dropped
     */
    public static GiveResult giveOrDrop(Player player, ItemStack... items) {
        GiveResult result = give(player, items);
        for (ItemStack leftover : result.leftovers()) {
            int max = Math.max(1, leftover.getMaxStackSize());
            for (int left = leftover.getAmount(); left > 0; left -= max) {
                ItemStack stack = leftover.clone();
                stack.setAmount(Math.min(max, left));
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
        return result;
    }

    /**
     * Returns whether the player has at least one empty storage slot.
     *
     * @param player the player
     * @return {@code true} if a slot is empty
     */
    public static boolean hasSpace(Player player) {
        return player.getInventory().firstEmpty() != -1;
    }

    /**
     * Returns whether {@code item} fits entirely in {@code inventory}.
     *
     * @param inventory the inventory
     * @param item      the stack
     * @return {@code true} if it fits
     */
    public static boolean hasSpace(Inventory inventory, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        int maxStack = item.getMaxStackSize();
        int amount = item.getAmount();

        for (ItemStack slot : inventory.getContents()) {
            if (slot == null || slot.getType() == Material.AIR) return true;
            if (slot.isSimilar(item) && slot.getAmount() < maxStack) {
                amount -= (maxStack - slot.getAmount());
                if (amount <= 0) return true;
            }
        }
        return false;
    }

    /**
     * Returns whether every stack in {@code items} fits in {@code inventory} together.
     *
     * @param inventory the inventory
     * @param items     the stacks
     * @return {@code true} if they all fit
     */
    public static boolean hasSpace(Inventory inventory, List<ItemStack> items) {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] sim = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            sim[i] = contents[i] == null ? null : contents[i].clone();
        }

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            int amount = item.getAmount();
            int maxStack = item.getMaxStackSize();

            for (int i = 0; i < sim.length && amount > 0; i++) {
                if (sim[i] == null || sim[i].getType() == Material.AIR) {
                    sim[i] = item.clone();
                    sim[i].setAmount(Math.min(amount, maxStack));
                    amount -= sim[i].getAmount();
                } else if (sim[i].isSimilar(item) && sim[i].getAmount() < maxStack) {
                    int space = maxStack - sim[i].getAmount();
                    int toAdd = Math.min(amount, space);
                    sim[i].setAmount(sim[i].getAmount() + toAdd);
                    amount -= toAdd;
                }
            }
            if (amount > 0) return false;
        }
        return true;
    }

    /**
     * Counts empty storage slots.
     *
     * @param player the player
     * @return number of empty slots
     */
    public static int freeSlots(Player player) {
        int count = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null) count++;
        }
        return count;
    }

    /**
     * Counts items of a material in the storage slots.
     *
     * @param player   the player
     * @param material the material
     * @return total amount
     */
    public static int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Removes {@code amount} items of a material, only if the player has enough.
     *
     * @param player   the player
     * @param material the material
     * @param amount   how many to remove
     * @return {@code true} if they were removed
     */
    public static boolean removeItem(Player player, Material material, int amount) {
        if (countItem(player, material) < amount) return false;

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;

            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                contents[i] = null;
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }

        player.getInventory().setStorageContents(contents);
        return true;
    }

    /**
     * Gives one stack, dropping what does not fit.
     *
     * @param player the recipient
     * @param item   the stack, ignored if {@code null}
     */
    public static void giveOrDrop(Player player, ItemStack item) {
        if (item == null) return;
        giveOrDrop(player, new ItemStack[]{item});
    }

    /**
     * Returns the non-empty storage slots.
     *
     * @param player the player
     * @return the stacks, live references in a new list
     */
    public static List<ItemStack> getContents(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null) items.add(item);
        }
        return items;
    }

    /**
     * Returns whether the player holds at least {@code amount} of a material.
     *
     * @param player   the player
     * @param material the material
     * @param amount   the amount
     * @return {@code true} if they do
     */
    public static boolean hasItem(Player player, Material material, int amount) {
        return countItem(player, material) >= amount;
    }

    /**
     * Returns whether all four armour slots are filled.
     *
     * @param player the player
     * @return {@code true} if fully armoured
     */
    public static boolean hasFullArmor(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece == null || piece.getType() == Material.AIR) return false;
        }
        return true;
    }

    /**
     * Empties storage, armour and off-hand.
     *
     * @param player the player
     */
    public static void clearAll(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }
}
