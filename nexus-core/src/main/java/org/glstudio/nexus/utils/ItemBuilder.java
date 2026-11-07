package org.glstudio.nexus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fluent editing of an {@link ItemStack}. The builder works on its own copy of the stack.
 *
 * <p>Text setters taking a {@code String} use legacy colour codes. The overloads taking a
 * {@link TextFormat} also accept MiniMessage; MiniMessage names and lore are not italic unless the
 * text says so, matching how legacy-coloured text looks in game.
 */
public class ItemBuilder {

    private final ItemStack is;

    /**
     * Starts from one item of {@code m}.
     *
     * @param m the material
     */
    public ItemBuilder(Material m) {
        this(m, 1);
    }

    /**
     * Starts from a copy of {@code is}.
     *
     * @param is the stack to copy
     */
    public ItemBuilder(ItemStack is) {
        this.is = is.clone();
    }

    /**
     * Starts from {@code amount} items of {@code m}.
     *
     * @param m      the material
     * @param amount the amount
     */
    public ItemBuilder(Material m, int amount) {
        is = new ItemStack(m, amount);
    }

    /**
     * Starts from a copy of {@code is} with another amount.
     *
     * @param is     the stack to copy
     * @param amount the amount
     */
    public ItemBuilder(ItemStack is, int amount) {
        this.is = is.clone();
        this.is.setAmount(amount);
    }

    /**
     * Starts from one item of {@code material}.
     *
     * @param material the material
     * @return the builder
     */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    /**
     * Starts from {@code amount} items of {@code material}.
     *
     * @param material the material
     * @param amount   the amount
     * @return the builder
     */
    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    /**
     * Starts from a player head owned by {@code owner}.
     *
     * @param owner the owner's name
     * @return the builder
     */
    public static ItemBuilder skull(String owner) {
        return new ItemBuilder(Material.PLAYER_HEAD).setSkullOwner(owner);
    }

    /**
     * Sets the display name from legacy text.
     *
     * @param name the name, {@code &} codes allowed
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder setName(String name) {
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(CC.t(name));
        is.setItemMeta(im);
        return this;
    }

    /**
     * Sets the display name from text written in {@code format}.
     *
     * @param name   the name
     * @param format how it is written
     * @return this builder
     */
    public ItemBuilder setName(String name, TextFormat format) {
        if (format != TextFormat.MINIMESSAGE) return setName(name);
        return setName(CC.miniMessage(name));
    }

    /**
     * Sets the display name; not italic unless the component says so.
     *
     * @param name the name
     * @return this builder
     */
    public ItemBuilder setName(Component name) {
        ItemMeta im = is.getItemMeta();
        im.displayName(nonItalic(name));
        is.setItemMeta(im);
        return this;
    }

    /**
     * Appends a lore line from legacy text.
     *
     * @param line the line, {@code &} codes allowed
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder addLoreLine(String line) {
        ItemMeta im = is.getItemMeta();
        List<String> lore = im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
        lore.add(CC.t(line));
        im.setLore(lore);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Replaces the lore line at {@code pos}.
     *
     * @param line the line, {@code &} codes allowed
     * @param pos  the index of an existing line
     * @return this builder
     * @throws IndexOutOfBoundsException if there is no line at {@code pos}
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder addLoreLine(String line, int pos) {
        ItemMeta im = is.getItemMeta();
        List<String> lore = im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
        lore.set(pos, CC.t(line));
        im.setLore(lore);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Sets the lore from legacy lines.
     *
     * @param lore the lines, {@code &} codes allowed
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder setLore(String... lore) {
        ItemMeta im = is.getItemMeta();
        im.setLore(CC.t(Arrays.asList(lore)));
        is.setItemMeta(im);
        return this;
    }

    /**
     * Sets the lore from legacy lines.
     *
     * @param lore the lines, {@code &} codes allowed
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder setLore(List<String> lore) {
        ItemMeta im = is.getItemMeta();
        im.setLore(CC.t(lore));
        is.setItemMeta(im);
        return this;
    }

    /**
     * Sets the lore from lines written in {@code format}.
     *
     * @param lore   the lines
     * @param format how they are written
     * @return this builder
     */
    public ItemBuilder setLore(List<String> lore, TextFormat format) {
        if (format != TextFormat.MINIMESSAGE) return setLore(lore);
        List<Component> lines = new ArrayList<>(lore.size());
        for (String line : lore) lines.add(CC.miniMessage(line));
        return setLoreComponents(lines);
    }

