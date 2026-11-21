package org.glstudio.nexus.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * Where something stands and which way it faces, kept by world name so a place in a world that is
 * not loaded yet is not lost. Coordinates are rounded to a thousandth of a block and angles to a
 * tenth of a degree, so files stay readable.
 *
 * @param world the world's name
 * @param x     the x coordinate
 * @param y     the y coordinate
 * @param z     the z coordinate
 * @param yaw   the yaw
 * @param pitch the pitch
 */
public record Position(String world, double x, double y, double z, float yaw, float pitch) {

    /**
     * Copies a location.
     *
     * @param location the location; its world must be set
     * @return the position
     */
    public static Position of(Location location) {
        return new Position(location.getWorld().getName(), round(location.getX(), 1000), round(location.getY(), 1000),
                round(location.getZ(), 1000), (float) round(location.getYaw(), 10), (float) round(location.getPitch(), 10));
    }

    /**
     * Reads a section with {@code world}, {@code x}, {@code y}, {@code z}, {@code yaw} and
     * {@code pitch}.
     *
     * @param section the section, may be {@code null}
     * @return the position, or {@code null} if there is no section or it names no world
     */
    public static @Nullable Position read(@Nullable ConfigurationSection section) {
        if (section == null || section.getString("world", "").isBlank()) return null;
        return new Position(section.getString("world"), section.getDouble("x"), section.getDouble("y"),
                section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    /**
     * Writes the position under a path, as {@link #read(ConfigurationSection)} reads it back.
     *
     * @param target the section to write into
     * @param path   the path, such as {@code location}
     */
    public void write(ConfigurationSection target, String path) {
        target.set(path + ".world", world);
        target.set(path + ".x", x);
        target.set(path + ".y", y);
        target.set(path + ".z", z);
        target.set(path + ".yaw", (double) yaw);
        target.set(path + ".pitch", (double) pitch);
    }

    /**
     * Returns the location, if the world is loaded.
     *
     * @return the location, or {@code null}
     */
    public @Nullable Location toLocation() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z, yaw, pitch);
    }

    /**
     * Returns this position moved by an offset.
     *
     * @param dx along x
     * @param dy along y
     * @param dz along z
     * @return the moved position
     */
    public Position add(double dx, double dy, double dz) {
        return new Position(world, round(x + dx, 1000), round(y + dy, 1000), round(z + dz, 1000), yaw, pitch);
    }

    /**
     * Returns this position turned.
     *
     * @param yaw   the new yaw
     * @param pitch the new pitch
     * @return the turned position
     */
    public Position withRotation(float yaw, float pitch) {
        return new Position(world, x, y, z, (float) round(yaw, 10), (float) round(pitch, 10));
    }

    /**
     * Returns the x of the chunk this position is in.
     *
     * @return the chunk x
     */
    public int chunkX() {
        return (int) Math.floor(x) >> 4;
    }

    /**
     * Returns the z of the chunk this position is in.
     *
     * @return the chunk z
     */
    public int chunkZ() {
        return (int) Math.floor(z) >> 4;
    }

    private static double round(double value, int scale) {
        return Math.round(value * scale) / (double) scale;
    }
}
