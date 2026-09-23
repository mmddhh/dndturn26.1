package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.MinecraftCombatRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep vanilla ticker registration, onLoad, and removed-ticker cleanup active. */
@Mixin(Level.class)
public abstract class LevelBlockEntityGateMixin {
    @Redirect(method = "tickBlockEntities()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean dndturn$regionalBlockEntityFreeze(Level level, BlockPos pos) {
        return level.shouldTickBlocksAt(pos)
            && (!(level instanceof ServerLevel serverLevel)
                || !MinecraftCombatRuntime.isFormalBlockSimulationPaused(serverLevel, pos));
    }
}
