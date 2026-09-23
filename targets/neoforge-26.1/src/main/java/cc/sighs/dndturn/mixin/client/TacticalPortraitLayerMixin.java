package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ParticipantPortraits;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.client.gui.pip.ApricityUiPipRenderState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ApricityGuiLayers.class, remap = false)
public abstract class TacticalPortraitLayerMixin {
    @Inject(method = "submitUi(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lcom/sighs/apricityui/client/gui/pip/ApricityUiPipRenderState$FloatingItemBatch;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;submitPictureInPictureRenderState(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;)V", shift = At.Shift.AFTER))
    private static void dndturn$portraits(GuiGraphicsExtractor graphics,
                                        ApricityUiPipRenderState.FloatingItemBatch items, CallbackInfo ci) {
        ParticipantPortraits.extract(graphics);
    }
}
