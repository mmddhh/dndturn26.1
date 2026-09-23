package mdh.dndturn.core;

import net.minecraft.core.BlockPos;

public class Grid {

    private final int floorY;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    public Grid(int floorY, int minX, int maxX, int minZ, int maxZ) {
        this.floorY = floorY;
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static Grid around(BlockPos center, int radius, int floorY) {
        return new Grid(floorY,
                center.getX() - radius, center.getX() + radius,
                center.getZ() - radius, center.getZ() + radius);
    }

    public int getFloorY() {
        return floorY;
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getZ());
    }

    public BlockPos center() {
        return new BlockPos((minX + maxX) / 2, floorY, (minZ + maxZ) / 2);
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }
}
