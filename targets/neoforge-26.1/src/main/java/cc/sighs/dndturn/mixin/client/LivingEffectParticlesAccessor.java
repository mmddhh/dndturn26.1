package cc.sighs.dndturn.mixin.client;

import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEffectParticlesAccessor {
    @Accessor("DATA_EFFECT_PARTICLES")
    static EntityDataAccessor<List<ParticleOptions>> dndturn$particles() { throw new AssertionError(); }
    @Accessor("DATA_EFFECT_AMBIENCE_ID")
    static EntityDataAccessor<Boolean> dndturn$ambient() { throw new AssertionError(); }
}
