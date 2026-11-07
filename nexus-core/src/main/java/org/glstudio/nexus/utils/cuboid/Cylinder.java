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
 * A vertical cylinder of blocks, iterable block by block.
 */
@Getter
public class Cylinder implements Iterable<Block>, Cloneable {

    /** Name of the world. */
    protected String worldName;
    /** Centre block X. */
    protected int centerX;
    /** Centre block Z. */
    protected int centerZ;
    /** Lowest block Y. */
    protected int y1;
    /** Highest block Y. */
    protected int y2;
    /** Radius in blocks. */
    protected int radius;
    /**
     * Creates a cylinder rising from a location.
     *
     * @param center the centre of the base
     * @param radius the radius in blocks
     * @param height the height in blocks
     */
    public Cylinder(Location center, int radius, int height) {
        Preconditions.checkNotNull(center, "Center location cannot be null");
        Preconditions.checkArgument(radius > 0, "Radius must be positive");
        Preconditions.checkArgument(height > 0, "Height must be positive");

        this.worldName = center.getWorld().getName();
        this.centerX = center.getBlockX();
        this.centerZ = center.getBlockZ();
        this.y1 = center.getBlockY();
        this.y2 = center.getBlockY() + height - 1;
        this.radius = radius;
    }
    /**
     * Creates a cylinder between two heights.
     *
     * @param center  the centre; only X and Z are used
     * @param radius  the radius in blocks
     * @param bottomY the lowest Y
     * @param topY    the highest Y
     */
    public Cylinder(Location center, int radius, int bottomY, int topY) {
        Preconditions.checkNotNull(center, "Center location cannot be null");
        Preconditions.checkArgument(radius > 0, "Radius must be positive");
        Preconditions.checkArgument(topY >= bottomY, "Top Y must be >= bottom Y");

        this.worldName = center.getWorld().getName();
        this.centerX = center.getBlockX();
        this.centerZ = center.getBlockZ();
        this.y1 = bottomY;
        this.y2 = topY;
        this.radius = radius;
    }
    /**
     * Creates a cylinder from coordinates.
     *
     * @param worldName the world name
     * @param centerX   centre X
     * @param centerZ   centre Z
     * @param y1        one end's Y
     * @param y2        the other end's Y
     * @param radius    the radius in blocks
     */
    public Cylinder(String worldName, int centerX, int centerZ, int y1, int y2, int radius) {
        Preconditions.checkNotNull(worldName, "World name cannot be null");
        Preconditions.checkArgument(radius > 0, "Radius must be positive");
        Preconditions.checkArgument(y2 >= y1, "Top Y must be >= bottom Y");

        this.worldName = worldName;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.y1 = y1;
        this.y2 = y2;
        this.radius = radius;
    }
    /**
     * Copies a cylinder.
     *
     * @param other the cylinder to copy
     */
    public Cylinder(Cylinder other) {
        this(other.worldName, other.centerX, other.centerZ, other.y1, other.y2, other.radius);
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
        return new Location(getWorld(), centerX + 0.5, (y1 + y2) / 2.0, centerZ + 0.5);
    }
    /**
     * Returns the height.
     *
     * @return the number of block layers
     */
    public int getHeight() {
        return y2 - y1 + 1;
    }
    /**
     * Returns the geometric volume.
     *
     * @return the volume in blocks
     */
    public double getVolume() {
        return Math.PI * radius * radius * getHeight();
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
        if (y < y1 || y > y2) return false;
        int dx = x - centerX;
        int dz = z - centerZ;
        return (dx * dx + dz * dz) <= radius * radius;
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
     * Returns the outer ring of blocks on every layer.
     *
     * @return the wall blocks
     */
    public List<Block> getWalls() {
        List<Block> blocks = new ArrayList<>();
        World world = getWorld();
        if (world == null) return blocks;

        int radiusSq = radius * radius;
        for (int y = y1; y <= y2; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    int dx = x - centerX;
                    int dz = z - centerZ;
                    int distSq = dx * dx + dz * dz;
                    if (distSq > radiusSq) continue;

                    boolean onSurface = false;
                    int nextDistSq = (dx + 1) * (dx + 1) + dz * dz;
                    if (nextDistSq > radiusSq) onSurface = true;

                    nextDistSq = (dx - 1) * (dx - 1) + dz * dz;
                    if (nextDistSq > radiusSq) onSurface = true;

                    nextDistSq = dx * dx + (dz + 1) * (dz + 1);
                    if (nextDistSq > radiusSq) onSurface = true;

                    nextDistSq = dx * dx + (dz - 1) * (dz - 1);
                    if (nextDistSq > radiusSq) onSurface = true;

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

        int minChunkX = (centerX - radius) >> 4;
        int maxChunkX = (centerX + radius) >> 4;
        int minChunkZ = (centerZ - radius) >> 4;
        int maxChunkZ = (centerZ + radius) >> 4;

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
        return new CylinderBlockIterator(getWorld(), centerX, centerZ, y1, y2, radius);
    }
    /**
     * Iterates over the block locations inside.
     *
     * @return a new iterator
     */
    public Iterator<Location> locationIterator() {
        World world = getWorld();
        return new Iterator<Location>() {
            private final Iterator<Block> blockIterator = new CylinderBlockIterator(world, centerX, centerZ, y1, y2, radius);

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
     * Copies this cylinder.
     *
     * @return the copy
     */
    @Override
    public Cylinder clone() {
        try {
            return (Cylinder) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException("This could never happen", ex);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cylinder)) return false;
        Cylinder cylinder = (Cylinder) o;
        return centerX == cylinder.centerX && centerZ == cylinder.centerZ && y1 == cylinder.y1 && y2 == cylinder.y2 && radius == cylinder.radius && worldName.equals(cylinder.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, centerX, centerZ, y1, y2, radius);
    }

    @Override
    public String toString() {
        return "Cylinder: " + worldName + ",center=" + centerX + "," + centerZ + ",y=" + y1 + "-" + y2 + ",r=" + radius;
    }
}