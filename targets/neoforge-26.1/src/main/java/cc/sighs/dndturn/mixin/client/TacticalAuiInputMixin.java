package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MouseEvent.class, remap = false)
public abstract class TacticalAuiInputMixin {
    @Inject(method = "tiggerEvent(Lcom/sighs/apricityui/event/MouseEvent;)Z",
        at = @At("HEAD"), cancellable = true)
    private static void dndturn$overlayRoute(MouseEvent event, CallbackInfoReturnable<Boolean> ci) {
        if (!ClientControl.routeAui()) return;
        Document document = ClientControl.mouseDocument();
        ci.setReturnValue(document != null && MouseEvent.tiggerEvent(event, document));
    }
    @Inject(method = "tiggerEvent(Lcom/sighs/apricityui/event/MouseEvent;Lcom/sighs/apricityui/init/Document;)Z",
        at = @At("HEAD"), cancellable = true)
    private static void dndturn$route(MouseEvent event, Document document, CallbackInfoReturnable<Boolean> ci) {
        if (!ClientControl.allowDocument(document)) ci.setReturnValue(false);
    }
}
