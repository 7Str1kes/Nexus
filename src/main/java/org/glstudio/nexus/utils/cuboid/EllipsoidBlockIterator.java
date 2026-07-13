package org.glstudio.nexus.utils.cuboid;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class EllipsoidBlockIterator implements Iterator<Block> {

    private final World world;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int radiusX;
    private final int radiusY;
    private final int radiusZ;

    private int x;
    private int y;
    private int z;
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private int minZ;
    private int maxZ;
    private Block next;

    public EllipsoidBlockIterator(World world, int centerX, int centerY, int centerZ, int radiusX, int radiusY, int radiusZ) {
        this.world = world;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;

        this.minX = centerX - radiusX;
        this.maxX = centerX + radiusX;
        this.minY = centerY - radiusY;
        this.maxY = centerY + radiusY;
        this.minZ = centerZ - radiusZ;
        this.maxZ = centerZ + radiusZ;

        this.x = minX;
        this.y = minY;
        this.z = minZ;

        scanForNext();
    }

    private void scanForNext() {
        while (y <= maxY) {
            while (x <= maxX) {
                while (z <= maxZ) {
                    double dx = (double) (x - centerX) / radiusX;
                    double dy = (double) (y - centerY) / radiusY;
                    double dz = (double) (z - centerZ) / radiusZ;
                    if (dx * dx + dy * dy + dz * dz <= 1.0) {
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