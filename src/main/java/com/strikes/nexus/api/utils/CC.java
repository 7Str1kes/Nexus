package com.strikes.nexus.api.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CC {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})|#([A-Fa-f0-9]{6})");

    public static String t(String text) {
        if (text == null) return "";
        text = translateHexColorCodes(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> t(List<String> textList) {
        List<String> translatedList = new ArrayList<>();
        for (String text : textList) {
            translatedList.add(t(text));
        }
        return translatedList;
    }

    public static String translateHexColorCodes(String message) {
        if (message == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String color = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(buffer, "\u00A7x");

            for (char c : color.toCharArray()) {
                buffer.append("\u00A7").append(c);
            }
        }

        return matcher.appendTail(buffer).toString();
    }
}