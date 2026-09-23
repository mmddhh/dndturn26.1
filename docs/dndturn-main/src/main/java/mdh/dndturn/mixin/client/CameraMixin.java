package mdh.dndturn.mixin.client;

import mdh.dndturn.client.camera.TacticalCamera;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean dndturn$forceDetached(boolean detached) {
        return TacticalCamera.instance().shouldOverride() || detached;
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void dndturn$applyTacticalCamera(BlockGetter level, Entity entity, boolean detached,
                                             boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        TacticalCamera camera = TacticalCamera.instance();
        camera.updateBlend();
        float alpha = camera.getBlend();
        if (alpha <= 0.002F) {
            return;
        }

        Vec3 eye = entity.getEyePosition(partialTick);
        float vanillaYaw = entity.getViewYRot(partialTick);
        float vanillaPitch = entity.getViewXRot(partialTick);

        double x = Mth.lerp(alpha, eye.x, camera.getCameraX());
        double y = Mth.lerp(alpha, eye.y, camera.getCameraY());
        double z = Mth.lerp(alpha, eye.z, camera.getCameraZ());
        float yaw = vanillaYaw + Mth.wrapDegrees(camera.getYaw() - vanillaYaw) * alpha;
        float pitch = Mth.lerp(alpha, vanillaPitch, camera.getPitch());

        CameraInvoker invoker = (CameraInvoker) (Object) this;
        invoker.dndturn$setPosition(x, y, z);
        invoker.dndturn$setRotation(yaw, pitch);
    }
}
