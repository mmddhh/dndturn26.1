package cc.sighs.dndturn.platform.mixin.server.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(targets="net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class InstantHealHarmMixin {
    @Redirect(method="applyInstantenousEffect", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private void dndturn$heal(LivingEntity target, float amount) {
        cc.sighs.dndturn.platform.server.runtime.VanillaEffectHooks.heal(target,amount);
    }
    @Redirect(method="applyInstantenousEffect", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean dndturn$hurt(LivingEntity target, ServerLevel level, DamageSource source, float amount) {
        return cc.sighs.dndturn.platform.server.runtime.VanillaEffectHooks.hurt(level,target,source,amount);
    }
}
