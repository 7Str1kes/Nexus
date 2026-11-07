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

/**
 * An axis-aligned ellipsoid of blocks, iterable block by block.
 */
@Getter
public class Ellipsoid implements Iterable<Block>, Cloneable {

    /** Name of the world. */
    protected String worldName;
    /** Centre block X. */
    protected int centerX;
    /** Centre block Y. */
    protected int centerY;
    /** Centre block Z. */
    protected int centerZ;
    /** Radius on X. */
    protected int radiusX;
    /** Radius on Y. */
    protected int radiusY;
    /** Radius on Z. */
    protected int radiusZ;
    /**
     * Creates an ellipsoid around a location.
     *
     * @param center  the centre; its block coordinates are used
     * @param radiusX radius on X
     * @param radiusY radius on Y
     * @param radiusZ radius on Z
     */
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
    /**
     * Creates an ellipsoid from coordinates.
     *
     * @param worldName the world name
     * @param centerX   centre X
     * @param centerY   centre Y
     * @param centerZ   centre Z
     * @param radiusX   radius on X
     * @param radiusY   radius on Y
     * @param radiusZ   radius on Z
     */
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
    /**
     * Copies an ellipsoid.
     *
     * @param other the ellipsoid to copy
     */
    public Ellipsoid(Ellipsoid other) {
        this(other.worldName, other.centerX, other.centerY, other.centerZ, other.radiusX, other.radiusY, other.radiusZ);
    }
    /**
     * Returns the world, looked up by name.
     *
     * @return the world, or {@code null} if it is not loaded
     */
    public World getWorld() {
        return Bukkit.getWorld(this.worldName);
    }
    /**
     * Returns the centre point.
     *
     * @return the centre
     */
    public Location getCenter() {
        return new Location(getWorld(), centerX + 0.5, centerY + 0.5, centerZ + 0.5);
    }
    /**
     * Returns the geometric volume.
     *
     * @return the volume in blocks
     */
    public double getVolume() {
        return (4.0 / 3.0) * Math.PI * radiusX * radiusY * radiusZ;
    }
    /**
     * Returns whether a block position is inside, in this shape's world.
     *
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return {@code true} if inside
     */
    public boolean contains(int x, int y, int z) {
        double dx = (double) (x - centerX) / radiusX;
        double dy = (double) (y - centerY) / radiusY;
        double dz = (double) (z - centerZ) / radiusZ;
        return (dx * dx + dy * dy + dz * dz) <= 1.0;
    }
    /**
     * Returns whether a location is inside and in the same world.
     *
     * @param location the location, may be {@code null}
     * @return {@code true} if inside
     */
    public boolean contains(Location location) {
        if (location == null || worldName == null) return false;
        World world = location.getWorld();
        return world != null && worldName.equals(location.getWorld().getName()) && contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    /**
     * Returns whether a block is inside.
     *
     * @param block the block
     * @return {@code true} if inside
     */
    public boolean contains(Block block) {
        return contains(block.getLocation());
    }
    /**
     * Returns whether a player stands inside.
     *
     * @param player the player
     * @return {@code true} if inside
     */
    public boolean contains(Player player) {
        return contains(player.getLocation());
    }
    /**
     * Returns whether an entity is inside.
     *
     * @param entity the entity
     * @return {@code true} if inside
     */
    public boolean contains(Entity entity) {
        return entity != null && contains(entity.getLocation());
    }
    /**
     * Returns the online players inside.
     *
     * @return the players
     */
    public Set<Player> getPlayers() {
        Set<Player> players = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (contains(player)) players.add(player);
        }
        return players;
    }
    /**
     * Returns the entities inside, from the chunks it overlaps.
     *
     * @return the entities
     */
    public List<Entity> getEntities() {
        List<Entity> entities = new ArrayList<>();
        World world = getWorld();
        if (world == null) return entities;
        for (Entity entity : world.getEntities()) {
            if (contains(entity.getLocation())) entities.add(entity);
        }
        return entities;
    }
    /**
     * Returns the living entities inside.
     *
     * @return the living entities
     */
    public List<LivingEntity> getLivingEntities() {
        List<LivingEntity> entities = new ArrayList<>();
        World world = getWorld();
        if (world == null) return entities;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (contains(entity.getLocation())) entities.add(entity);
        }
        return entities;
    }
    /**
     * Returns the living entities inside of the given types.
     *
     * @param types the types to keep
     * @return the matching entities
     */
    public List<LivingEntity> getLivingEntities(EntityType... types) {
        Set<EntityType> filter = EnumSet.noneOf(EntityType.class);
        Collections.addAll(filter, types);
        List<LivingEntity> entities = new ArrayList<>();
        for (LivingEntity entity : getLivingEntities()) {
            if (filter.contains(entity.getType())) entities.add(entity);
        }
        return entities;
    }
    /**
     * Kills the living entities inside that match {@code filter} by setting their health to 0.
     *
     * @param filter selects the entities to kill
     * @return how many were killed
     */
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
    /**
     * Kills every living entity inside except players.
     *
     * @return how many were killed
     */
    public int killAll() {
        return kill(entity -> !(entity instanceof Player));
    }
    /**
     * Kills the living entities inside of the given types.
     *
     * @param types the types to kill
     * @return how many were killed
     */
    public int killOnly(EntityType... types) {
        Set<EntityType> filter = EnumSet.noneOf(EntityType.class);
        Collections.addAll(filter, types);
        return kill(entity -> filter.contains(entity.getType()));
    }
    /**
     * Kills every living entity inside except players and the given types.
     *
     * @param types the types to spare
     * @return how many were killed
     */
    public int killAllExcept(EntityType... types) {
        Set<EntityType> filter = EnumSet.noneOf(EntityType.class);
        Collections.addAll(filter, types);
        return kill(entity -> !(entity instanceof Player) && !filter.contains(entity.getType()));
    }
    /**
     * Returns every block inside.
     *
     * @return the blocks
     */
    public List<Block> getBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : this) blocks.add(block);
        return blocks;
    }
    /**
     * Returns the blocks on the surface.
     *
     * @return the shell blocks
     */
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
    /**
     * Returns the chunks this shape overlaps, loading them if needed.
     *
     * @return the chunks
     */
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
    /**
     * Returns whether every block inside is of one material.
     *
     * @param material the material
     * @return {@code true} if all blocks match
     */
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
    /**
     * Iterates over the block locations inside.
     *
     * @return a new iterator
     */
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

    /**
     * Copies this ellipsoid.
     *
     * @return the copy
     */
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