package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.TacticalDamageContext;
import java.util.Stack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bridge the fixed-version blocking check without invoking vanilla mitigation twice. */
@Mixin(LivingEntity.class)
public abstract class TacticalShieldBlockMixin {
    @Shadow protected Stack<DamageContainer> damageContainers;

    @Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
    private void dndturn$captureShieldWear(ServerLevel level, DamageSource source, float amount,
                                           CallbackInfoReturnable<Float> callback) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (TacticalDamageContext.applies(target, source)
            && TacticalDamageContext.captureShieldAttempt(target, source, amount,
                damageContainers.peek()))
            callback.setReturnValue(0.0F);
    }
}
