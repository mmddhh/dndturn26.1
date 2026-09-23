package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.EnvironmentProcesses;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.entity.DaylightDetectorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the sampling cadence is local. .84 sky brightness and sun angle remain live world facts. */
@Mixin(DaylightDetectorBlock.class)
public abstract class DaylightEnvironmentTimeMixin {
    @Redirect(method = "tickEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getGameTime()J"))
    private static long dndturn$cadence(Level receiver, Level level, BlockPos pos, BlockState state,
                                       DaylightDetectorBlockEntity detector) {
        return EnvironmentProcesses.time(level, pos);
    }
}
