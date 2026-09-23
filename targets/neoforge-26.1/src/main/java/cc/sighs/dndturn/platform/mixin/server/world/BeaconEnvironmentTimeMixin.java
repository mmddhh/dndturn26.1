package cc.sighs.dndturn.platform.mixin.server.world;

import cc.sighs.dndturn.platform.server.world.EnvironmentProcesses;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconEnvironmentTimeMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getGameTime()J"))
    private static long dndturn$period(Level receiver, Level level, BlockPos pos, BlockState state, BeaconBlockEntity beacon) {
        return EnvironmentProcesses.time(level, pos);
    }
}
