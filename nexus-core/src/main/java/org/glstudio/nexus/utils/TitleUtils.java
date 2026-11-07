package org.glstudio.nexus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Titles and action bars from legacy text.
 */
public class TitleUtils {
    /**
     * Shows a title for 3.5 s with the default fades.
     *
     * @param player   the player
     * @param title    the title, legacy codes allowed
     * @param subtitle the subtitle, legacy codes allowed
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 70, 20);
    }
    /**
     * Shows a title.
     *
     * @param player   the player
     * @param title    the title, legacy codes allowed
     * @param subtitle the subtitle, legacy codes allowed
     * @param fadeIn   fade-in in ticks
     * @param stay     time shown in ticks
     * @param fadeOut  fade-out in ticks
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );
        player.showTitle(Title.title(toComponent(title), toComponent(subtitle), times));
    }
    /**
     * Shows only a subtitle.
     *
     * @param player   the player
     * @param subtitle the subtitle, legacy codes allowed
     */
    public static void sendSubtitle(Player player, String subtitle) {
        sendTitle(player, "", subtitle);
    }
    /**
     * Removes the current title.
     *
     * @param player the player
     */
    public static void clearTitle(Player player) {
        player.clearTitle();
    }
    /**
     * Shows text above the hotbar.
     *
     * @param player  the player
     * @param message the text, legacy codes allowed
     */
    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(toComponent(message));
    }
    /**
     * Shows text above the hotbar, sending an empty title to keep the client's timer running.
     *
     * @param player        the player
     * @param message       the text, legacy codes allowed
     * @param durationTicks how long the empty title stays
     */
    public static void sendActionBar(Player player, String message, int durationTicks) {
        sendTitle(player, "", "", 0, durationTicks, 0);
        sendActionBar(player, message);
    }

    private static Component toComponent(String text) {
        return CC.component(text);
    }
}
