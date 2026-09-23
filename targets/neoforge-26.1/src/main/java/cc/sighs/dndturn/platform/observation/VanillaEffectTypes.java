package cc.sighs.dndturn.platform.observation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.*;

/** Fixed-version shared classification; no settlement state or world mutation. */
public final class VanillaEffectTypes {
    private VanillaEffectTypes() {}
    public static boolean supported(MobEffectInstance effect) {
        var value=effect.getEffect().value();
        if (!BuiltInRegistries.MOB_EFFECT.getKey(value).getNamespace().equals("minecraft")) return false;
        String type=value.getClass().getName();
        return value.getClass()==MobEffect.class || type.equals("net.minecraft.world.effect.RegenerationMobEffect")
            || value.getClass()==PoisonMobEffect.class || value.getClass()==WitherMobEffect.class
            || type.equals("net.minecraft.world.effect.AbsorptionMobEffect");
    }
}
