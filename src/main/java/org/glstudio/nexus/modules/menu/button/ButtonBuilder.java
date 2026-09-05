package org.glstudio.nexus.modules.menu.button;

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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent {@link Button} construction on top of Nexus's {@link ItemBuilder}, adding the pieces a
 * menu button needs beyond a plain item: glow, an item-model key, player-head owners, and the
 * click callback itself. {@link #fromConfig(ConfigurationSection)} reads the common subset of
 * that straight out of YAML for plugins that want a config-driven button.
 */
public class ButtonBuilder {

    private final ItemBuilder itemBuilder;
    private Consumer<InventoryClickEvent> onClick = event -> {
    };
    private boolean cancelClick = true;

    /** Defaults to {@link Material#STONE} — several plugins' own builder did the same, always
     * followed immediately by {@link #material(Material)}. */
    public ButtonBuilder() {
        this.itemBuilder = new ItemBuilder(Material.STONE);
    }

    public ButtonBuilder(Material material) {
        this.itemBuilder = new ItemBuilder(material);
    }

    public ButtonBuilder(ItemStack base) {
        this.itemBuilder = new ItemBuilder(base);
    }

    public static ButtonBuilder of(Material material) {
        return new ButtonBuilder(material);
    }

    public static ButtonBuilder of(ItemStack base) {
        return new ButtonBuilder(base);
    }

    public static ButtonBuilder skull(String ownerName) {
        return new ButtonBuilder(ItemBuilder.skull(ownerName).toItemStack());
    }

    public static ButtonBuilder skull(OfflinePlayer owner) {
        return new ButtonBuilder(Material.PLAYER_HEAD).skullOwner(owner);
    }

    /**
     * Builds a button from a canonical config schema: {@code material, amount, name, lore,
     * custom-model-data, glow, unbreakable, item-flags, skull-owner}. Plugins with their own YAML
     * schema/case convention should keep their existing parsing glue and call the individual
     * builder methods instead — this is a convenience for new, canonically-shaped config.
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
                builder.flags(ItemFlag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Unknown flag name in config — skip it rather than fail the whole button.
            }
        }

        return builder;
    }

    /** Overrides the material after construction — e.g. a per-entry icon beating a template default. */
    public ButtonBuilder material(Material material) {
        itemBuilder.toItemStack().setType(material);
        return this;
    }

    public ButtonBuilder name(String name) {
        itemBuilder.setName(name);
        return this;
    }

    public ButtonBuilder lore(String... lore) {
        itemBuilder.setLore(lore);
        return this;
    }

    public ButtonBuilder lore(List<String> lore) {
        itemBuilder.setLore(lore);
        return this;
    }

    public ButtonBuilder addLoreLine(String line) {
        itemBuilder.addLoreLine(line);
        return this;
    }

    public ButtonBuilder amount(int amount) {
        itemBuilder.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ButtonBuilder enchant(Enchantment enchantment, int level) {
        itemBuilder.addEnchant(enchantment, level);
        return this;
    }

    public ButtonBuilder enchantments(Map<Enchantment, Integer> enchantments) {
        itemBuilder.addEnchantments(enchantments);
        return this;
    }

    public ButtonBuilder flags(ItemFlag... flags) {
        itemBuilder.addFlag(flags);
        return this;
    }

    public ButtonBuilder hideAll() {
        itemBuilder.hideAll();
        return this;
    }

    public ButtonBuilder unbreakable(boolean unbreakable) {
        itemBuilder.setUnbreakable(unbreakable);
        return this;
    }

    public ButtonBuilder customModelData(int data) {
        itemBuilder.setCustomModelData(data);
        return this;
    }

    public ButtonBuilder skullOwner(String ownerName) {
        itemBuilder.setSkullOwner(ownerName);
        return this;
    }

    public ButtonBuilder skullOwner(OfflinePlayer owner) {
        ItemStack stack = itemBuilder.toItemStack();
        if (stack.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(owner);
            stack.setItemMeta(skullMeta);
        }
        return this;
    }

    /** Real client-side glint via the modern API, falling back to a hidden fake enchant. */
    public ButtonBuilder glow() {
        ItemStack stack = itemBuilder.toItemStack();
        ItemMeta meta = stack.getItemMeta();
        boolean applied = false;
        try {
            meta.setEnchantmentGlintOverride(true);
            applied = true;
        } catch (Throwable ignored) {
            // Older API without glint override support — fall back below.
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

    /** Best-effort: the item-model API is only present on newer Paper versions. */
    public ButtonBuilder itemModel(NamespacedKey key) {
        ItemStack stack = itemBuilder.toItemStack();
        try {
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(key);
            stack.setItemMeta(meta);
        } catch (Throwable ignored) {
            // Not supported on this server version — the item just keeps its default model.
        }
        return this;
    }

    public ButtonBuilder onClick(Consumer<InventoryClickEvent> handler) {
        this.onClick = handler != null ? handler : event -> {
        };
        return this;
    }

    public ButtonBuilder onClick(Runnable handler) {
        this.onClick = event -> handler.run();
        return this;
    }

    /** Alias of {@link #onClick(Runnable)} — several migrated plugins used this name. */
    public ButtonBuilder onClickSimple(Runnable handler) {
        return onClick(handler);
    }

    public ButtonBuilder onClickPlayer(Consumer<Player> handler) {
        this.onClick = event -> handler.accept((Player) event.getWhoClicked());
        return this;
    }

    /** Whether the click should be force-cancelled before the handler runs. Defaults to true. */
    public ButtonBuilder cancelClick(boolean cancelClick) {
        this.cancelClick = cancelClick;
        return this;
    }

    public ItemStack toItemStack() {
        return itemBuilder.toItemStack();
    }

    public Button build() {
        return new Button(itemBuilder.toItemStack(), onClick, cancelClick);
    }
}
