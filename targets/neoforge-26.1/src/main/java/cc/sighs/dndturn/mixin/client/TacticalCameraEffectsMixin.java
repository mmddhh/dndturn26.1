package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class TacticalCameraEffectsMixin {
    @Inject(method = {"renderItemInHand", "bobView", "bobHurt"}, at = @At("HEAD"), cancellable = true)
    private void dndturn$detachedEffects(CallbackInfo ci) {
        if (ClientControl.camera(Minecraft.getInstance().gameRenderer.getMainCamera())) ci.cancel();
    }
}
