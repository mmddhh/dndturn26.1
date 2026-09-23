package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppress prediction at its actual entrypoints; packet authorization remains server-owned. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientGameplayInputMixin {
    @Inject(method = {"startDestroyBlock", "continueDestroyBlock", "destroyBlock"},
        at = @At("HEAD"), cancellable = true)
    private void dndturn$destroy(CallbackInfoReturnable<Boolean> ci) {
        if (ClientControl.blockWorldActions()) ci.setReturnValue(false);
    }

    @Inject(method = {"useItem", "useItemOn", "interact"}, at = @At("HEAD"), cancellable = true)
    private void dndturn$use(CallbackInfoReturnable<InteractionResult> ci) {
        if (ClientControl.blockWorldActions()) ci.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = {"attack", "piercingAttack"}, at = @At("HEAD"), cancellable = true)
    private void dndturn$attack(CallbackInfo ci) {
        if (ClientControl.blockWorldActions()) ci.cancel();
    }
}
