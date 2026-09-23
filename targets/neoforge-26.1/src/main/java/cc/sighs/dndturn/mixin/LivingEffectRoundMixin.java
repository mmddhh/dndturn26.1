package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ParticipantEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LivingEntity.class)
public abstract class LivingEffectRoundMixin {
    @Redirect(method="tickEffects", at=@At(value="INVOKE", target="Lnet/minecraft/world/effect/MobEffectInstance;getDuration()I"))
    private int dndturn$noRepeatedRefresh(MobEffectInstance effect) {
        // A frozen multiple of 600 must not send an effect refresh on every real tick.
        return ParticipantEffects.holdEffect((LivingEntity)(Object)this, effect) ? 1 : effect.getDuration();
    }
}
