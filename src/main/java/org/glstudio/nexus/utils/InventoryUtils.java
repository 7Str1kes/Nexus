package org.glstudio.nexus.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class InventoryUtils {

    public static boolean hasSpace(Player player) {
        return player.getInventory().firstEmpty() != -1;
    }

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

    public static int freeSlots(Player player) {
        int count = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null) count++;
        }
        return count;
    }

    public static int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

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

    public static void giveOrDrop(Player player, ItemStack item) {
        if (item == null) return;
        if (hasSpace(player)) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    public static List<ItemStack> getContents(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null) items.add(item);
        }
        return items;
    }

    public static boolean hasItem(Player player, Material material, int amount) {
        return countItem(player, material) >= amount;
    }

    public static boolean hasFullArmor(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece == null || piece.getType() == Material.AIR) return false;
        }
        return true;
    }

    public static void clearAll(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }
}