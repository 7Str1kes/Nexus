package com.strikes.nexus.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class LocationUtils {

    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + "," +
                loc.getX() + "," +
                loc.getY() + "," +
                loc.getZ() + "," +
                loc.getYaw() + "," +
                loc.getPitch();
    }

    public static String serializeSimple(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + "," +
                loc.getX() + "," +
                loc.getY() + "," +
                loc.getZ();
    }

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

    public static boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX() &&
                a.getBlockY() == b.getBlockY() &&
                a.getBlockZ() == b.getBlockZ();
    }

    public static boolean isSameWorld(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().equals(b.getWorld());
    }

    public static double distance2D(Location a, Location b) {
        if (!isSameWorld(a, b)) return -1;
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean isWithinRadius(Location a, Location b, double radius) {
        if (!isSameWorld(a, b)) return false;
        return a.distanceSquared(b) <= radius * radius;
    }

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

    public static Location toBlockLocation(Location loc) {
        if (loc == null) return null;
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static String toReadable(Location loc) {
        if (loc == null || loc.getWorld() == null) return "unknown";
        return loc.getWorld().getName() + " @ " +
                loc.getBlockX() + ", " +
                loc.getBlockY() + ", " +
                loc.getBlockZ();
    }
}