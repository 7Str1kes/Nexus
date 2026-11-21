package org.glstudio.nexus.utils;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A firework as config files describe it: shape, colours, the colours it fades to, flicker, trail
 * and how high it flies. Colour names are those of {@link Colors#BUKKIT}, and {@code #RRGGBB} works
 * too.
 *
 * <pre>
 * type: BALL_LARGE        # BALL, BALL_LARGE, STAR, BURST or CREEPER
 * colors: [AQUA, "#FFAA00"]
 * fade-colors: [WHITE]
 * flicker: true
 * trail: true
 * power: 1                # 0 bursts where it is launched
 * </pre>
 *
 * @param type    the shape of the burst
 * @param colors  its colours, at least one
 * @param fades   the colours it fades to
 * @param flicker whether it twinkles
 * @param trail   whether it leaves a trail
 * @param power   how high it flies, 0 to 3; 0 bursts at once
 */
public record FireworkSpec(FireworkEffect.Type type, List<Color> colors, List<Color> fades, boolean flicker,
                           boolean trail, int power) {

    /**
     * Creates the spec.
     */
    public FireworkSpec {
        colors = colors.isEmpty() ? List.of(Color.WHITE) : List.copyOf(colors);
        fades = List.copyOf(fades);
        power = Math.max(0, Math.min(3, power));
    }

    /**
     * Reads a firework section, keeping the defaults' values for what it leaves out. Unknown shapes
     * and colours are reported and left out.
     *
     * @param section  the section, may be {@code null}
     * @param defaults what a missing key keeps
     * @param problems where problems are added, one line each
     * @return the firework
     */
    public static FireworkSpec read(@Nullable ConfigurationSection section, FireworkSpec defaults, List<String> problems) {
        if (section == null) return defaults;
        String where = section.getCurrentPath() == null || section.getCurrentPath().isEmpty() ? "" : section.getCurrentPath() + ".";
        FireworkEffect.Type type = defaults.type();
        if (section.isSet("type")) {
            Optional<FireworkEffect.Type> read = type(section.getString("type", ""));
            if (read.isPresent()) {
                type = read.get();
            } else {
                problems.add(where + "type: unknown shape '" + section.getString("type") + "'");
            }
        }
        List<Color> colors = section.isSet("colors") ? colors(section.getStringList("colors"), where + "colors", problems)
                : defaults.colors();
        List<Color> fades = section.isSet("fade-colors")
                ? colors(section.getStringList("fade-colors"), where + "fade-colors", problems) : defaults.fades();
        return new FireworkSpec(type, colors.isEmpty() ? defaults.colors() : colors, fades,
                section.getBoolean("flicker", defaults.flicker()), section.getBoolean("trail", defaults.trail()),
                section.getInt("power", defaults.power()));
    }

    /**
     * Reads a shape, ignoring case.
     *
     * @param name the name, such as {@code BALL_LARGE}
     * @return the shape, or empty if unknown
     */
    public static Optional<FireworkEffect.Type> type(String name) {
        try {
            return Optional.of(FireworkEffect.Type.valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /**
     * Reads colours, with the names of {@link Colors#BUKKIT}; unknown ones are reported and left out.
     *
     * @param raw      the colours as written
     * @param path     where they were read, for problems
     * @param problems where problems are added
     * @return the colours read
     */
    public static List<Color> colors(List<String> raw, String path, List<String> problems) {
        List<Color> colors = new ArrayList<>();
        for (String value : raw) {
            Optional<Color> color = Colors.parse(value, Colors.BUKKIT);
            if (color.isPresent()) {
                colors.add(color.get());
            } else {
                problems.add(path + ": unknown colour '" + value + "'");
            }
        }
        return colors;
    }

    /**
     * Builds the burst.
     *
     * @return the firework effect
     */
    public FireworkEffect effect() {
        return FireworkEffect.builder().with(type).withColor(colors).withFade(fades).flicker(flicker).trail(trail).build();
    }

    /**
     * Launches the firework. Main thread only.
     *
     * @param location where
     * @return the rocket, or {@code null} if the location has no world
     */
    public @Nullable Firework launch(Location location) {
        return launch(location, firework -> { });
    }

    /**
     * Launches the firework, letting the caller mark the rocket first, for example so its burst can
     * be kept from hurting anyone. Main thread only.
     *
     * @param location where
     * @param before   called on the rocket before it flies
     * @return the rocket, or {@code null} if the location has no world
     */
    public @Nullable Firework launch(Location location, Consumer<Firework> before) {
        if (location.getWorld() == null) return null;
        Firework firework = location.getWorld().spawn(location, Firework.class, rocket -> {
            FireworkMeta meta = rocket.getFireworkMeta();
            meta.addEffect(effect());
            meta.setPower(power);
            rocket.setFireworkMeta(meta);
            before.accept(rocket);
        });
        if (power == 0) firework.detonate();
        return firework;
    }
}
