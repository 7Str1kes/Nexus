package org.glstudio.nexus.utils;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Set;

/**
 * Fluent creation of Adventure boss bars from legacy text.
 */
public class BossBarBuilder {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private Component name = Component.empty();
    private float progress = 1.0f;
    private BossBar.Color color = BossBar.Color.WHITE;
    private BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;
    private final Set<BossBar.Flag> flags = EnumSet.noneOf(BossBar.Flag.class);
    /**
     * Starts an empty white progress bar.
     */
    public BossBarBuilder() {}
    /**
     * Starts an empty white progress bar.
     *
     * @return the builder
     */
    public static BossBarBuilder of() {
        return new BossBarBuilder();
    }
    /**
     * Sets the name.
     *
     * @param name legacy text
     * @return this builder
     */
    public BossBarBuilder name(String name) {
        this.name = SERIALIZER.deserialize(CC.t(name));
        return this;
    }
    /**
     * Sets the progress, clamped to 0-1.
     *
     * @param progress the progress
     * @return this builder
     */
    public BossBarBuilder progress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        return this;
    }
    /**
     * Sets the colour.
     *
     * @param color the colour
     * @return this builder
     */
    public BossBarBuilder color(BossBar.Color color) {
        this.color = color;
        return this;
    }
    /**
     * Sets the overlay (segments).
     *
     * @param overlay the overlay
     * @return this builder
     */
    public BossBarBuilder overlay(BossBar.Overlay overlay) {
        this.overlay = overlay;
        return this;
    }
    /**
     * Adds a flag.
     *
     * @param flag the flag
     * @return this builder
     */
    public BossBarBuilder flag(BossBar.Flag flag) {
        this.flags.add(flag);
        return this;
    }
    /**
     * Adds world fog.
     *
     * @return this builder
     */
    public BossBarBuilder createFog() {
        return flag(BossBar.Flag.CREATE_WORLD_FOG);
    }
    /**
     * Darkens the sky.
     *
     * @return this builder
     */
    public BossBarBuilder darkenSky() {
        return flag(BossBar.Flag.DARKEN_SCREEN);
    }
    /**
     * Plays boss music.
     *
     * @return this builder
     */
    public BossBarBuilder playMusic() {
        return flag(BossBar.Flag.PLAY_BOSS_MUSIC);
    }
    /**
     * Creates the bar.
     *
     * @return the bar
     */
    public BossBar build() {
        return BossBar.bossBar(name, progress, color, overlay, flags);
    }
    /**
     * Shows a bar to a player.
     *
     * @param player  the player
     * @param bossBar the bar
     */
    public static void show(Player player, BossBar bossBar) {
        player.showBossBar(bossBar);
    }
    /**
     * Hides a bar from a player.
     *
     * @param player  the player
     * @param bossBar the bar
     */
    public static void hide(Player player, BossBar bossBar) {
        player.hideBossBar(bossBar);
    }
    /**
     * Creates the bar and shows it to a player.
     *
     * @param player the player
     * @return the bar, to hide it later
     */
    public BossBar show(Player player) {
        BossBar bar = build();
        player.showBossBar(bar);
        return bar;
    }
}