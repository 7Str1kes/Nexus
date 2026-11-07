package org.glstudio.nexus.modules.menu.button;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.glstudio.nexus.modules.menu.config.MenuConfigUtils;
import org.glstudio.nexus.utils.ItemBuilder;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.nexus.utils.TextFormat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent {@link Button} construction on top of {@link ItemBuilder}, adding what a menu button needs
 * beyond a plain item: glow, an item-model key, player-head owners, and the click callback.
 *
 * <p>For buttons described in a menu file, see {@link org.glstudio.nexus.modules.menu.config.MenuConfig}.
 */
public class ButtonBuilder {

    private final ItemBuilder itemBuilder;
    private Consumer<InventoryClickEvent> onClick = event -> {
    };
    private boolean cancelClick = true;

    /**
     * Starts from {@link Material#STONE}, usually followed by {@link #material(Material)}.
     */
    public ButtonBuilder() {
        this.itemBuilder = new ItemBuilder(Material.STONE);
    }

    /**
     * Starts from one item of {@code material}.
     *
     * @param material the material
     */
    public ButtonBuilder(Material material) {
        this.itemBuilder = new ItemBuilder(material);
    }

    /**
     * Starts from a copy of {@code base}.
     *
     * @param base the stack to copy
     */
    public ButtonBuilder(ItemStack base) {
        this.itemBuilder = new ItemBuilder(base);
    }

    /**
     * Starts from one item of {@code material}.
     *
     * @param material the material
     * @return the builder
     */
    public static ButtonBuilder of(Material material) {
        return new ButtonBuilder(material);
    }

    /**
     * Starts from a copy of {@code base}.
     *
     * @param base the stack to copy
     * @return the builder
     */
    public static ButtonBuilder of(ItemStack base) {
        return new ButtonBuilder(base);
    }

    /**
     * Starts from a player head owned by {@code ownerName}.
     *
     * @param ownerName the owner's name
     * @return the builder
     */
    public static ButtonBuilder skull(String ownerName) {
        return new ButtonBuilder(ItemBuilder.skull(ownerName).toItemStack());
    }

    /**
     * Starts from a player head owned by {@code owner}.
     *
     * @param owner the owner
     * @return the builder
     */
    public static ButtonBuilder skull(OfflinePlayer owner) {
        return new ButtonBuilder(Material.PLAYER_HEAD).skullOwner(owner);
    }

