package org.glstudio.nexus.utils;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Set;

public class BossBarBuilder {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private Component name = Component.empty();
    private float progress = 1.0f;
    private BossBar.Color color = BossBar.Color.WHITE;
    private BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;
    private final Set<BossBar.Flag> flags = EnumSet.noneOf(BossBar.Flag.class);

    public BossBarBuilder() {}

    public static BossBarBuilder of() {
        return new BossBarBuilder();
    }

    public BossBarBuilder name(String name) {
        this.name = SERIALIZER.deserialize(CC.t(name));
        return this;
    }

    public BossBarBuilder progress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        return this;
    }

    public BossBarBuilder color(BossBar.Color color) {
        this.color = color;
        return this;
    }

    public BossBarBuilder overlay(BossBar.Overlay overlay) {
        this.overlay = overlay;
        return this;
    }

    public BossBarBuilder flag(BossBar.Flag flag) {
        this.flags.add(flag);
        return this;
    }

    public BossBarBuilder createFog() {
        return flag(BossBar.Flag.CREATE_WORLD_FOG);
    }

    public BossBarBuilder darkenSky() {
        return flag(BossBar.Flag.DARKEN_SCREEN);
    }

    public BossBarBuilder playMusic() {
        return flag(BossBar.Flag.PLAY_BOSS_MUSIC);
    }

    public BossBar build() {
        return BossBar.bossBar(name, progress, color, overlay, flags);
    }

    public static void show(Player player, BossBar bossBar) {
        player.showBossBar(bossBar);
    }

    public static void hide(Player player, BossBar bossBar) {
        player.hideBossBar(bossBar);
    }

    public BossBar show(Player player) {
        BossBar bar = build();
        player.showBossBar(bar);
        return bar;
    }
}