package cc.sighs.dndturn.platform.mixin.server.control;

import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
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
        if (!ActorControlPolicy.autonomousDecision(AuthorityProjection.actor((EnderMan)(Object)this)).allowed()) ci.setReturnValue(false);
    }
}
