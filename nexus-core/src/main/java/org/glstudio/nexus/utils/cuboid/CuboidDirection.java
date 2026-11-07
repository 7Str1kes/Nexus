package org.glstudio.nexus.utils.cuboid;

import org.bukkit.block.BlockFace;

/**
 * A face or group of faces of a {@link Cuboid}. In this library NORTH and SOUTH are the X axis and
 * EAST and WEST the Z axis.
 */
public enum CuboidDirection {
    /** Towards lower X. */
    NORTH,
    /** Towards lower Z. */
    EAST,
    /** Towards higher X. */
    SOUTH,
    /** Towards higher Z. */
    WEST,
    /** Towards higher Y. */
    UP,
    /** Towards lower Y. */
    DOWN,
    /** North, east, south and west together. */
    HORIZONTAL,
    /** Up and down together. */
    VERTICAL,
    /** Every face. */
    BOTH,
    /** No direction. */
    UNKNOWN;
    /**
     * Returns the opposite direction.
     *
     * @return the opposite; groups and {@code UNKNOWN} return themselves or their counterpart
     */
    public CuboidDirection opposite() {
        switch (this) {
            case NORTH: {
                return CuboidDirection.SOUTH;
            }
            case EAST: {
                return CuboidDirection.WEST;
            }
            case SOUTH: {
                return CuboidDirection.NORTH;
            }
            case WEST: {
                return CuboidDirection.EAST;
            }
            case HORIZONTAL: {
                return CuboidDirection.VERTICAL;
            }
            case VERTICAL: {
                return CuboidDirection.HORIZONTAL;
            }
            case UP: {
                return CuboidDirection.DOWN;
            }
            case DOWN: {
                return CuboidDirection.UP;
            }
            case BOTH: {
                return CuboidDirection.BOTH;
            }
            default: {
                return CuboidDirection.UNKNOWN;
            }
        }
    }
    /**
     * Converts to a Bukkit block face.
     *
     * @return the face, or {@code null} for groups
     */
    public BlockFace toBukkitDirection() {
        switch (this) {
            case NORTH: {
                return BlockFace.NORTH;
            }
            case EAST: {
                return BlockFace.EAST;
            }
            case SOUTH: {
                return BlockFace.SOUTH;
            }
            case WEST: {
                return BlockFace.WEST;
            }
            case UP: {
                return BlockFace.UP;
            }
            case DOWN: {
                return BlockFace.DOWN;
            }
            default: {
                return null;
            }
        }
    }
}