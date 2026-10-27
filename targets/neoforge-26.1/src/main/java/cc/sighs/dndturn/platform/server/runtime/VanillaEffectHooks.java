package cc.sighs.dndturn.platform.server.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;

/** Resolves the server instance only at the Minecraft effect tick seam. */
public final class VanillaEffectHooks {
    private VanillaEffectHooks() {}
    public static void instant(net.minecraft.world.effect.MobEffect effect, ServerLevel level, LivingEntity target,
            int amplifier, double scale, Runnable nativeCall) {
        if (cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController.instant(effect,level,target,amplifier,scale,nativeCall)) return;
        var runtime=ServerRuntime.existingEncounter(level.getServer());
        if (runtime!=null && runtime.isMember(target.getUUID())) throw new IllegalStateException("instant effect requires a bound application");
        nativeCall.run();
    }
    public static boolean holdEffect(LivingEntity actor, MobEffectInstance effect) {
        if (!(actor.level() instanceof ServerLevel level)) return false;
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        return runtime != null && runtime.holdVanillaEffect(actor, effect);
    }
    public static boolean suppressSpecial(LivingEntity actor, MobEffectInstance effect) {
        if (!(actor.level() instanceof ServerLevel level)) return false;
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        return runtime != null && runtime.isMember(actor.getUUID())
            && !cc.sighs.dndturn.platform.server.effect.vanilla.EffectAdapters.supported(effect);
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
