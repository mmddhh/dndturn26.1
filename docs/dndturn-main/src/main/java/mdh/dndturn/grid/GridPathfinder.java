package mdh.dndturn.grid;

import mdh.dndturn.core.Grid;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GridPathfinder {

    public static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private GridPathfinder() {
    }

    public static BlockPos findStandable(Level level, Entity entity, int x, int z, int aroundY) {
        for (int y = aroundY + 2; y >= aroundY - 4; y--) {
            if (isWaterSurface(level, x, y, z) && isFreeSpace(level, entity, x, y, z)
                    && !isHazard(level, x, y, z)) {
                return new BlockPos(x, y, z);
            }
        }
        for (int y = aroundY + 2; y >= aroundY - 4; y--) {
            if (isStandable(level, entity, x, y, z)) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    public static boolean isStandable(Level level, Entity entity, int x, int y, int z) {
        BlockPos floor = new BlockPos(x, y - 1, z);
        VoxelShape floorShape = level.getBlockState(floor).getCollisionShape(level, floor);
        if (floorShape.isEmpty()) {
            return false;
        }
        return isFreeSpace(level, entity, x, y, z) && !isHazard(level, x, y, z);
    }

    private static boolean isFreeSpace(Level level, Entity entity, int x, int y, int z) {
        AABB box = entity.getDimensions(Pose.STANDING).makeBoundingBox(x + 0.5D, y, z + 0.5D);
        return level.noCollision(entity, box);
    }

    private static boolean isWaterSurface(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER)) {
            return false;
        }
        return !level.getFluidState(pos.above()).is(FluidTags.WATER);
    }

    private static boolean isHazard(Level level, int x, int y, int z) {
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                return true;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                return true;
            }
        }
        return false;
    }

    public static Result compute(Level level, LivingEntity entity, BlockPos start, int maxCost,
                                 Grid grid, Set<BlockPos> occupied) {
        BlockPos standStart = findStandable(level, entity, start.getX(), start.getZ(), start.getY());
        if (standStart == null) {
            standStart = start;
        }
        Result result = new Result(standStart);
        result.dist.put(standStart, 0);

        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(standStart);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int currentCost = result.dist.get(current);
            if (currentCost >= maxCost) {
                continue;
            }
            for (int[] dir : DIRECTIONS) {
                int nx = current.getX() + dir[0];
                int nz = current.getZ() + dir[1];
                if (!grid.contains(nx, nz)) {
                    continue;
                }
                BlockPos stand = findStandable(level, entity, nx, nz, current.getY());
                if (stand == null) {
                    continue;
                }
                if (Math.abs(stand.getY() - current.getY()) > 1) {
                    continue;
                }
                if (occupied.contains(stand) || occupied.contains(stand.above())) {
                    continue;
                }
                if (result.dist.containsKey(stand)) {
                    continue;
                }
                result.dist.put(stand, currentCost + 1);
                result.parent.put(stand, current);
                queue.add(stand);
            }
        }
        return result;
    }

    public static class Result {

        private final BlockPos start;
        private final Map<BlockPos, Integer> dist = new HashMap<>();
        private final Map<BlockPos, BlockPos> parent = new HashMap<>();

        Result(BlockPos start) {
            this.start = start;
        }

        public Map<BlockPos, Integer> distances() {
            return dist;
        }

        public Set<BlockPos> cells() {
            return new HashSet<>(dist.keySet());
        }

        public boolean contains(BlockPos pos) {
            return dist.containsKey(pos);
        }

        public int cost(BlockPos pos) {
            return dist.getOrDefault(pos, Integer.MAX_VALUE);
        }

        public List<BlockPos> pathTo(BlockPos target) {
            List<BlockPos> path = new LinkedList<>();
            BlockPos current = target;
            while (current != null && !current.equals(start)) {
                path.add(0, current);
                current = parent.get(current);
                if (path.size() > 4096) {
                    break;
                }
            }
            return new ArrayList<>(path);
        }
    }
}
