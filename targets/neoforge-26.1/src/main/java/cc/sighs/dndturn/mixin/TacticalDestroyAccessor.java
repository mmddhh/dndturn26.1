package cc.sighs.dndturn.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerGameMode.class)
public interface TacticalDestroyAccessor {
    @Accessor("isDestroyingBlock") boolean dndturn$destroying();
    @Accessor("destroyPos") BlockPos dndturn$destroyPos();
    @Accessor("gameTicks") int dndturn$gameTicks();
    @Accessor("destroyProgressStart") int dndturn$destroyStart();
    @Accessor("hasDelayedDestroy") void dndturn$clearDelayed(boolean value);
}
