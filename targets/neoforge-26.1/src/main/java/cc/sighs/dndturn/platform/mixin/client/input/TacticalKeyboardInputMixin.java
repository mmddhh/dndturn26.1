package cc.sighs.dndturn.platform.mixin.client.input;

import cc.sighs.dndturn.platform.client.action.ClientTacticalPlan;
import cc.sighs.dndturn.platform.client.input.ClientControl;
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
        var steering = ClientTacticalPlan.steering();
        if (steering != null) { keyPresses = steering.keys(); moveVector = steering.vector(); return; }
        if (ClientControl.blockMovement()) { keyPresses = Input.EMPTY; moveVector = Vec2.ZERO; }
    }
}
