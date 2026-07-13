package org.glstudio.nexus.utils.cuboid;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class CylinderBlockIterator implements Iterator<Block> {

    private final World world;
    private final int centerX;
    private final int centerZ;
    private final int y1;
    private final int y2;
    private final int radiusSq;

    private int x;
    private int y;
    private int z;
    private int minX;
    private int maxX;
    private int minZ;
    private int maxZ;
    private Block next;

    public CylinderBlockIterator(World world, int centerX, int centerZ, int y1, int y2, int radius) {
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.y1 = y1;
        this.y2 = y2;
        this.radiusSq = radius * radius;

        this.minX = centerX - radius;
        this.maxX = centerX + radius;
        this.minZ = centerZ - radius;
        this.maxZ = centerZ + radius;

        this.x = minX;
        this.y = y1;
        this.z = minZ;

        scanForNext();
    }

    private void scanForNext() {
        while (y <= y2) {
            while (x <= maxX) {
                while (z <= maxZ) {
                    int dx = x - centerX;
                    int dz = z - centerZ;
                    if (dx * dx + dz * dz <= radiusSq) {
                        next = world.getBlockAt(x, y, z);
                        z++;
                        return;
                    }
                    z++;
                }
                z = minZ;
                x++;
            }
            z = minZ;
            x = minX;
            y++;
        }
        next = null;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Block next() {
        if (next == null) throw new NoSuchElementException();
        Block current = next;
        scanForNext();
        return current;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}