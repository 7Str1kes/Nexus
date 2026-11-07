package org.glstudio.nexus.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Location helpers and the {@code world,x,y,z[,yaw,pitch]} text format.
 */
public class LocationUtils {
    /**
     * Writes {@code world,x,y,z,yaw,pitch}.
     *
     * @param loc the location
     * @return the text, empty if the location or its world is {@code null}
     */
    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + "," +
                loc.getX() + "," +
                loc.getY() + "," +
                loc.getZ() + "," +
                loc.getYaw() + "," +
                loc.getPitch();
    }
    /**
     * Writes {@code world,x,y,z}.
     *
     * @param loc the location
     * @return the text, empty if the location or its world is {@code null}
     */
    public static String serializeSimple(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + "," +
                loc.getX() + "," +
                loc.getY() + "," +
                loc.getZ();
    }
    /**
     * Reads {@code world,x,y,z[,yaw,pitch]}.
     *
     * @param raw the text
     * @return the location, or {@code null} if malformed or the world is not loaded
     */
    public static Location deserialize(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        try {
            String[] parts = raw.split(",");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;

            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            if (parts.length == 6) {
                float yaw = Float.parseFloat(parts[4]);
                float pitch = Float.parseFloat(parts[5]);
                return new Location(world, x, y, z, yaw, pitch);
            }

            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
    /**
     * Returns whether two locations are in the same block.
     *
     * @param a a location
     * @param b another location
     * @return {@code true} if same world and block
     */
    public static boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX() &&
                a.getBlockY() == b.getBlockY() &&
                a.getBlockZ() == b.getBlockZ();
    }
    /**
     * Returns whether two locations are in the same world.
     *
     * @param a a location
     * @param b another location
     * @return {@code true} if both worlds are set and equal
     */
    public static boolean isSameWorld(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().equals(b.getWorld());
    }
    /**
     * Returns the horizontal distance.
     *
     * @param a a location
     * @param b another location
     * @return the distance, or -1 in different worlds
     */
    public static double distance2D(Location a, Location b) {
        if (!isSameWorld(a, b)) return -1;
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
    /**
     * Returns whether two locations are within {@code radius} of each other.
     *
     * @param a      a location
     * @param b      another location
     * @param radius the radius
     * @return {@code true} if in the same world and close enough
     */
    public static boolean isWithinRadius(Location a, Location b, double radius) {
        if (!isSameWorld(a, b)) return false;
        return a.distanceSquared(b) <= radius * radius;
    }
    /**
     * Returns the location moved to the horizontal centre of its block, keeping Y and rotation.
     *
     * @param loc the location
     * @return the centred location, or {@code null}
     */
    public static Location centerBlock(Location loc) {
        if (loc == null) return null;
        return new Location(
                loc.getWorld(),
                loc.getBlockX() + 0.5,
                loc.getBlockY(),
                loc.getBlockZ() + 0.5,
                loc.getYaw(),
                loc.getPitch()
        );
    }
    /**
     * Returns the location rounded down to its block, without rotation.
     *
     * @param loc the location
     * @return the block location, or {@code null}
     */
    public static Location toBlockLocation(Location loc) {
        if (loc == null) return null;
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    /**
     * Returns {@code world @ x, y, z} for messages.
     *
     * @param loc the location
     * @return the text, {@code unknown} if not set
     */
    public static String toReadable(Location loc) {
        if (loc == null || loc.getWorld() == null) return "unknown";
        return loc.getWorld().getName() + " @ " +
                loc.getBlockX() + ", " +
                loc.getBlockY() + ", " +
                loc.getBlockZ();
    }
}