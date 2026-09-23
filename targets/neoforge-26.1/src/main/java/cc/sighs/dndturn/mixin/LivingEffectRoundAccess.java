package cc.sighs.dndturn.mixin;

import java.util.Collection;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEffectRoundAccess {
    @Invoker("onEffectUpdated") void dndturn$effectUpdated(MobEffectInstance effect, boolean refresh, Entity source);
    @Invoker("onEffectsRemoved") void dndturn$effectsRemoved(Collection<MobEffectInstance> effects);
}
