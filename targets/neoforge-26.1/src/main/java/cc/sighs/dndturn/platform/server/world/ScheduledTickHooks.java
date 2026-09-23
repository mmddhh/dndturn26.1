package cc.sighs.dndturn.platform.server.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

/** Queue query/mutation seam, including held entries; RegionalScheduledTicks remains their sole owner. */
public final class ScheduledTickHooks {
    private ScheduledTickHooks() {}
    public static <T> boolean holdScheduled(LevelTicks<T> queue, ScheduledTick<T> tick) { return RegionalScheduledTicks.holdScheduled(queue, tick); }
    public static boolean isHeld(LevelTicks<?> queue, BlockPos pos, Object type) { return RegionalScheduledTicks.isHeld(queue, pos, type); }
    public static void clearHeld(LevelTicks<?> queue, BoundingBox area) { RegionalScheduledTicks.clearHeld(queue, area); }
    public static <T> boolean copyAreaSnapshot(LevelTicks<T> destination, LevelTicks<T> source, BoundingBox area, Vec3i offset) { return RegionalScheduledTicks.copyAreaSnapshot(destination, source, area, offset); }
    public static int heldCount(LevelTicks<?> queue) { return RegionalScheduledTicks.heldCount(queue); }
}
