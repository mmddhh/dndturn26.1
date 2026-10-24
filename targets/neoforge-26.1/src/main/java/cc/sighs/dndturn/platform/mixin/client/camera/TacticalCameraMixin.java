package cc.sighs.dndturn.platform.mixin.client.camera;

import cc.sighs.dndturn.platform.client.input.ClientControl;
import cc.sighs.dndturn.platform.client.camera.TerrainCutaway;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class TacticalCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);
    @Shadow private boolean detached;
    @Inject(method = "getFluidInCamera", at = @At("RETURN"), cancellable = true)
    private void dndturn$cutawayFog(CallbackInfoReturnable<FogType> cir) {
        if (TerrainCutaway.hidden(BlockPos.containing(((Camera)(Object)this).position()))) cir.setReturnValue(FogType.NONE);
    }
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void dndturn$pose(float partialTick, CallbackInfo ci) {
        if (!ClientControl.camera((Camera)(Object)this)) return;
        ClientControl.frame();
        setPosition(ClientControl.position());
        setRotation(ClientControl.yaw(), ClientControl.pitch(), 0);
        detached = true;
    }
}
