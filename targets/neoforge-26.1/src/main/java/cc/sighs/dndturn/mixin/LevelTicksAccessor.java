package cc.sighs.dndturn.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.Queue;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelTicks.class)
public interface LevelTicksAccessor<T> {
    @Accessor("allContainers")
    Long2ObjectMap<LevelChunkTicks<T>> dndturn$containers();

    @Accessor("toRunThisTick")
    Queue<ScheduledTick<T>> dndturn$toRunThisTick();

    @Accessor("alreadyRunThisTick")
    List<ScheduledTick<T>> dndturn$alreadyRunThisTick();
}
