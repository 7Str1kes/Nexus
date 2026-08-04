package org.glstudio.nexus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CC {
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

    public static String t(String text) {
        if (text == null) return "";
        text = translateHex(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> t(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) result.add(t(s));
        return result;
    }

    private static String translateHex(String message) {
        if (message == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

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

    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(t(text));
    }

    public static Component component(String text) {
        return LEGACY_SECTION.deserialize(t(text));
    }

    public static Component component(String text, TextFormat format) {
        return format == TextFormat.MINIMESSAGE ? miniMessage(text) : component(text);
    }

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

    public static Component miniMessage(String text) {
        if (text == null) return Component.empty();
        return MINI.deserialize(sectionToMiniMessage(text));
    }

    public static Component miniMessage(String text, String slot, Component slotValue) {
        if (text == null) return Component.empty();
        if (slot == null || slotValue == null) return miniMessage(text);
        return MINI.deserialize(sectionToMiniMessage(text), Placeholder.component(slot, slotValue));
    }

    public static Component componentOfUserInput(String text, boolean allowColor) {
        return componentOfUserInput(text, allowColor, TextFormat.LEGACY);
    }

    public static Component componentOfUserInput(String text, boolean allowColor, TextFormat format) {
        String safe = stripSection(text);
        if (!allowColor) return Component.text(safe);

        return format == TextFormat.MINIMESSAGE
                ? USER_SAFE_MINI.deserialize(safe)
                : component(safe);
    }

    public static String escapeMiniMessage(String text) {
        return text == null ? "" : MINI.escapeTags(text);
    }

    public static String stripSection(String text) {
        return text == null ? "" : text.replace(SECTION, ' ');
    }

    public static String sectionToMiniMessage(String text) {
        if (text == null) return "";
        if (text.indexOf(SECTION) < 0) return text;

        StringBuilder out = new StringBuilder(text.length());
        int i = 0;

        while (i < text.length()) {
            char c = text.charAt(i);

            if (c != SECTION || i + 1 >= text.length()) {
                out.append(c);
                i++;
                continue;
            }

            char code = Character.toLowerCase(text.charAt(i + 1));

            if (code == 'x' && i + 13 < text.length()) {
                StringBuilder hex = new StringBuilder(6);
                boolean valid = true;

                for (int k = 0; k < 6; k++) {
                    int position = i + 2 + k * 2;
                    if (text.charAt(position) != SECTION) {
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

            String tag = SECTION_TAGS.get(code);
            if (tag != null) {
                out.append(tag);
                i += 2;
                continue;
            }

            out.append(c);
            i++;
        }

        return out.toString();
    }
}
