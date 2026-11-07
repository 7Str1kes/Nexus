package org.glstudio.nexus.modules.menu.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.paginated.PaginatedMenu;
import org.glstudio.nexus.modules.menu.placeholder.Placeholders;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.nexus.utils.KeyStyle;
import org.glstudio.nexus.utils.TextFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A menu file ({@code menus/*.yml}) read as items, slots and titles.
 *
 * <p>Item sections use the keys {@code material, amount, name, lore, custom-model-data, glow,
 * unbreakable, item-flags, skull-owner, item-model, slot, slots}, spelled in the file's
 * {@link KeyStyle} ({@code custom-model-data} or {@code CUSTOM_MODEL_DATA}). Names and lore are written
 * in the file's {@link TextFormat}, and every method that builds an item accepts placeholder pairs
 * (see {@link Placeholders#apply(String, Object...)}).
 *
 * <p>A path without a section, or a section without a slot, simply produces no button: removing an
 * item from the file removes it from the menu.
 */
public final class MenuConfig {

    /** Returned when an item has no slot. */
    public static final int NO_SLOT = -1;

    private final ConfigFile file;
    private final KeyStyle style;
    private final TextFormat format;

    /**
     * Wraps a menu file written in kebab case or upper snake case, with legacy text.
     *
     * @param file the menu file
     */
    public MenuConfig(ConfigFile file) {
        this(file, KeyStyle.ANY, TextFormat.LEGACY);
    }

    /**
     * Wraps a menu file.
     *
     * @param file   the menu file
     * @param style  how its keys are spelled
     * @param format how its names and lore are written
     */
    public MenuConfig(ConfigFile file, KeyStyle style, TextFormat format) {
        this.file = Objects.requireNonNull(file, "file");
        this.style = Objects.requireNonNull(style, "style");
        this.format = Objects.requireNonNull(format, "format");
    }

    /**
     * Returns the underlying file.
     *
     * @return the file
     */
    public ConfigFile file() {
        return file;
    }

    /**
     * Returns how names and lore are written.
     *
     * @return the text format
     */
    public TextFormat format() {
        return format;
    }

    // ─── menu level ──────────────────────────────────────────────────────────────

    /**
     * Returns the menu title with placeholders applied.
     *
     * @param fallback used when the file has no {@code title}
     * @param pairs    alternating tokens and values
     * @return the title, in the file's format
     */
    public String title(String fallback, Object... pairs) {
        String raw = file.getString(style.resolve(file, "title"), null);
        return Placeholders.apply(raw == null ? fallback : raw, pairs);
    }

    /**
     * Returns the inventory size in slots from the {@code size} key.
     *
     * @param fallback used when missing
     * @return the size, as configured
     */
    public int size(int fallback) {
        return file.getInt(style.resolve(file, "size"), fallback);
    }

    /**
     * Returns the inventory size from a row count stored at {@code path}, clamped to 1-6 rows.
     *
     * @param path         the key holding the rows
     * @param fallbackRows used when missing
     * @return the size in slots
     */
    public int rows(String path, int fallbackRows) {
        int rows = file.getInt(path, fallbackRows);
        return Math.max(1, Math.min(6, rows)) * 9;
    }

    // ─── item level ──────────────────────────────────────────────────────────────

    /**
     * Returns whether {@code path} is an item section.
     *
     * @param path the item path
     * @return {@code true} if the section exists
     */
    public boolean has(String path) {
        return section(path) != null;
    }

    /**
     * Returns the item section at {@code path}.
     *
     * @param path the item path
     * @return the section, or {@code null}
     */
    public ConfigurationSection section(String path) {
        return file.getConfigurationSection(path);
    }

    /**
     * Returns the slot of an item.
     *
     * @param path the item path
     * @return the slot, or {@link #NO_SLOT}
     */
    public int slot(String path) {
        ConfigurationSection section = section(path);
        return section == null ? NO_SLOT : section.getInt(style.resolve(section, "slot"), NO_SLOT);
    }

    /**
     * Returns the slot of an item, or {@code fallback} if it has none.
     *
     * @param path     the item path
     * @param fallback used when missing
     * @return the slot
     */
    public int slot(String path, int fallback) {
        int slot = slot(path);
        return slot < 0 ? fallback : slot;
    }

    /**
     * Returns every slot of an item: its {@code slots} list (ints and {@code "a-b"} ranges) or its
     * single {@code slot}.
     *
     * @param path the item path
     * @return the slots, empty if none
     */
    public List<Integer> slots(String path) {
        ConfigurationSection section = section(path);
        if (section == null) return new ArrayList<>();
        String key = style.resolve(section, "slots");
        if (section.contains(key)) return MenuConfigUtils.parseSlots(section, key);
        int slot = slot(path);
        List<Integer> single = new ArrayList<>(1);
        if (slot >= 0) single.add(slot);
        return single;
    }

    /**
     * Returns the raw name of an item.
     *
     * @param path     the item path
     * @param fallback used when missing
     * @return the name template
     */
    public String name(String path, String fallback) {
        ConfigurationSection section = section(path);
        String name = section == null ? null : section.getString(style.resolve(section, "name"));
        return name == null ? fallback : name;
    }

    /**
     * Returns the raw lore of an item.
     *
     * @param path the item path
     * @return the lore templates, empty if none
     */
    public List<String> lore(String path) {
        ConfigurationSection section = section(path);
        return section == null ? new ArrayList<>() : new ArrayList<>(section.getStringList(style.resolve(section, "lore")));
    }

    /**
     * Returns the material of an item, warning about unknown names.
     *
     * @param path     the item path
     * @param fallback used when missing or unknown
     * @return the material
     */
    public Material material(String path, Material fallback) {
        ConfigurationSection section = section(path);
        if (section == null) return fallback;
        return ButtonBuilder.parseMaterial(section.getString(style.resolve(section, "material")), fallback,
                file.getFileName() + ":" + path);
    }

    /**
     * Builds an item's button without a click handler, applying placeholders to name and lore.
     *
     * @param path  the item path
     * @param pairs alternating tokens and values
     * @return the builder; a stone button if the section is missing
     */
    public ButtonBuilder builder(String path, Object... pairs) {
        ConfigurationSection section = section(path);
        if (section == null) return ButtonBuilder.of(Material.STONE);

        ButtonBuilder builder = ButtonBuilder.of(material(path, Material.STONE));
        String amount = style.resolve(section, "amount");
        if (section.contains(amount)) builder.amount(section.getInt(amount));
        String name = style.resolve(section, "name");
        if (section.contains(name)) builder.name(Placeholders.apply(section.getString(name), pairs), format);
        String lore = style.resolve(section, "lore");
        if (section.contains(lore)) builder.lore(Placeholders.apply(section.getStringList(lore), pairs), format);
        String model = style.resolve(section, "custom-model-data");
        if (section.contains(model)) builder.customModelData(section.getInt(model));
        if (section.getBoolean(style.resolve(section, "glow"), false)) builder.glow();
        String unbreakable = style.resolve(section, "unbreakable");
        if (section.contains(unbreakable)) builder.unbreakable(section.getBoolean(unbreakable));
        String skull = style.resolve(section, "skull-owner");
        if (section.contains(skull)) builder.skullOwner(Placeholders.apply(section.getString(skull), pairs));
        String itemModel = style.resolve(section, "item-model");
        if (section.contains(itemModel)) {
            NamespacedKey key = NamespacedKey.fromString(section.getString(itemModel, "").toLowerCase(Locale.ROOT));
            if (key != null) builder.itemModel(key);
        }
        List<String> flags = new ArrayList<>(section.getStringList(style.resolve(section, "item-flags")));
        flags.addAll(section.getStringList(style.resolve(section, "flags")));
        for (String flag : flags) {
            String constant = flag.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (constant.equals("ALL")) {
                builder.hideAll();
                continue;
            }
            try {
                builder.flags(ItemFlag.valueOf(constant));
            } catch (IllegalArgumentException ignored) {
                // Unknown flag name: skip it rather than fail the whole button.
            }
        }
        return builder;
    }

    /**
     * Builds an item.
     *
     * @param path  the item path
     * @param pairs alternating tokens and values
     * @return the item
     */
    public ItemStack item(String path, Object... pairs) {
        return builder(path, pairs).toItemStack();
    }

    /**
     * Places an item's button at each of its slots, if it has any.
     *
     * @param buttons the menu's buttons
     * @param path    the item path
     * @param onClick the click action, {@code null} for a decorative item
     * @param pairs   alternating tokens and values
     */
    public void put(Map<Integer, Button> buttons, String path, Runnable onClick, Object... pairs) {
        List<Integer> slots = slots(path);
        if (slots.isEmpty()) return;
        ButtonBuilder builder = builder(path, pairs);
        if (onClick != null) builder.onClick(onClick);
        Button button = builder.build();
        for (int slot : slots) buttons.put(slot, button);
    }

    /**
     * Places every child of {@code path} as a decorative item, never replacing a button already
     * placed. Call it after the menu's functional buttons.
     *
     * @param buttons the menu's buttons
     * @param path    the section holding free-form decorative items, e.g. {@code extra}
     */
    public void putExtras(Map<Integer, Button> buttons, String path) {
        ConfigurationSection section = section(path);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String itemPath = path + "." + key;
            Button button = builder(itemPath).build();
            for (int slot : slots(itemPath)) buttons.putIfAbsent(slot, button);
        }
    }

    /**
     * How a permission-gated button looks to a viewer who may not use it.
     */
    public enum GatedBehavior {
        /** The slot is left empty. */
        HIDE,
        /** The normal button is shown but does nothing. */
        DISABLE,
        /** The {@code <path>.denied} item is shown (or the normal one) and does nothing. */
        SHOW_DENIED;

        /**
         * Parses a configured value.
         *
         * @param raw      the value, may be {@code null}
         * @param fallback used for a missing or unknown value
         * @return the behaviour
         */
        public static GatedBehavior parse(String raw, GatedBehavior fallback) {
            if (raw == null || raw.isBlank()) return fallback;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    /**
     * Places a button that only some viewers may use, following its {@code denied-behavior} key
     * ({@link GatedBehavior#HIDE} by default) when {@code allowed} is {@code false}.
     *
     * @param buttons the menu's buttons
     * @param path    the item path
     * @param allowed whether the viewer may use it
     * @param onClick the click action for allowed viewers
     * @param pairs   alternating tokens and values
     */
    public void putGated(Map<Integer, Button> buttons, String path, boolean allowed, Runnable onClick,
                         Object... pairs) {
        ConfigurationSection section = section(path);
        int slot = slot(path);
        if (section == null || slot < 0) return;
        if (allowed) {
            buttons.put(slot, builder(path, pairs).onClick(onClick).build());
            return;
        }
        GatedBehavior behavior = GatedBehavior.parse(section.getString(style.resolve(section, "denied-behavior")),
                GatedBehavior.HIDE);
        switch (behavior) {
            case HIDE -> {
                // Nothing placed: the slot keeps whatever the background already put there.
            }
            case DISABLE -> buttons.put(slot, builder(path, pairs).build());
            case SHOW_DENIED -> {
                String deniedPath = path + ".denied";
                buttons.put(slot, (has(deniedPath) ? builder(deniedPath, pairs) : builder(path, pairs)).build());
            }
        }
    }

    /**
     * Returns the filler item at {@code path}.
     *
     * @param path the filler section, e.g. {@code filler}
     * @return the item, or {@code null} if the file has none
     */
    public ItemStack filler(String path) {
        return has(path) ? item(path) : null;
    }

    /**
     * Builds the previous and next page buttons of {@code menu} from two item paths. Each is placed
     * only when there is a page to go to.
     *
     * @param menu     the paginated menu
     * @param prevPath the previous-page item
     * @param nextPath the next-page item
     * @return the buttons by slot
     */
    public Map<Integer, Button> pageControls(PaginatedMenu<?> menu, String prevPath, String nextPath) {
        Map<Integer, Button> buttons = new HashMap<>();
        int page = menu.getPage();
        int maxPage = menu.getMaxPage();
        Object[] tokens = pageTokens(page, maxPage);
        int previous = slot(prevPath);
        if (previous >= 0 && page > 0) {
            buttons.put(previous, builder(prevPath, tokens).onClick(menu::previousPage).build());
        }
        int next = slot(nextPath);
        if (next >= 0 && page < maxPage) {
            buttons.put(next, builder(nextPath, tokens).onClick(menu::nextPage).build());
        }
        return buttons;
    }

    private static Object[] pageTokens(int page, int maxPage) {
        return new Object[]{
                "page", page + 1,
                "max_page", maxPage + 1,
                "next_page", Math.min(page + 2, maxPage + 1),
                "previous_page", Math.max(page, 1)
        };
    }

    // ─── plain values ────────────────────────────────────────────────────────────

    /**
     * Returns a string from the file, or {@code fallback}.
     *
     * @param path     the key
     * @param fallback used when missing
     * @return the value
     */
    public String string(String path, String fallback) {
        return file.contains(path) ? file.getString(path, fallback) : fallback;
    }

    /**
     * Returns an integer from the file, or {@code fallback}.
     *
     * @param path     the key
     * @param fallback used when missing
     * @return the value
     */
    public int integer(String path, int fallback) {
        return file.contains(path) ? file.getInt(path, fallback) : fallback;
    }

    /**
     * Returns a boolean from the file, or {@code fallback}.
     *
     * @param path     the key
     * @param fallback used when missing
     * @return the value
     */
    public boolean bool(String path, boolean fallback) {
        return file.contains(path) ? file.getBoolean(path, fallback) : fallback;
    }

    /**
     * Returns a list of integers from the file.
     *
     * @param path the key
     * @return the list, empty when missing
     */
    public List<Integer> intList(String path) {
        return file.contains(path) ? file.getIntegerList(path) : new ArrayList<>();
    }
}
