package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class TacticalMouseMixin implements cc.sighs.dndturn.client.MouseInputReset {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void dndturn$beforeButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        ClientControl.reconcile();
    }
    @Shadow private boolean isLeftPressed;
    @Shadow private boolean isRightPressed;
    @Shadow private boolean isMiddlePressed;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private MouseButtonInfo activeButton;
    @Shadow private boolean ignoreFirstMove;
    @Shadow private double mousePressedTime;
    @Override public void dndturn$resetInput() {
        isLeftPressed = isRightPressed = isMiddlePressed = false;
        accumulatedDX = accumulatedDY = mousePressedTime = 0;
        activeButton = null; ignoreFirstMove = true;
    }
    @Redirect(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;onMouseButtonPre(Lnet/minecraft/client/input/MouseButtonInfo;I)Z"))
    private boolean dndturn$button(MouseButtonInfo info, int action) {
        ClientControl.beginMouse(info.button(), action);
        boolean consumed = false;
        try { consumed = ClientHooks.onMouseButtonPre(info, action); }
        finally { consumed = ClientControl.endMouse(info.button(), action, consumed); }
        return consumed;
    }
    @Redirect(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;onMouseScroll(Lnet/minecraft/client/MouseHandler;DD)Z"))
    private boolean dndturn$scroll(MouseHandler mouse, double x, double y) {
        return ClientControl.scroll(y, ClientHooks.onMouseScroll(mouse, x, y));
    }
    @Inject(method = "onMove", at = @At("HEAD"))
    private void dndturn$move(long window, double x, double y, CallbackInfo ci) {
        if (window == net.minecraft.client.Minecraft.getInstance().getWindow().handle()) ClientControl.mouseMove(x, y);
    }
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void dndturn$turn(double time, CallbackInfo ci) { if (ClientControl.blockTurn()) ci.cancel(); }
}
