package org.glstudio.nexus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CC {

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private static final Pattern HEX_PATTERN = Pattern.compile(
            "&#([A-Fa-f0-9]{6})" +
                    "|#([A-Fa-f0-9]{6})" +
                    "|&x&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])" +
                    "|\u00A7x\u00A7([A-Fa-f0-9])\u00A7([A-Fa-f0-9])\u00A7([A-Fa-f0-9])\u00A7([A-Fa-f0-9])\u00A7([A-Fa-f0-9])\u00A7([A-Fa-f0-9])"
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
                // &#RRGGBB
                hex = matcher.group(1);
            } else if (matcher.group(2) != null) {
                // #RRGGBB
                hex = matcher.group(2);
            } else if (matcher.group(3) != null) {
                // &x&R&R&G&G&B&B
                hex = matcher.group(3) + matcher.group(4) + matcher.group(5)
                        + matcher.group(6) + matcher.group(7) + matcher.group(8);
            } else {
                // §x§R§R§G§G§B§B
                hex = matcher.group(9) + matcher.group(10) + matcher.group(11)
                        + matcher.group(12) + matcher.group(13) + matcher.group(14);
            }

            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : hex.toCharArray()) {
                replacement.append("\u00A7").append(c);
            }

            matcher.appendReplacement(buffer, replacement.toString());
        }

        return matcher.appendTail(buffer).toString();
    }

    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(t(text));
    }

    /**
     * Turns trusted text — a config line, a language entry, a resolved placeholder — into a
     * styled {@link Component}.
     *
     * <p>Deserializing with the section sign rather than the ampersand is what makes this
     * correct, and it is not a detail. Anything that has already been translated arrives here
     * carrying section signs: PlaceholderAPI returns {@code %luckperms_prefix%} as
     * {@code §c[Owner]}, and {@link #t(String)} folds {@code &c} and {@code &#RRGGBB} into the
     * same form. An ampersand deserializer does not recognise those, so they survive as
     * literal characters in the component's text instead of becoming style:
     *
     * <pre>
     * legacyAmpersand().deserialize("§c[Owner]") → "§c[Owner]"
     * legacySection().deserialize("§c[Owner]")   → {"color":"red","text":"[Owner]"}
     * </pre>
     *
     * <p>A component carrying control characters renders differently depending on how it
     * reaches the client — the section signs are reprocessed in a system message but not in the
     * body of a player-chat packet — so the same line can come out coloured on one server and
     * plain on another.
     */
    public static Component component(String text) {
        return SECTION.deserialize(t(text));
    }

    /**
     * Turns text a player typed into a component.
     *
     * <p>Section signs are always removed: a player must never be able to smuggle raw style
     * into a component, whether or not they are allowed colours. {@code &} codes are honoured
     * only when {@code allowColor} is set, otherwise they are shown as typed.
     */
    public static Component componentOfUserInput(String text, boolean allowColor) {
        String safe = stripSection(text);
        return allowColor ? component(safe) : Component.text(safe);
    }

    /** Removes raw section signs, so untrusted text cannot carry style of its own. */
    public static String stripSection(String text) {
        return text == null ? "" : text.replace('§', ' ');
    }
}