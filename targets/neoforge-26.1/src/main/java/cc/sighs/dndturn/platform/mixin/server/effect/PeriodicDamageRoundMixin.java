package cc.sighs.dndturn.platform.mixin.server.effect;

import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.PoisonMobEffect;
import net.minecraft.world.effect.WitherMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin({PoisonMobEffect.class, WitherMobEffect.class})
public abstract class PeriodicDamageRoundMixin {
    @Redirect(method="applyEffectTick", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean dndturn$periodicDamage(LivingEntity target, ServerLevel level, DamageSource source, float amount) {
        return VanillaEffectRoundController.hurt(level, target, source, amount);
    }
}
