package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class TacticalKeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void dndturn$input(CallbackInfo ci) {
        var steering = cc.sighs.dndturn.client.ClientTacticalPlan.steering();
        if (steering != null) { keyPresses = steering.keys(); moveVector = steering.vector(); return; }
        if (ClientControl.blockMovement()) { keyPresses = Input.EMPTY; moveVector = Vec2.ZERO; }
    }
}
