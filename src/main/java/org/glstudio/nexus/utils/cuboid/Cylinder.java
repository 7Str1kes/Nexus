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
public class Cylinder implements Iterable<Block>, Cloneable {

    protected String worldName;
    protected int centerX;
    protected int centerZ;
    protected int y1;
    protected int y2;
    protected int radius;

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

    public Cylinder(Cylinder other) {
        this(other.worldName, other.centerX, other.centerZ, other.y1, other.y2, other.radius);
    }

    public World getWorld() {
        return Bukkit.getWorld(this.worldName);
    }

    public Location getCenter() {
        return new Location(getWorld(), centerX + 0.5, (y1 + y2) / 2.0, centerZ + 0.5);
    }

    public int getHeight() {
        return y2 - y1 + 1;
    }

    public double getVolume() {
        return Math.PI * radius * radius * getHeight();
    }

    public boolean contains(int x, int y, int z) {
        if (y < y1 || y > y2) return false;
        int dx = x - centerX;
        int dz = z - centerZ;
        return (dx * dx + dz * dz) <= radius * radius;
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