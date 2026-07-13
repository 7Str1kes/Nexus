package org.glstudio.nexus.utils.cuboid;

import com.google.common.base.Preconditions;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Getter
public class Ellipsoid implements Iterable<Block>, Cloneable {

    protected String worldName;
    protected int centerX;
    protected int centerY;
    protected int centerZ;
    protected int radiusX;
    protected int radiusY;
    protected int radiusZ;

    public Ellipsoid(Location center, int radiusX, int radiusY, int radiusZ) {
        Preconditions.checkNotNull(center, "Center location cannot be null");
        Preconditions.checkArgument(radiusX > 0 && radiusY > 0 && radiusZ > 0, "All radii must be positive");

        this.worldName = center.getWorld().getName();
        this.centerX = center.getBlockX();
        this.centerY = center.getBlockY();
        this.centerZ = center.getBlockZ();
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
    }

    public Ellipsoid(String worldName, int centerX, int centerY, int centerZ, int radiusX, int radiusY, int radiusZ) {
        Preconditions.checkNotNull(worldName, "World name cannot be null");
        Preconditions.checkArgument(radiusX > 0 && radiusY > 0 && radiusZ > 0, "All radii must be positive");

        this.worldName = worldName;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
    }

    public Ellipsoid(Ellipsoid other) {
        this(other.worldName, other.centerX, other.centerY, other.centerZ, other.radiusX, other.radiusY, other.radiusZ);
    }

    public World getWorld() {
        return Bukkit.getWorld(this.worldName);
    }

    public Location getCenter() {
        return new Location(getWorld(), centerX + 0.5, centerY + 0.5, centerZ + 0.5);
    }

    public double getVolume() {
        return (4.0 / 3.0) * Math.PI * radiusX * radiusY * radiusZ;
    }

    public boolean contains(int x, int y, int z) {
        double dx = (double) (x - centerX) / radiusX;
        double dy = (double) (y - centerY) / radiusY;
        double dz = (double) (z - centerZ) / radiusZ;
        return (dx * dx + dy * dy + dz * dz) <= 1.0;
    }

    public boolean contains(Location location) {
        if (location == null || worldName == null) return false;
        World world = location.getWorld();
        return world != null && worldName.equals(location.getWorld().getName()) && contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(Block block) {
        return contains(block.getLocation());
    }

    public boolean contains(Player player) {
        return contains(player.getLocation());
    }

    public boolean contains(Entity entity) {
        return entity != null && contains(entity.getLocation());
    }

    public Set<Player> getPlayers() {
        Set<Player> players = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (contains(player)) players.add(player);
        }
        return players;
    }

    public List<Entity> getEntities() {
        List<Entity> entities = new ArrayList<>();
        World world = getWorld();
        if (world == null) return entities;
        for (Entity entity : world.getEntities()) {
            if (contains(entity.getLocation())) entities.add(entity);
        }
        return entities;
    }

