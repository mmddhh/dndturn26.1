package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A filtered document must not stop AUI's outer traversal with an intercept hit. */
@Mixin(value = Document.class, remap = false)
public abstract class TacticalAuiDocumentMixin {
    @Inject(method = "interceptsMouseEventsAt", at = @At("HEAD"), cancellable = true)
    private void dndturn$intercept(Position position, CallbackInfoReturnable<Boolean> ci) {
        if (!ClientControl.allowDocument((Document)(Object)this)) ci.setReturnValue(false);
    }
}
