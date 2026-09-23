package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Observations at vanilla lifecycle boundaries, after the actual body / death / impulse operation. */
public final class ActorLifecycleHooks {
    private ActorLifecycleHooks() {}
    public static void simulated(LivingEntity actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        if (runtime != null) runtime.actorStates().simulated(actor);
    }
    public static void playerDeathCompleted(ServerPlayer player) {
        var runtime = ServerRuntime.existingEncounter(player.level().getServer());
        if (runtime != null) runtime.noteCompletedPlayerDeath(player);
    }
    public static void knockbackObserved(LivingEntity actor, UUID operation) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        if (runtime != null) runtime.noteKnockbackImpulse(actor, operation);
    }
}
