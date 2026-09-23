package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.MinecraftCombatRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Skip sampled block/fluid callbacks at exact paused positions while other positions retain vanilla sampling. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelRandomTickGateMixin {
    @Redirect(method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void dndturn$regionalRandomBlockFreeze(BlockState state, ServerLevel level,
                                                    BlockPos pos, RandomSource random) {
        if (!MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos)) state.randomTick(level, pos, random);
    }

    @Redirect(method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void dndturn$regionalRandomFluidFreeze(FluidState state, ServerLevel level,
                                                    BlockPos pos, RandomSource random) {
        if (!MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos)) state.randomTick(level, pos, random);
    }
}
