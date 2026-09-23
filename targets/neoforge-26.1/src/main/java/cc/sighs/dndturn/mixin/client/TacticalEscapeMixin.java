package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class TacticalEscapeMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void dndturn$escape(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (window == net.minecraft.client.Minecraft.getInstance().getWindow().handle()
            && ClientControl.escape(action, event)) ci.cancel();
    }
}
