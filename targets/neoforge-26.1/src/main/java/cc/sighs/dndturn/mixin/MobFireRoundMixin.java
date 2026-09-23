package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ParticipantEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Entity.class)
public abstract class MobFireRoundMixin {
    @Redirect(method="baseTick", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/Entity;setRemainingFireTicks(I)V"))
    private void dndturn$fireDuration(Entity entity, int ticks) {
        if (!ParticipantEffects.holdFire(entity)) entity.setRemainingFireTicks(ticks);
    }
    @Redirect(method="baseTick", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean dndturn$firePeriod(Entity entity, ServerLevel level, DamageSource source, float amount) {
        return !ParticipantEffects.holdFire(entity) && entity.hurtServer(level, source, amount);
    }
}
