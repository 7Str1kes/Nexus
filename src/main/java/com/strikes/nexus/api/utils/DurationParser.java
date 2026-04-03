package com.strikes.nexus.api.utils;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DurationParser {
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhd])");

    public static Duration parse(String input) {
        Matcher matcher = TIME_PATTERN.matcher(input.toLowerCase());
        Duration duration = Duration.ZERO;

        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            duration = switch (unit) {
                case "s" -> duration.plusSeconds(value);
                case "m" -> duration.plusMinutes(value);
                case "h" -> duration.plusHours(value);
                case "d" -> duration.plusDays(value);
                default -> duration;
            };
        }

        return duration;
    }
}