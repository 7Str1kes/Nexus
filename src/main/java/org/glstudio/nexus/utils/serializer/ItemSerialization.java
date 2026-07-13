package org.glstudio.nexus.utils.serializer;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.glstudio.nexus.utils.LoggerUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class ItemSerialization {

    public static String serialize(ItemStack item) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos);
            boos.writeObject(item);
            boos.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LoggerUtils.logException("ItemSerialization#serialize", e);
            return "";
        }
    }

    public static ItemStack deserialize(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream bois = new BukkitObjectInputStream(bais);
            ItemStack item = (ItemStack) bois.readObject();
            bois.close();
            return item;
        } catch (Exception e) {
            LoggerUtils.logException("ItemSerialization#deserialize", e);
            return null;
        }
    }

    public static String serializeInventory(ItemStack[] contents) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos);
            boos.writeInt(contents.length);
            for (ItemStack item : contents) {
                boos.writeObject(item);
            }
            boos.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LoggerUtils.logException("ItemSerialization#serializeInventory", e);
            return "";
        }
    }

    public static ItemStack[] deserializeInventory(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream bois = new BukkitObjectInputStream(bais);
            int size = bois.readInt();
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                contents[i] = (ItemStack) bois.readObject();
            }
            bois.close();
            return contents;
        } catch (Exception e) {
            LoggerUtils.logException("ItemSerialization#deserializeInventory", e);
            return new ItemStack[0];
        }
    }

    public static String serializeInventory(Inventory inventory) {
        return serializeInventory(inventory.getContents());
    }

    public static void deserializeIntoInventory(Inventory inventory, String data) {
        ItemStack[] contents = deserializeInventory(data);
        if (contents.length > 0) {
            inventory.setContents(contents);
        }
    }
}