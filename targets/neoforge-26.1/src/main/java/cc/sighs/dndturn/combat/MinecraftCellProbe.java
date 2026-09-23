package cc.sighs.dndturn.combat;

import cc.sighs.dndturn.combat.CombatEngine.Bounds;
import cc.sighs.dndturn.combat.TacticalPlanner.CellProbe;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Reads Minecraft collision and fluid state; the planner only receives value results. */
public final class MinecraftCellProbe implements CellProbe {
    private final ServerLevel level;
    private final Entity mover;
    private final Bounds bounds;
    private final EncounterRegion region;

    @Deprecated
    public MinecraftCellProbe(ServerLevel level, Entity mover, Bounds bounds) {
        this.level = Objects.requireNonNull(level);
        this.mover = Objects.requireNonNull(mover);
        this.bounds = Objects.requireNonNull(bounds);
        this.region = null;
    }

    public MinecraftCellProbe(ServerLevel level, Entity mover, EncounterRegion region) {
        this.level = Objects.requireNonNull(level);
        this.mover = Objects.requireNonNull(mover);
        this.bounds = null;
        this.region = Objects.requireNonNull(region);
    }

    @Override
    public boolean canOccupy(GridCell cell) {
        checkServerThread();
        if (cell.y() == Integer.MIN_VALUE) return false;
        BlockPos floor = new BlockPos(cell.x(), cell.y() - 1, cell.z());
        AABB footprint = footprint(cell);
        if (region != null) {
            if (!region.dimension().equals(level.dimension().identifier().toString())) return false;
            var center = footprint.getCenter();
            if (!region.containsPoint(center.x, center.y, center.z)) return false;
        } else if (!bounds.dimension().equals(level.dimension().toString()) || !bounds.contains(cell)) return false;
        if (!isLoaded(footprint) || !level.hasChunkAt(floor)) return false;
        AABB supportProbe = new AABB(footprint.minX, footprint.minY - 0.01, footprint.minZ,
            footprint.maxX, footprint.minY, footprint.maxZ);
        if (level.noCollision(mover, supportProbe)
            && !level.getFluidState(new BlockPos(cell.x(), cell.y(), cell.z())).is(FluidTags.WATER)) return false;
        return level.noCollision(mover, footprint);
    }

    @Override
    public int traversalCost(GridCell from, GridCell to) {
        checkServerThread();
        if (from.chebyshev(to) != 1) return 0;
        boolean jump = to.y() > from.y() && to.y() - from.y() > mover.maxUpStep();
        if (jump && (!(mover instanceof net.minecraft.world.entity.player.Player) || to.y() - from.y() != 1)) return 0;
        AABB source = footprint(from);
        AABB destination = footprint(to);
        double dx = destination.minX - source.minX;
        double dy = destination.minY - source.minY;
        double dz = destination.minZ - source.minZ;
        AABB first = dy > 0 ? source.expandTowards(0, dy, 0) : source.expandTowards(dx, 0, dz);
        AABB second = dy > 0 ? source.move(0, dy, 0).expandTowards(dx, 0, dz)
            : source.move(dx, 0, dz).expandTowards(0, dy, 0);
        if (jump) {
            // Conservative full-body envelope includes the apex of the ordinary one-block jump.
            AABB arc = source.expandTowards(dx, 1.3, dz);
            AABB headroom = new AABB(arc.minX, source.maxY, arc.minZ, arc.maxX, arc.maxY, arc.maxZ);
            if (!isLoaded(headroom) || !level.noCollision(mover, headroom)) return 0;
        }
        if (!isLoaded(first) || !isLoaded(second) || !level.noCollision(mover, first)
            || !level.noCollision(mover, second) || !canOccupy(to)) return 0;
        if (from.x() != to.x() && from.z() != to.z()
            && (!canOccupy(new GridCell(to.x(), from.y(), from.z()))
                || !canOccupy(new GridCell(from.x(), from.y(), to.z())))) return 0;
        return 1;
    }

    private AABB footprint(GridCell cell) {
        EntityDimensions dimensions = mover.getDimensions(mover.getPose());
        return dimensions.makeBoundingBox(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
    }

    private void checkServerThread() {
        if (!level.getServer().isSameThread() || mover.level() != level)
            throw new IllegalStateException("live collision probe requires mover on server thread");
    }

    private boolean isLoaded(AABB box) {
        int minChunkX = Math.floorDiv((int) Math.floor(box.minX), 16);
        int maxChunkX = Math.floorDiv((int) Math.floor(Math.nextDown(box.maxX)), 16);
        int minChunkZ = Math.floorDiv((int) Math.floor(box.minZ), 16);
        int maxChunkZ = Math.floorDiv((int) Math.floor(Math.nextDown(box.maxZ)), 16);
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!level.hasChunkAt(new BlockPos(x << 4, 0, z << 4))) return false;
            }
        }
        return true;
    }
}