    /**
     * Builds a button from the kebab-case schema {@code material, amount, name, lore,
     * custom-model-data, glow, unbreakable, item-flags, skull-owner}, with legacy text.
     *
     * <p>{@link org.glstudio.nexus.modules.menu.config.MenuConfig} supports more schemas and
     * MiniMessage.
     *
     * @param section the section, may be {@code null}
     * @return the builder; a stone button if the section is {@code null}
     */
    public static ButtonBuilder fromConfig(ConfigurationSection section) {
        if (section == null) return new ButtonBuilder(Material.STONE);

        Material material = MenuConfigUtils.parseMaterial(section.getString("material"), Material.STONE);
        ButtonBuilder builder = new ButtonBuilder(material);

        if (section.contains("amount")) builder.amount(section.getInt("amount"));
        if (section.contains("name")) builder.name(section.getString("name"));
        if (section.contains("lore")) builder.lore(section.getStringList("lore"));
        if (section.contains("custom-model-data")) builder.customModelData(section.getInt("custom-model-data"));
        if (section.getBoolean("glow", false)) builder.glow();
        if (section.contains("unbreakable")) builder.unbreakable(section.getBoolean("unbreakable"));
        if (section.contains("skull-owner")) builder.skullOwner(section.getString("skull-owner"));

        for (String flagName : section.getStringList("item-flags")) {
            try {
                builder.flags(ItemFlag.valueOf(flagName.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Unknown flag name in config: skip it rather than fail the whole button.
            }
        }

        return builder;
    }

    /**
     * Parses a material, warning and falling back for a bad value.
     *
     * <p>Accepts {@code STONE}, {@code stone} and {@code minecraft:stone}.
     *
     * @param name     the configured value, may be {@code null}
     * @param fallback used for a missing or unknown value
     * @param context  where the value comes from, shown in the warning; may be {@code null}
     * @return the material
     */
    public static Material parseMaterial(String name, Material fallback, String context) {
        if (name == null || name.isBlank()) return fallback;
        Material material = Material.matchMaterial(name.trim().replace('-', '_'));
        if (material != null) return material;
        LoggerUtils.logWarn("Unknown material '" + name + "'"
                + (context == null ? "" : " at " + context) + "; using " + fallback + ".");
        return fallback;
    }

    /**
     * Changes the material, e.g. a per-entry icon overriding a template default.
     *
     * @param material the material
     * @return this builder
     */
    public ButtonBuilder material(Material material) {
        itemBuilder.toItemStack().setType(material);
        return this;
    }

    /**
     * Sets the name from legacy text.
     *
     * @param name the name, {@code &} codes allowed
     * @return this builder
     */
    public ButtonBuilder name(String name) {
        itemBuilder.setName(name);
        return this;
    }

    /**
     * Sets the name from text written in {@code format}.
     *
     * @param name   the name
     * @param format how it is written
     * @return this builder
     */
    public ButtonBuilder name(String name, TextFormat format) {
        itemBuilder.setName(name, format);
        return this;
    }

    /**
     * Sets the name; not italic unless the component says so.
     *
     * @param name the name
     * @return this builder
     */
    public ButtonBuilder name(Component name) {
        itemBuilder.setName(name);
        return this;
    }

    /**
     * Sets the lore from legacy lines.
     *
     * @param lore the lines
     * @return this builder
     */
    public ButtonBuilder lore(String... lore) {
        itemBuilder.setLore(lore);
        return this;
    }

    /**
     * Sets the lore from legacy lines.
     *
     * @param lore the lines
     * @return this builder
     */
    public ButtonBuilder lore(List<String> lore) {
        itemBuilder.setLore(lore);
        return this;
    }

    /**
     * Sets the lore from lines written in {@code format}.
     *
     * @param lore   the lines
     * @param format how they are written
     * @return this builder
     */
    public ButtonBuilder lore(List<String> lore, TextFormat format) {
        itemBuilder.setLore(lore, format);
        return this;
    }

    /**
     * Sets the lore; lines are not italic unless the component says so.
     *
     * @param lore the lines
     * @return this builder
     */
    public ButtonBuilder loreComponents(List<Component> lore) {
        itemBuilder.setLoreComponents(lore);
        return this;
    }

    /**
     * Appends a legacy lore line.
     *
     * @param line the line
     * @return this builder
     */
    public ButtonBuilder addLoreLine(String line) {
        itemBuilder.addLoreLine(line);
        return this;
    }

    /**
     * Sets the amount, clamped to 1-64.
     *
     * @param amount the amount
     * @return this builder
     */
    public ButtonBuilder amount(int amount) {
        itemBuilder.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    /**
     * Adds an enchantment, ignoring level restrictions.
     *
     * @param enchantment the enchantment
     * @param level       the level
     * @return this builder
     */
    public ButtonBuilder enchant(Enchantment enchantment, int level) {
        itemBuilder.addEnchant(enchantment, level);
        return this;
    }

    /**
     * Adds enchantments.
     *
     * @param enchantments enchantments and levels
     * @return this builder
     */
    public ButtonBuilder enchantments(Map<Enchantment, Integer> enchantments) {
        itemBuilder.addEnchantments(enchantments);
        return this;
    }

    /**
     * Adds item flags.
     *
     * @param flags the flags
     * @return this builder
     */
    public ButtonBuilder flags(ItemFlag... flags) {
        itemBuilder.addFlag(flags);
        return this;
    }

    /**
     * Hides attributes, enchantments and every other tooltip extra.
     *
     * @return this builder
     */
    public ButtonBuilder hideAll() {
        itemBuilder.hideAll();
        return this;
    }

    /**
     * Sets whether the item is unbreakable.
     *
     * @param unbreakable {@code true} for unbreakable
     * @return this builder
     */
    public ButtonBuilder unbreakable(boolean unbreakable) {
        itemBuilder.setUnbreakable(unbreakable);
        return this;
    }

    /**
     * Sets the custom model data.
     *
     * @param data the value
     * @return this builder
     */
    public ButtonBuilder customModelData(int data) {
        itemBuilder.setCustomModelData(data);
        return this;
    }

    /**
     * Sets the owner of a player head; ignored for other items.
     *
     * @param ownerName the owner's name
     * @return this builder
     */
    public ButtonBuilder skullOwner(String ownerName) {
        itemBuilder.setSkullOwner(ownerName);
        return this;
    }

    /**
     * Sets the owner of a player head; ignored for other items.
     *
     * @param owner the owner
     * @return this builder
     */
    public ButtonBuilder skullOwner(OfflinePlayer owner) {
        ItemStack stack = itemBuilder.toItemStack();
        if (stack.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(owner);
            stack.setItemMeta(skullMeta);
        }
        return this;
    }

    /**
     * Adds the enchantment glint without showing an enchantment.
     *
     * @return this builder
     */
    public ButtonBuilder glow() {
        ItemStack stack = itemBuilder.toItemStack();
        ItemMeta meta = stack.getItemMeta();
        boolean applied = false;
        try {
            meta.setEnchantmentGlintOverride(true);
            applied = true;
        } catch (Throwable ignored) {
            // Older API without glint override support: fall back below.
        }
        stack.setItemMeta(meta);
        if (!applied) {
            stack.addUnsafeEnchantment(Enchantment.LURE, 1);
            ItemMeta fallback = stack.getItemMeta();
            fallback.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(fallback);
        }
        return this;
    }

    /**
     * Sets the item model; ignored on servers without the item-model API.
     *
     * @param key the model key
     * @return this builder
     */
    public ButtonBuilder itemModel(NamespacedKey key) {
        ItemStack stack = itemBuilder.toItemStack();
        try {
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(key);
            stack.setItemMeta(meta);
        } catch (Throwable ignored) {
            // Not supported on this server version: the item keeps its default model.
        }
        return this;
    }

    /**
     * Sets the click handler.
     *
     * @param handler receives the click event; {@code null} for none
     * @return this builder
     */
    public ButtonBuilder onClick(Consumer<InventoryClickEvent> handler) {
        this.onClick = handler != null ? handler : event -> {
        };
        return this;
    }

    /**
     * Sets a click handler that ignores the event.
     *
     * @param handler the action
     * @return this builder
     */
    public ButtonBuilder onClick(Runnable handler) {
        this.onClick = event -> handler.run();
        return this;
    }

    /**
     * Sets a click handler that ignores the event; an alias of {@link #onClick(Runnable)}.
     *
     * @param handler the action
     * @return this builder
     */
    public ButtonBuilder onClickSimple(Runnable handler) {
        return onClick(handler);
    }

    /**
     * Sets a click handler that receives the clicking player.
     *
     * @param handler the action
     * @return this builder
     */
    public ButtonBuilder onClickPlayer(Consumer<Player> handler) {
        this.onClick = event -> handler.accept((Player) event.getWhoClicked());
        return this;
    }

    /**
     * Sets whether the click is cancelled before the handler runs. Defaults to {@code true}.
     *
     * @param cancelClick {@code true} to cancel
     * @return this builder
     */
    public ButtonBuilder cancelClick(boolean cancelClick) {
        this.cancelClick = cancelClick;
        return this;
    }

    /**
     * Returns the item being built (the builder's own copy).
     *
     * @return the stack
     */
    public ItemStack toItemStack() {
        return itemBuilder.toItemStack();
    }

    /**
     * Creates the button.
     *
     * @return the button
     */
    public Button build() {
        return new Button(itemBuilder.toItemStack(), onClick, cancelClick);
    }
}
