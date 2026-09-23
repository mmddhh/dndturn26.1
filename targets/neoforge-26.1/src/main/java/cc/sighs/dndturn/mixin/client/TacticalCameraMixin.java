package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class TacticalCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);
    @Shadow private boolean detached;
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void dndturn$pose(float partialTick, CallbackInfo ci) {
        if (!ClientControl.camera((Camera)(Object)this)) return;
        ClientControl.frame();
        setPosition(ClientControl.position());
        setRotation(ClientControl.yaw(), ClientControl.pitch(), 0);
        detached = true;
    }
}
