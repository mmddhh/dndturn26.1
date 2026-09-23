package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.TacticalOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Base.class, remap = false)
public abstract class TacticalAuiRenderMixin {
    @Inject(method = "drawOverlayDocument", at = @At("HEAD"), cancellable = true)
    private static void dndturn$hudVisibility(PoseStack pose, Document document, CallbackInfo ci) {
        if (!TacticalOverlay.allowOverlayRender(document)) ci.cancel();
    }
}
