package cc.sighs.dndturn.platform.mixin.server.effect;

import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LivingEntity.class)
public abstract class LivingEffectRoundMixin {
    @Redirect(method="tickEffects", at=@At(value="INVOKE", target="Lnet/minecraft/world/effect/MobEffectInstance;getDuration()I"))
    private int dndturn$noRepeatedRefresh(MobEffectInstance effect) {
        // A frozen multiple of 600 must not send an effect refresh on every real tick.
        return VanillaEffectRoundController.holdEffect((LivingEntity)(Object)this, effect) ? 1 : effect.getDuration();
    }
}
