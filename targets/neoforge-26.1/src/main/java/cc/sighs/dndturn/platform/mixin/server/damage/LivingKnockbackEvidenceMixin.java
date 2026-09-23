package cc.sighs.dndturn.platform.mixin.server.damage;

import cc.sighs.dndturn.platform.server.actor.ActorLifecycleHooks;
import cc.sighs.dndturn.platform.server.damage.TacticalDamageContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records an actual vanilla impulse after the cancellable knockback event has run. */
@Mixin(LivingEntity.class)
public abstract class LivingKnockbackEvidenceMixin {
    @Unique private Vec3 dndturn$velocityBeforeKnockback;

    @Inject(method = "knockback", at = @At("HEAD"))
    private void dndturn$beforeKnockback(double power, double x, double z, CallbackInfo callback) {
        dndturn$velocityBeforeKnockback = ((LivingEntity) (Object) this).getDeltaMovement();
    }

    @Inject(method = "knockback", at = @At("RETURN"))
    private void dndturn$afterKnockback(double power, double x, double z, CallbackInfo callback) {
        LivingEntity target = (LivingEntity) (Object) this;
        Vec3 before = dndturn$velocityBeforeKnockback;
        dndturn$velocityBeforeKnockback = null;
        if (before == null || before.equals(target.getDeltaMovement())
            || !(target.level() instanceof ServerLevel level)) return;
        ActorLifecycleHooks.knockbackObserved(target,
            TacticalDamageContext.currentOperationId(target));
    }
}
