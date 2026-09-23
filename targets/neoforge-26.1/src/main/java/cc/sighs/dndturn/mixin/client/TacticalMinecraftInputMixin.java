package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class TacticalMinecraftInputMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void dndturn$queues(CallbackInfo ci) { ClientControl.beforeKeybinds(); }
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void dndturn$attack(CallbackInfoReturnable<Boolean> ci) {
        if (ClientControl.blockWorldActions()) ci.setReturnValue(false);
    }
    @Inject(method = {"continueAttack", "startUseItem", "pickBlockOrEntity"}, at = @At("HEAD"), cancellable = true)
    private void dndturn$world(CallbackInfo ci) { if (ClientControl.blockWorldActions()) ci.cancel(); }
}
