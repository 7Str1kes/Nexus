package org.glstudio.nexus.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * A particle effect read from configuration. Only particles that need no extra data (colour, block)
 * are accepted.
 *
 * <p>Two configuration shapes are supported: an inline string
 * ({@code FLAME:20:0.5:0.5:0.5:0.01}, everything after the name optional) and a section with
 * {@code enabled}, {@code type}, {@code amount}/{@code count}, {@code spread} or
 * {@code offset-x/-y/-z}, and {@code speed}/{@code extra}.
 *
 * @param particle the particle, or {@code null} for none
 * @param count    how many, 0 to 500
 * @param dx       spread on X
 * @param dy       spread on Y
 * @param dz       spread on Z
 * @param speed    the particle speed ("extra")
 */
public record ParticleSpec(Particle particle, int count, double dx, double dy, double dz, double speed) {

    /** No particles. */
    public static final ParticleSpec NONE = new ParticleSpec(null, 0, 0, 0, 0, 0);

    private static final int MAX_COUNT = 500;

    /**
     * Creates a spec, clamping the count to 0-500.
     *
     * @param particle the particle, or {@code null}
     * @param count    how many
     * @param dx       spread on X
     * @param dy       spread on Y
     * @param dz       spread on Z
     * @param speed    the particle speed
     */
    public ParticleSpec {
        count = Math.max(0, Math.min(MAX_COUNT, count));
    }

    /**
     * Parses {@code <particle>[:<count>[:<x>[:<y>[:<z>[:<speed>]]]]]}; missing values default to
     * 10 particles, 0.5 spread and speed 0.
     *
     * @param raw the configured value; {@code null}, blank or {@code NONE} give {@link #NONE}
     * @return the spec, {@link #NONE} if the particle is unknown, needs data, or a number is invalid
     */
    public static ParticleSpec parse(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("NONE")) return NONE;
        String[] parts = raw.trim().split(":");
        Particle particle = Registries.particle(parts[0]).orElse(null);
        if (particle == null || particle.getDataType() != Void.class) return NONE;
        try {
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
            double x = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.5;
            double y = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.5;
            double z = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.5;
            double speed = parts.length > 5 ? Double.parseDouble(parts[5]) : 0;
            return new ParticleSpec(particle, count, x, y, z, speed);
        } catch (NumberFormatException e) {
            return NONE;
        }
    }

    /**
     * Reads a section; missing keys keep the fallback's values.
     *
     * @param section  the section, may be {@code null}
     * @param fallback used when the section is missing, and for missing keys
     * @param problems where to record problems, may be {@code null}
     * @param path     the section path, used in problem messages
     * @return the spec, {@link #NONE} if disabled or unusable
     */
    public static ParticleSpec fromSection(ConfigurationSection section, ParticleSpec fallback,
                                           ConfigProblems problems, String path) {
        if (section == null) return fallback;
        if (!section.getBoolean("enabled", true)) return NONE;

        Particle particle = fallback.particle();
        String typeKey = section.isSet("type") ? "type" : "particle";
        if (section.isSet(typeKey)) {
            String raw = section.getString(typeKey, "");
            particle = Registries.particle(raw).filter(found -> found.getDataType() == Void.class).orElse(null);
            if (particle == null && problems != null) {
                problems.add(path + "." + typeKey + " is '" + raw + "', which is unknown or needs extra data "
                        + "(colour, block); no particles will spawn.");
            }
        }
        int count = section.getInt(section.isSet("amount") ? "amount" : "count", fallback.count());
        if (problems != null && (count < 0 || count > MAX_COUNT)) {
            problems.add(path + ".amount is " + count + "; clamping to 0-" + MAX_COUNT + ".");
        }
        double dx;
        double dy;
        double dz;
        if (section.isSet("spread")) {
            double spread = Math.max(0, section.getDouble("spread"));
            dx = spread;
            dy = spread;
            dz = spread;
        } else {
            dx = section.getDouble("offset-x", fallback.dx());
            dy = section.getDouble("offset-y", fallback.dy());
            dz = section.getDouble("offset-z", fallback.dz());
        }
        double speed = Math.max(0, section.getDouble(section.isSet("speed") ? "speed" : "extra", fallback.speed()));
        return new ParticleSpec(particle, count, dx, dy, dz, speed);
    }

    /**
     * Returns whether this spec spawns nothing.
     *
     * @return {@code true} for no particles
     */
    public boolean isNone() {
        return particle == null || count <= 0;
    }

    /**
     * Spawns the particles at {@code location}, visible to everyone nearby.
     *
     * @param location where to spawn them
     */
    public void spawn(Location location) {
        if (isNone() || location == null) return;
        World world = location.getWorld();
        if (world != null) world.spawnParticle(particle, location, count, dx, dy, dz, speed);
    }

    /**
     * Spawns the particles at {@code location}, visible only to {@code viewer}.
     *
     * @param viewer   who sees them
     * @param location where to spawn them
     */
    public void spawnFor(Player viewer, Location location) {
        if (isNone() || viewer == null || location == null) return;
        viewer.spawnParticle(particle, location, count, dx, dy, dz, speed);
    }
}
