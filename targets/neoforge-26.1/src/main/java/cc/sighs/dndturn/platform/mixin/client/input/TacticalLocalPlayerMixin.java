package cc.sighs.dndturn.platform.mixin.client.input;

import cc.sighs.dndturn.platform.client.input.ClientControl;
import cc.sighs.dndturn.platform.client.state.ClientCombatState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve connection input/position maintenance when the server pauses bodily simulation. */
@Mixin(LocalPlayer.class)
public abstract class TacticalLocalPlayerMixin {
    @Shadow private Input lastSentInput;
    @Shadow protected abstract void sendPosition();
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void dndturn$cursorFacing(CallbackInfo ci) {
        // After old rotation capture, before input steering, body follow and sendPosition.
        ClientControl.updateFacing((LocalPlayer)(Object)this);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dndturn$maintenance(CallbackInfo ci) {
        if (!ClientCombatState.bodySimulationPaused()) return;
        LocalPlayer player = (LocalPlayer)(Object)this;
        if (!player.isAlive()) return;
        player.input.keyPresses = Input.EMPTY;
        if (player.connection.hasClientLoaded()) {
            if (!lastSentInput.equals(Input.EMPTY)) {
                player.connection.send(new ServerboundPlayerInputPacket(Input.EMPTY));
                lastSentInput = Input.EMPTY;
            }
            sendPosition();
        }
        ci.cancel();
    }
}
