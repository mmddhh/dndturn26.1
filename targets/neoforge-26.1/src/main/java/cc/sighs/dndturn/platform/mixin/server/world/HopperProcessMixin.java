package cc.sighs.dndturn.platform.mixin.server.world;

import cc.sighs.dndturn.platform.server.control.SimulationPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
import cc.sighs.dndturn.platform.server.world.EnvironmentProcesses;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Contact suction and ticker transfer share the environment process; manual inventory access does not. */
@Mixin(HopperBlockEntity.class)
public abstract class HopperProcessMixin {
    @Inject(method = "ejectItems", at = @At("HEAD"), cancellable = true)
    private static void dndturn$destination(Level level, BlockPos pos, HopperBlockEntity hopper,
                                           CallbackInfoReturnable<Boolean> ci) {
        if (level instanceof ServerLevel server && !SimulationPolicy.process(EnvironmentProcesses.Policy.ENVIRONMENT, AuthorityProjection.block(server, pos.relative(hopper.getBlockState().getValue(HopperBlock.FACING)))).allowed())
            ci.setReturnValue(false);
    }

    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void dndturn$source(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> ci) {
        if (hopper instanceof HopperBlockEntity && level instanceof ServerLevel server && (!SimulationPolicy.process(EnvironmentProcesses.Policy.ENVIRONMENT, AuthorityProjection.block(server, BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ()))).allowed()
            || !SimulationPolicy.process(EnvironmentProcesses.Policy.ENVIRONMENT, AuthorityProjection.block(server, BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1, hopper.getLevelZ()))).allowed()))
            ci.setReturnValue(false);
    }

    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
        at = @At("HEAD"), cancellable = true)
    private static void dndturn$currentItem(Container container, ItemEntity item, CallbackInfoReturnable<Boolean> ci) {
        if (container instanceof HopperBlockEntity hopper && item.level() instanceof ServerLevel server
            && (!item.isAlive() || !SimulationPolicy.process(EnvironmentProcesses.Policy.ENVIRONMENT, AuthorityProjection.block(server, hopper.getBlockPos())).allowed()
                || !SimulationPolicy.process(EnvironmentProcesses.Policy.ENVIRONMENT, AuthorityProjection.block(server, item.blockPosition())).allowed()))
            ci.setReturnValue(false);
    }

    @Inject(method = "tryMoveItems", at = @At("HEAD"), cancellable = true)
    private static void dndturn$automaticTransfer(Level level, BlockPos pos, BlockState state,
        HopperBlockEntity hopper, BooleanSupplier action, CallbackInfoReturnable<Boolean> ci) {
        if (level instanceof ServerLevel serverLevel
            && !SimulationPolicy.process(EnvironmentProcesses.policy(false, EnvironmentProcesses.Channel.HOPPER_TRANSFER), AuthorityProjection.block(serverLevel, pos)).allowed())
            ci.setReturnValue(false);
    }
}
