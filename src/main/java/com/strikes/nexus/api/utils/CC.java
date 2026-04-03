package com.strikes.nexus.api.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CC {

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
}