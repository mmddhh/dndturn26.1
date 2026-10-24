package cc.sighs.dndturn.platform.server.runtime;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import cc.sighs.dndturn.platform.server.damage.DamageAttempt;
import cc.sighs.dndturn.platform.server.damage.TacticalDamageContext;
import cc.sighs.dndturn.platform.server.control.WorldOutcomePolicy;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController;
import cc.sighs.dndturn.platform.server.runtime.AuthorityProjection;
import cc.sighs.dndturn.platform.server.world.WorldOutcomeHooks;
/** Loader event seam and isolated platform probes; execution owners supply DamageAttempt directly. */
public final class DamageHooks {
    private DamageHooks() {}
    private static final DamageAttempt UNSCOPED = new DamageAttempt() {
        public boolean cancelDamageAttempt(LivingEntity target, DamageSource source, float amount, UUID operation) { return false; }
        public Entity damageBody(LivingEntity target, UUID operation) { return target; }
    };
    public static TacticalDamageContext.HurtObservation hurtObserved(ServerLevel level, LivingEntity target, DamageSource source,
            float amount, boolean knockback, UUID operation) {
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        return TacticalDamageContext.hurtObserved(level, target, source, amount, knockback, operation,
            runtime == null ? UNSCOPED : runtime.combat());
    }
    public static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        return hurtObserved(level, target, source, amount, false, null).accepted();
    }
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (WorldOutcomeHooks.authorizesDamage(event.getEntity(), event.getSource())) return;
        if (VanillaEffectRoundController.authorizeIncoming(event)) return;
        if (TacticalDamageContext.configureIncoming(event)) return;
        if (event.getEntity().level() instanceof ServerLevel
                && !WorldOutcomePolicy.unscopedDamage(AuthorityProjection.incomingDamage(event.getEntity(), event.getSource())).allowed()) {
            event.setCanceled(true);
            var runtime = ServerRuntime.existingEncounter(((ServerLevel)event.getEntity().level()).getServer());
            if (runtime != null && runtime.combat().enqueueMeleeIntrusion(event.getEntity(), event.getSource())) return;
            if (event.getEntity() instanceof ServerPlayer player)
                player.sendOverlayMessage(Component.literal("DNDTurn: unsupported external damage rejected"));
        }
    }
}
