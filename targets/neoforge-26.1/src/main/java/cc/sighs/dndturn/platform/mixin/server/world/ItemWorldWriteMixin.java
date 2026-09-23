package cc.sighs.dndturn.platform.mixin.server.world;

import cc.sighs.dndturn.platform.server.action.ItemWorldMutationScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fixed .84 synchronous write boundary; inactive outside an accepted item/block invocation. */
@Mixin(Level.class)
public abstract class ItemWorldWriteMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void dndturn$write(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> ci) {
        ItemWorldMutationScope.beforeBlockWrite((Level)(Object)this, pos);
    }
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"))
    private void dndturn$destroy(BlockPos pos, boolean drops, Entity breaker, int limit, CallbackInfoReturnable<Boolean> ci) {
        ItemWorldMutationScope.beforeBlockWrite((Level)(Object)this, pos);
    }
    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void dndturn$blockEntity(BlockEntity entity, CallbackInfo ci) {
        ItemWorldMutationScope.beforeBlockWrite((Level)(Object)this, entity.getBlockPos());
    }
    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void dndturn$removeBlockEntity(BlockPos pos, CallbackInfo ci) {
        ItemWorldMutationScope.beforeBlockWrite((Level)(Object)this, pos);
    }
}
