package org.glstudio.nexus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colour and text helpers for both formats the plugins use: legacy {@code &} codes (with
 * {@code &#rrggbb} hex) and MiniMessage.
 *
 * <p>The MiniMessage path also accepts legacy codes, converting them to tags first, so a language
 * file can mix both while it is being migrated.
 */
public final class CC {

    private static final char SECTION = '§';
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final TagResolver USER_SAFE_TAGS = TagResolver.builder()
            .resolver(StandardTags.color())
            .resolver(StandardTags.decorations())
            .resolver(StandardTags.gradient())
            .resolver(StandardTags.rainbow())
            .resolver(StandardTags.reset())
            .build();

    private static final MiniMessage USER_SAFE_MINI = MiniMessage.builder().tags(USER_SAFE_TAGS).build();

    private static final Map<Character, String> SECTION_TAGS = new HashMap<>();

    static {
        SECTION_TAGS.put('0', "<black>");
        SECTION_TAGS.put('1', "<dark_blue>");
        SECTION_TAGS.put('2', "<dark_green>");
        SECTION_TAGS.put('3', "<dark_aqua>");
        SECTION_TAGS.put('4', "<dark_red>");
        SECTION_TAGS.put('5', "<dark_purple>");
        SECTION_TAGS.put('6', "<gold>");
        SECTION_TAGS.put('7', "<gray>");
        SECTION_TAGS.put('8', "<dark_gray>");
        SECTION_TAGS.put('9', "<blue>");
        SECTION_TAGS.put('a', "<green>");
        SECTION_TAGS.put('b', "<aqua>");
        SECTION_TAGS.put('c', "<red>");
        SECTION_TAGS.put('d', "<light_purple>");
        SECTION_TAGS.put('e', "<yellow>");
        SECTION_TAGS.put('f', "<white>");
        SECTION_TAGS.put('k', "<obfuscated>");
        SECTION_TAGS.put('l', "<bold>");
        SECTION_TAGS.put('m', "<strikethrough>");
        SECTION_TAGS.put('n', "<underlined>");
        SECTION_TAGS.put('o', "<italic>");
        SECTION_TAGS.put('r', "<reset>");
    }

    private static final Pattern HEX_PATTERN = Pattern.compile(
            "&#([A-Fa-f0-9]{6})" +
                    "|#([A-Fa-f0-9]{6})" +
                    "|&x&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])" +
                    "|§x§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])"
    );

    private CC() {
    }

    /**
     * Translates {@code &} colour codes and hex colours ({@code &#rrggbb}, {@code #rrggbb},
     * {@code &x&r&r&g&g&b&b}) to {@code §} codes.
     *
     * @param text the text, may be {@code null}
     * @return the translated text, empty for {@code null}
     */
    public static String t(String text) {
        if (text == null) return "";
        text = translateHex(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Applies {@link #t(String)} to every line.
     *
     * @param list the lines
     * @return a new list
     */
    public static List<String> t(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) result.add(t(s));
        return result;
    }

    private static String translateHex(String message) {
        if (message == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String hex;

            if (matcher.group(1) != null) {
                hex = matcher.group(1);
            } else if (matcher.group(2) != null) {
                hex = matcher.group(2);
            } else if (matcher.group(3) != null) {
                hex = matcher.group(3) + matcher.group(4) + matcher.group(5)
                        + matcher.group(6) + matcher.group(7) + matcher.group(8);
            } else {
                hex = matcher.group(9) + matcher.group(10) + matcher.group(11)
                        + matcher.group(12) + matcher.group(13) + matcher.group(14);
            }

            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }

            matcher.appendReplacement(buffer, replacement.toString());
        }

        return matcher.appendTail(buffer).toString();
    }

    /**
     * Removes every colour code, after translating {@code &} codes.
     *
     * @param text the text, may be {@code null}
     * @return plain text, empty for {@code null}
     */
    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(t(text));
    }

    /**
     * Parses legacy text ({@code &} codes and hex) into a component.
     *
     * @param text the text, may be {@code null}
     * @return the component
     */
    public static Component component(String text) {
        return LEGACY_SECTION.deserialize(t(text));
    }

    /**
     * Parses text written in {@code format}.
     *
     * @param text   the text, may be {@code null}
     * @param format the format it is written in
     * @return the component
     */
    public static Component component(String text, TextFormat format) {
        return format == TextFormat.MINIMESSAGE ? miniMessage(text) : component(text);
    }

    /**
     * Parses text written in {@code format}, replacing {@code %slot%} with a component (for example
     * an item's hover name) without passing that component through the parser.
     *
     * @param text      the text, may be {@code null}
     * @param format    the format it is written in
     * @param slot      the token name, without percent signs
     * @param slotValue the component inserted for the token
     * @return the component
     */
    public static Component component(String text, TextFormat format, String slot, Component slotValue) {
        if (text == null) return Component.empty();
        if (slot == null || slotValue == null) return component(text, format);

        if (format == TextFormat.MINIMESSAGE) {
            String template = sectionToMiniMessage(text).replace("%" + slot + "%", "<" + slot + ">");
            return MINI.deserialize(template, Placeholder.component(slot, slotValue));
        }

        String[] pieces = text.split(Pattern.quote("%" + slot + "%"), -1);
        Component result = component(pieces[0]);
        for (int i = 1; i < pieces.length; i++) {
            result = result.append(slotValue).append(component(pieces[i]));
        }
        return result;
    }

    /**
     * Parses MiniMessage, accepting legacy {@code &} and {@code §} codes as well.
     *
     * @param text the text, may be {@code null}
     * @return the component
     */
    public static Component miniMessage(String text) {
        if (text == null) return Component.empty();
        return MINI.deserialize(legacyToMiniMessage(text));
    }

    /**
     * Parses MiniMessage with a component placeholder {@code <slot>}.
     *
     * @param text      the text, may be {@code null}
     * @param slot      the tag name
     * @param slotValue the component inserted for the tag
     * @return the component
     */
    public static Component miniMessage(String text, String slot, Component slotValue) {
        if (text == null) return Component.empty();
        if (slot == null || slotValue == null) return miniMessage(text);
        return MINI.deserialize(legacyToMiniMessage(text), Placeholder.component(slot, slotValue));
    }

    /**
     * Parses text typed by a player in legacy format, never honouring {@code §} codes.
     *
     * @param text       the text
     * @param allowColor {@code false} to show the text literally
     * @return the component
     */
    public static Component componentOfUserInput(String text, boolean allowColor) {
        return componentOfUserInput(text, allowColor, TextFormat.LEGACY);
    }

    /**
     * Parses text typed by a player. In MiniMessage only colour and decoration tags are honoured, so
     * a player cannot inject click or hover events.
     *
     * @param text       the text
     * @param allowColor {@code false} to show the text literally
     * @param format     the format the player writes in
     * @return the component
     */
    public static Component componentOfUserInput(String text, boolean allowColor, TextFormat format) {
        String safe = stripSection(text);
        if (!allowColor) return Component.text(safe);

        return format == TextFormat.MINIMESSAGE
                ? USER_SAFE_MINI.deserialize(legacyToMiniMessage(safe))
                : component(safe);
    }

    /**
     * Escapes MiniMessage tags, so the text is shown literally when embedded in a template.
     *
     * @param text the text, may be {@code null}
     * @return the escaped text
     */
    public static String escapeMiniMessage(String text) {
        return text == null ? "" : MINI.escapeTags(text);
    }

    /**
     * Replaces every {@code §} with a space.
     *
     * @param text the text, may be {@code null}
     * @return the text without section signs
     */
    public static String stripSection(String text) {
        return text == null ? "" : text.replace(SECTION, ' ');
    }

    /**
     * Converts legacy codes to MiniMessage tags; an alias of {@link #legacyToMiniMessage(String)}.
     *
     * @param text the text
     * @return the converted text
     */
    public static String sectionToMiniMessage(String text) {
        return legacyToMiniMessage(text);
    }

    /**
     * Serialises a component to {@code §}-coloured legacy text, for APIs that only take strings.
     *
     * @param component the component
     * @return the legacy text
     */
    public static String legacy(Component component) {
        return component == null ? "" : LEGACY_SECTION.serialize(component);
    }

    /**
     * Returns the plain text of a component, without any formatting.
     *
     * @param component the component
     * @return the plain text
     */
    public static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Converts legacy {@code &}/{@code §} codes (including hex forms) to MiniMessage tags, leaving
     * everything else untouched.
     *
     * @param text the text, may be {@code null}
     * @return the converted text, empty for {@code null}
     */
    public static String legacyToMiniMessage(String text) {
        if (text == null) return "";
        if (text.indexOf(SECTION) < 0 && text.indexOf('&') < 0) return text;

        int length = text.length();
        StringBuilder out = new StringBuilder(length);
        int i = 0;

        while (i < length) {
            char marker = text.charAt(i);

            if ((marker != SECTION && marker != '&') || i + 1 >= length) {
                out.append(marker);
                i++;
                continue;
            }

            char next = text.charAt(i + 1);

            if (marker == '&' && next == '#' && i + 7 < length && isHex(text, i + 2, 6)) {
                out.append("<color:#").append(text, i + 2, i + 8).append('>');
                i += 8;
                continue;
            }

            if ((next == 'x' || next == 'X') && i + 13 < length) {
                StringBuilder hex = new StringBuilder(6);
                boolean valid = true;

                for (int k = 0; k < 6; k++) {
                    int position = i + 2 + k * 2;
                    if (text.charAt(position) != marker || !isHexDigit(text.charAt(position + 1))) {
                        valid = false;
                        break;
                    }
                    hex.append(text.charAt(position + 1));
                }

                if (valid) {
                    out.append("<color:#").append(hex).append('>');
                    i += 14;
                    continue;
                }
            }

            String tag = SECTION_TAGS.get(Character.toLowerCase(next));
            if (tag != null) {
                out.append(tag);
                i += 2;
                continue;
            }

            out.append(marker);
            i++;
        }

        return out.toString();
    }

    private static boolean isHex(String text, int start, int length) {
        for (int i = start; i < start + length; i++) {
            if (!isHexDigit(text.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
