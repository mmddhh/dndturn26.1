package cc.sighs.dndturn.platform.mixin.client.camera;

import cc.sighs.dndturn.platform.client.camera.CutawayStampAccess;
import cc.sighs.dndturn.platform.client.camera.TerrainCutaway;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the renderer's captured region sees air; ClientLevel and section copies stay intact. */
@Mixin(RenderSectionRegion.class)
public abstract class CutawayRegionMixin implements CutawayStampAccess {
    @Shadow @Final private int minSectionX;
    @Shadow @Final private int minSectionY;
    @Shadow @Final private int minSectionZ;
    @Unique private Stamp dndturn$stamp;
    @Override public Stamp dndturn$cutawayStamp() { return dndturn$stamp; }
    @Override public void dndturn$cutawayStamp(Stamp stamp) { dndturn$stamp = stamp; }
    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;III[Lnet/minecraft/client/renderer/chunk/SectionCopy;Lit/unimi/dsi/fastutil/longs/Long2ObjectFunction;)V", at = @At("RETURN"))
    private void dndturn$capture(CallbackInfo ci) {
        dndturn$stamp = TerrainCutaway.capture(minSectionX + 1, minSectionY + 1, minSectionZ + 1);
    }
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void dndturn$block(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (dndturn$stamp.mask().hides(pos)) cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void dndturn$fluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (dndturn$stamp.mask().hides(pos)) cir.setReturnValue(Blocks.AIR.defaultBlockState().getFluidState());
    }
    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void dndturn$blockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (dndturn$stamp.mask().hides(pos)) cir.setReturnValue(null);
    }
}