    public List<LivingEntity> getLivingEntities() {
        List<LivingEntity> entities = new ArrayList<>();
        World world = getWorld();
        if (world == null) return entities;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (contains(entity.getLocation())) entities.add(entity);
        }
        return entities;
    }

    public List<LivingEntity> getLivingEntities(EntityType... types) {
        Set<EntityType> filter = EnumSet.noneOf(EntityType.class);
        Collections.addAll(filter, types);
        List<LivingEntity> entities = new ArrayList<>();
        for (LivingEntity entity : getLivingEntities()) {
            if (filter.contains(entity.getType())) entities.add(entity);
        }
        return entities;
    }

    public int kill(Predicate<LivingEntity> filter) {
        int killed = 0;
        for (LivingEntity entity : getLivingEntities()) {
            if (filter.test(entity)) {
                entity.setHealth(0);
                killed++;
            }
        }
        return killed;
    }

    public int killAll() {
        return kill(entity -> !(entity instanceof Player));
    }

    public int killOnly(EntityType... types) {
        Set<EntityType> filter = EnumSet.noneOf(EntityType.class);
        Collections.addAll(filter, types);
        return kill(entity -> filter.contains(entity.getType()));
    }

    public int killAllExcept(EntityType... types) {
        Set<EntityType> filter = EnumSet.noneOf(EntityType.class);
        Collections.addAll(filter, types);
        return kill(entity -> !(entity instanceof Player) && !filter.contains(entity.getType()));
    }

    public List<Block> getBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : this) blocks.add(block);
        return blocks;
    }

    public List<Block> getSurface() {
        List<Block> blocks = new ArrayList<>();
        World world = getWorld();
        if (world == null) return blocks;

        double rxSq = (double) radiusX * radiusX;
        double rySq = (double) radiusY * radiusY;
        double rzSq = (double) radiusZ * radiusZ;

        for (int x = centerX - radiusX; x <= centerX + radiusX; x++) {
            for (int y = centerY - radiusY; y <= centerY + radiusY; y++) {
                for (int z = centerZ - radiusZ; z <= centerZ + radiusZ; z++) {
                    double dx = (double) (x - centerX) / radiusX;
                    double dy = (double) (y - centerY) / radiusY;
                    double dz = (double) (z - centerZ) / radiusZ;
                    double value = dx * dx + dy * dy + dz * dz;
                    if (value > 1.0) continue;

                    double dx1 = (double) (x + 1 - centerX) / radiusX;
                    double dz1 = (double) (z + 1 - centerZ) / radiusZ;
                    double dy1 = (double) (y + 1 - centerY) / radiusY;

                    boolean onSurface = dx1 * dx1 + dy * dy + dz * dz > 1.0
                            || dx * dx + dy1 * dy1 + dz * dz > 1.0
                            || dx * dx + dy * dy + dz1 * dz1 > 1.0;

                    if (onSurface) {
                        blocks.add(world.getBlockAt(x, y, z));
                    }
                }
            }
        }
        return blocks;
    }

    public List<Chunk> getChunks() {
        World world = getWorld();
        List<Chunk> result = new ArrayList<>();
        if (world == null) return result;

        int minChunkX = (centerX - radiusX) >> 4;
        int maxChunkX = (centerX + radiusX) >> 4;
        int minChunkZ = (centerZ - radiusZ) >> 4;
        int maxChunkZ = (centerZ + radiusZ) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                result.add(world.getChunkAt(cx, cz));
            }
        }
        return result;
    }

    public boolean containsOnly(Material material) {
        for (Block block : this) {
            if (block.getType() != material) return false;
        }
        return true;
    }

    @Override
    public Iterator<Block> iterator() {
        return new EllipsoidBlockIterator(getWorld(), centerX, centerY, centerZ, radiusX, radiusY, radiusZ);
    }

    public Iterator<Location> locationIterator() {
        World world = getWorld();
        return new Iterator<Location>() {
            private final Iterator<Block> blockIterator = new EllipsoidBlockIterator(world, centerX, centerY, centerZ, radiusX, radiusY, radiusZ);

            @Override
            public boolean hasNext() {
                return blockIterator.hasNext();
            }

            @Override
            public Location next() {
                return blockIterator.next().getLocation();
            }
        };
    }

    @Override
    public Ellipsoid clone() {
        try {
            return (Ellipsoid) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException("This could never happen", ex);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ellipsoid)) return false;
        Ellipsoid ellipsoid = (Ellipsoid) o;
        return centerX == ellipsoid.centerX && centerY == ellipsoid.centerY && centerZ == ellipsoid.centerZ && radiusX == ellipsoid.radiusX && radiusY == ellipsoid.radiusY && radiusZ == ellipsoid.radiusZ && worldName.equals(ellipsoid.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, centerX, centerY, centerZ, radiusX, radiusY, radiusZ);
    }

    @Override
    public String toString() {
        return "Ellipsoid: " + worldName + ",center=" + centerX + "," + centerY + "," + centerZ + ",rx=" + radiusX + ",ry=" + radiusY + ",rz=" + radiusZ;
    }
}