package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ActiveBodyControl;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Teleport is not part of the audited standard-melee family, including hurtServer side branches. */
@Mixin(EnderMan.class)
public abstract class EndermanActiveProcessMixin {
    @Inject(method = "teleport(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void dndturn$unsupportedTeleport(double x, double y, double z, CallbackInfoReturnable<Boolean> ci) {
        if (ActiveBodyControl.controlled((EnderMan)(Object)this)) ci.setReturnValue(false);
    }
}
