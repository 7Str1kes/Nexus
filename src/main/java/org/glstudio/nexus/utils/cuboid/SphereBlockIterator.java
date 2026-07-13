package org.glstudio.nexus.utils.cuboid;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class SphereBlockIterator implements Iterator<Block> {

    private final World world;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int radiusSq;

    private int x;
    private int y;
    private int z;
    private int min;
    private int max;
    private Block next;

    public SphereBlockIterator(World world, int centerX, int centerY, int centerZ, int radius) {
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radiusSq = radius * radius;

        this.min = centerX - radius;
        this.max = centerX + radius;

        this.x = min;
        this.y = centerY - radius;
        this.z = centerZ - radius;

        scanForNext();
    }

    private void scanForNext() {
        int yMax = centerY + radiusSq;
        int zMin = centerZ - radiusSq;
        int zMax = centerZ + radiusSq;

        while (y <= yMax) {
            while (x <= max) {
                while (z <= zMax) {
                    int dx = x - centerX;
                    int dy = y - centerY;
                    int dz = z - centerZ;
                    if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                        next = world.getBlockAt(x, y, z);
                        z++;
                        return;
                    }
                    z++;
                }
                z = zMin;
                x++;
            }
            z = zMin;
            x = min;
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