package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.spatial.GridCell;
import net.minecraft.core.BlockPos;

/** Explicit block-address conversion; no world access or movement. */
public final class MinecraftCoordinates {
    private MinecraftCoordinates() {}
    public static GridCell cell(BlockPos pos) { return new GridCell(pos.getX(), pos.getY(), pos.getZ()); }
    public static BlockPos pos(GridCell cell) { return new BlockPos(cell.x(), cell.y(), cell.z()); }
}
