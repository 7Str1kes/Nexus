package org.glstudio.nexus.modules.menu.config;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.glstudio.nexus.modules.lang.LanguageFile;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.chat.ChatInputManager;
import org.glstudio.nexus.modules.menu.placeholder.Placeholders;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.nexus.utils.KeyStyle;
import org.glstudio.nexus.utils.TextFormat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What menus written in MiniMessage with kebab-case keys share: reading the file, titles, the slots
 * a list fills, item buttons whose name and lore come from the file, and asking something in chat.
 */
public final class MenuSupport {

    /** How long a question asked in chat waits for the answer. */
    public static final Duration INPUT_TIMEOUT = Duration.ofSeconds(30);

    private MenuSupport() {
    }

    /**
     * Wraps a menu file written in MiniMessage with kebab-case keys.
     *
     * @param file the file
     * @return the reader
     */
    public static MenuConfig config(ConfigFile file) {
        return new MenuConfig(file, KeyStyle.KEBAB, TextFormat.MINIMESSAGE);
    }

    /**
     * Returns the title of a menu in the legacy form the inventory constructor expects.
     *
     * @param config   the menu file
     * @param fallback used when the file has no title
     * @param pairs    alternating tokens and values
     * @return the title
     */
    public static String title(MenuConfig config, String fallback, Object... pairs) {
        return CC.legacy(CC.miniMessage(config.title(fallback, pairs)));
    }

    /**
     * Returns slots of the file (numbers and {@code "a-b"} ranges) that fit the menu, or every row but
     * the last when there are none.
     *
     * @param config the menu file
     * @param path   the key, such as {@code content-slots}
     * @param size   the menu size
     * @return the slots
     */
    public static List<Integer> slots(MenuConfig config, String path, int size) {
        List<Integer> configured = MenuConfigUtils.parseSlots(config.file(), path).stream()
                .filter(slot -> slot >= 0 && slot < size)
                .distinct()
                .toList();
        if (!configured.isEmpty()) return configured;
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < Math.max(9, size - 9); slot++) slots.add(slot);
        return slots;
    }

    /**
     * Returns the slots the entries of a list go into: {@code content-slots}, or every row but the
     * last.
     *
     * @param config the menu file
     * @param size   the menu size
     * @return the slots
     */
    public static List<Integer> contentSlots(MenuConfig config, int size) {
        return slots(config, "content-slots", size);
    }

    /**
     * Builds a button that shows an item, with the name and lore of the file at {@code path}. Without
     * a name in the file the item keeps its own.
     *
     * @param item   the item
     * @param config the menu file
     * @param path   the item path holding {@code name} and {@code lore}
     * @param pairs  alternating tokens and values
     * @return the builder
     */
    public static ButtonBuilder icon(ItemStack item, MenuConfig config, String path, Object... pairs) {
        ButtonBuilder builder = ButtonBuilder.of(item.clone())
                .amount(1)
                .lore(Placeholders.apply(config.lore(path), pairs), TextFormat.MINIMESSAGE)
                .hideAll();
        String name = config.name(path, null);
        if (name != null) builder.name(Placeholders.apply(name, pairs), TextFormat.MINIMESSAGE);
        if (config.bool(path + ".glow", false)) builder.glow();
        return builder;
    }

    /**
     * Builds a button that shows an item, like {@link #icon(ItemStack, MenuConfig, String, Object...)}.
     * A lore line that is only a placeholder is left out when the placeholder is empty, and a line
     * that is only {@code {description}} becomes the given lines.
     *
     * @param item        the item
     * @param config      the menu file
     * @param path        the item path holding {@code name} and {@code lore}
     * @param description the lines that replace {@code {description}}
     * @param values      the placeholders, such as {@code {name}}
     * @return the builder
     */
    public static ButtonBuilder icon(ItemStack item, MenuConfig config, String path, List<String> description,
                                     Map<String, Object> values) {
        Object[] pairs = pairs(values);
        List<String> lore = new ArrayList<>();
        for (String line : config.lore(path)) {
            String trimmed = line.trim();
            if (trimmed.equals("{description}")) {
                lore.addAll(description);
                continue;
            }
            if (values.containsKey(trimmed) && String.valueOf(values.get(trimmed)).isEmpty()) continue;
            lore.add(Placeholders.apply(line, pairs));
        }
        ButtonBuilder builder = ButtonBuilder.of(item.clone())
                .amount(1)
                .lore(lore, TextFormat.MINIMESSAGE)
                .hideAll();
        String name = config.name(path, null);
        if (name != null) builder.name(Placeholders.apply(name, pairs), TextFormat.MINIMESSAGE);
        if (config.bool(path + ".glow", false)) builder.glow();
        return builder;
    }

    /**
     * Turns placeholders into the alternating form Nexus takes.
     *
     * @param values the placeholders, such as {@code {name}}
     * @return tokens and values, alternating
     */
    public static Object[] pairs(Map<String, Object> values) {
        Object[] pairs = new Object[values.size() * 2];
        int i = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            pairs[i++] = entry.getKey();
            pairs[i++] = entry.getValue();
        }
        return pairs;
    }

    /**
     * Adds more tokens and values to some.
     *
     * @param pairs the pairs there are
     * @param more  the pairs to add
     * @return a new array with both
     */
    public static Object[] append(Object[] pairs, Object... more) {
        Object[] all = Arrays.copyOf(pairs, pairs.length + more.length);
        System.arraycopy(more, 0, all, pairs.length, more.length);
        return all;
    }

    /**
     * Asks a player something in chat, closing their menu. {@code cancel} or {@code cancelar}, or no
     * answer in {@link #INPUT_TIMEOUT}, cancels; a player who walked away does not get the menu back.
     *
     * @param input        the plugin's chat input
     * @param messages     the plugin's messages
     * @param player       who is asked
     * @param key          the message that asks, sent with the prefix
     * @param cancelledKey the message sent when they cancel or do not answer
     * @param onInput      gets the answer
     * @param onCancel     runs when they cancel by typing
     * @param pairs        alternating tokens and values of the question
     */
    public static void prompt(ChatInputManager input, LanguageFile messages, Player player, String key, String cancelledKey,
                              Consumer<String> onInput, Runnable onCancel, Object... pairs) {
        input.request(player)
                .prompt(messages.render(player, messages.prefix() + messages.raw(key, ""), pairs))
                .cancelWords("cancel", "cancelar")
                .timeout(INPUT_TIMEOUT)
                .closeInventory(true)
                .onInput(onInput)
                .onCancel(() -> {
                    messages.send(player, cancelledKey);
                    onCancel.run();
                })
                .onTimeout(() -> messages.send(player, cancelledKey))
                .start();
    }
}
