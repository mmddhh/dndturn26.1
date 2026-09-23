package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.TacticalDamageContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep vanilla equipment wear while the tactical damage number bypasses armor reduction. */
@Mixin(LivingEntity.class)
public abstract class TacticalArmorDurabilityMixin {
    @Shadow protected abstract void hurtArmor(DamageSource source, float damage);

    @Inject(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
        at = @At("HEAD"))
    private void dndturn$wearArmor(DamageSource source, float damage, CallbackInfoReturnable<Float> callback) {
        if (TacticalDamageContext.restoresArmorDurability((LivingEntity) (Object) this, source)) {
            this.hurtArmor(source, damage);
        }
    }
}
