package cc.sighs.dndturn.platform.mixin.server.effect;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(targets={"net.minecraft.world.effect.MobEffect", "net.minecraft.world.effect.HealOrHarmMobEffect"})
public abstract class InstantEffectMixin {
    @WrapMethod(method="applyInstantenousEffect")
    private void dndturn$application(ServerLevel level, Entity source, Entity owner, LivingEntity target,
            int amplifier, double scale, Operation<Void> original) {
        cc.sighs.dndturn.platform.server.runtime.VanillaEffectHooks.instant((MobEffect)(Object)this,level,target,amplifier,scale,
            () -> original.call(level,source,owner,target,amplifier,scale));
    }
}
