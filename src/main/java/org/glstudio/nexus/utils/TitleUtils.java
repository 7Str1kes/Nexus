package org.glstudio.nexus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public class TitleUtils {

    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 70, 20);
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );
        player.showTitle(Title.title(toComponent(title), toComponent(subtitle), times));
    }

    public static void sendSubtitle(Player player, String subtitle) {
        sendTitle(player, "", subtitle);
    }

    public static void clearTitle(Player player) {
        player.clearTitle();
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(toComponent(message));
    }

    public static void sendActionBar(Player player, String message, int durationTicks) {
        sendTitle(player, "", "", 0, durationTicks, 0);
        sendActionBar(player, message);
    }

    private static Component toComponent(String text) {
        return CC.component(text);
    }
}