package cc.sighs.dndturn.platform.server.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;

/** Resolves the server instance only at the Minecraft effect tick seam. */
public final class VanillaEffectHooks {
    private VanillaEffectHooks() {}
    public static boolean holdEffect(LivingEntity actor, MobEffectInstance effect) {
        if (!(actor.level() instanceof ServerLevel level)) return false;
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        return runtime != null && runtime.holdVanillaEffect(actor, effect);
    }
    public static boolean holdFire(Entity actor) {
        if (!(actor.level() instanceof ServerLevel level)) return false;
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        return runtime != null && runtime.holdVanillaFire(actor);
    }
    public static void heal(LivingEntity target, float amount) {
        cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController.heal(target, amount);
    }
    public static boolean hurt(ServerLevel level, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController.hurt(level, target, source, amount);
    }
}
