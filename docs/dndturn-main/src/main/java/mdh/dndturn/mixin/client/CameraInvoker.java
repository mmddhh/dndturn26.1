package mdh.dndturn.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {

    @Invoker("setPosition")
    void dndturn$setPosition(double x, double y, double z);

    @Invoker("setRotation")
    void dndturn$setRotation(float yaw, float pitch);
}
