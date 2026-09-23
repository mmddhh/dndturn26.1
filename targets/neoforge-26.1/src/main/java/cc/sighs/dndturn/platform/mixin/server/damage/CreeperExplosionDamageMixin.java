package cc.sighs.dndturn.platform.mixin.server.damage;

import cc.sighs.dndturn.platform.server.builtin.creeper.CreeperExplosion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerExplosion.class)
public abstract class CreeperExplosionDamageMixin {
    @Redirect(method = "hurtEntities(Ljava/util/List;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean dndturn$damage(Entity target, ServerLevel level, DamageSource source, float amount) {
        return CreeperExplosion.hurt(target, level, source, amount);
    }
}
