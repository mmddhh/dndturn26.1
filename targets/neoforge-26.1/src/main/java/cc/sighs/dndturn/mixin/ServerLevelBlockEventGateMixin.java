package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.MinecraftCombatRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Vanilla runBlockEvents reschedules entries when this positional gate returns false. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockEventGateMixin {
    @Redirect(method = "runBlockEvents()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean dndturn$regionalBlockEventFreeze(ServerLevel level, BlockPos pos) {
        return level.shouldTickBlocksAt(pos)
            && !MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos);
    }
}
