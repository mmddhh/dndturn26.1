package cc.sighs.dndturn.platform.mixin.client.ui;

import cc.sighs.dndturn.platform.client.ui.OverlayUi;
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
        if (!OverlayUi.prepareForDraw(document)) ci.cancel();
    }
}
