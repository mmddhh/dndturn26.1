package cc.sighs.dndturn.platform.mixin.server.effect;

import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(targets="net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationRoundMixin {
    @Redirect(method="applyEffectTick", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private void dndturn$periodicHeal(LivingEntity target, float amount) { VanillaEffectRoundController.heal(target, amount); }
}