    /**
     * Sets the lore; lines are not italic unless the component says so.
     *
     * @param lore the lines
     * @return this builder
     */
    public ItemBuilder setLoreComponents(List<Component> lore) {
        ItemMeta im = is.getItemMeta();
        List<Component> lines = new ArrayList<>(lore.size());
        for (Component line : lore) lines.add(nonItalic(line));
        im.lore(lines);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Removes the first lore line equal to {@code line}.
     *
     * @param line the exact line, colour codes already translated
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder removeLoreLine(String line) {
        ItemMeta im = is.getItemMeta();
        if (!im.hasLore()) return this;
        List<String> lore = new ArrayList<>(im.getLore());
        lore.remove(line);
        im.setLore(lore);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Removes the lore line at {@code index}, if it exists.
     *
     * @param index the line index
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder removeLoreLine(int index) {
        ItemMeta im = is.getItemMeta();
        if (!im.hasLore()) return this;
        List<String> lore = new ArrayList<>(im.getLore());
        if (index < 0 || index >= lore.size()) return this;
        lore.remove(index);
        im.setLore(lore);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Removes every lore line.
     *
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder clearLore() {
        ItemMeta im = is.getItemMeta();
        im.setLore(new ArrayList<>());
        is.setItemMeta(im);
        return this;
    }

    /**
     * Returns the current lore as legacy strings.
     *
     * @return the lines, empty if none
     */
    @SuppressWarnings("deprecation")
    public List<String> getLore() {
        if (is.hasItemMeta() && is.getItemMeta().hasLore()) return is.getItemMeta().getLore();
        return Collections.emptyList();
    }

    /**
     * Adds an enchantment, ignoring level restrictions.
     *
     * @param ench  the enchantment
     * @param level the level
     * @return this builder
     */
    public ItemBuilder addEnchant(Enchantment ench, int level) {
        ItemMeta im = is.getItemMeta();
        im.addEnchant(ench, level, true);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Adds an enchantment without any validation.
     *
     * @param ench  the enchantment
     * @param level the level
     * @return this builder
     */
    public ItemBuilder addUnsafeEnchantment(Enchantment ench, int level) {
        is.addUnsafeEnchantment(ench, level);
        return this;
    }

    /**
     * Adds enchantments, validating them against the item.
     *
     * @param enchantments enchantments and levels
     * @return this builder
     */
    public ItemBuilder addEnchantments(Map<Enchantment, Integer> enchantments) {
        is.addEnchantments(enchantments);
        return this;
    }

    /**
     * Removes an enchantment.
     *
     * @param ench the enchantment
     * @return this builder
     */
    public ItemBuilder removeEnchantment(Enchantment ench) {
        is.removeEnchantment(ench);
        return this;
    }

    /**
     * Adds item flags.
     *
     * @param flags the flags
     * @return this builder
     */
    public ItemBuilder addFlag(ItemFlag... flags) {
        ItemMeta im = is.getItemMeta();
        im.addItemFlags(flags);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Removes item flags.
     *
     * @param flags the flags
     * @return this builder
     */
    public ItemBuilder removeFlag(ItemFlag... flags) {
        ItemMeta im = is.getItemMeta();
        im.removeItemFlags(flags);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Adds every item flag, hiding attributes, enchantments and the like.
     *
     * @return this builder
     */
    public ItemBuilder hideAll() {
        return addFlag(ItemFlag.values());
    }

    /**
     * Sets whether the item is unbreakable.
     *
     * @param unbreakable {@code true} for unbreakable
     * @return this builder
     */
    public ItemBuilder setUnbreakable(boolean unbreakable) {
        ItemMeta im = is.getItemMeta();
        im.setUnbreakable(unbreakable);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Sets the legacy durability field to its maximum.
     *
     * @return this builder
     * @deprecated legacy durability; use {@link #setUnbreakable(boolean)}
     */
    @Deprecated
    public ItemBuilder setInfinityDurability() {
        is.setDurability(Short.MAX_VALUE);
        return this;
    }

    /**
     * Sets the amount.
     *
     * @param amount the amount
     * @return this builder
     */
    public ItemBuilder setAmount(int amount) {
        is.setAmount(amount);
        return this;
    }

    /**
     * Sets the owner of a player head; ignored for other items.
     *
     * @param owner the owner's name
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder setSkullOwner(String owner) {
        if (is.getItemMeta() instanceof SkullMeta im) {
            im.setOwner(owner);
            is.setItemMeta(im);
        }
        return this;
    }

    /**
     * Dyes leather armour; ignored for other items.
     *
     * @param color the colour
     * @return this builder
     */
    public ItemBuilder setLeatherArmorColor(Color color) {
        if (is.getItemMeta() instanceof LeatherArmorMeta im) {
            im.setColor(color);
            is.setItemMeta(im);
        }
        return this;
    }

    /**
     * Sets the custom model data used by resource packs.
     *
     * @param data the value
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder setCustomModelData(int data) {
        ItemMeta im = is.getItemMeta();
        im.setCustomModelData(data);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Sets the legacy durability (damage) field.
     *
     * @param data the value
     * @return this builder
     * @deprecated legacy durability; use {@code Damageable} meta
     */
    @Deprecated
    public ItemBuilder data(int data) {
        is.setDurability((short) data);
        return this;
    }

    /**
     * Copies this builder.
     *
     * @return a builder over a copy of the current stack
     */
    @Override
    public ItemBuilder clone() {
        return new ItemBuilder(is.clone());
    }

    /**
     * Returns the stack being built. It is the builder's own copy, not a new one.
     *
     * @return the stack
     */
    public ItemStack toItemStack() {
        return is;
    }

    private static Component nonItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
