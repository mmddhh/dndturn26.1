package cc.sighs.dndturn.platform.mixin.client.simulation;

import cc.sighs.dndturn.platform.client.simulation.ClientEntitySimulation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Authoritative effect packets set remaining durations; rendering does not consume them. */
@Mixin(LivingEntity.class)
public abstract class ClientEffectRoundMixin {
    @Redirect(method="tickEffects",at=@At(value="INVOKE",target="Lnet/minecraft/world/effect/MobEffectInstance;tickClient()V"))
    private void dndturn$participantDuration(MobEffectInstance effect) {
        var entity=(LivingEntity)(Object)this;
        if(!ClientEntitySimulation.holdsEffect(entity, effect)) effect.tickClient();
    }
}
