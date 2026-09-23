package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.platform.server.control.ActiveBodyControl;
import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.control.InputPolicy;
import cc.sighs.dndturn.platform.server.control.SimulationPolicy;
import cc.sighs.dndturn.platform.server.control.WorldOutcomePolicy;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import cc.sighs.dndturn.platform.server.world.EnvironmentExplosion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Read-only capture from owners. No issuance, reconciliation, or world effects. */
public final class AuthorityProjection {
    private AuthorityProjection() {}
    public static WorldOutcomePolicy.IncomingFacts incomingDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource damage) {
        var service = target.level() instanceof ServerLevel level ? ServerRuntime.existingEncounter(level.getServer()) : null;
        return new WorldOutcomePolicy.IncomingFacts(service != null && service.isMember(target.getUUID()),
            EnvironmentExplosion.allows(target, damage));
    }
    public static InputPolicy.Facts input(ServerPlayer player) {
        var service = ServerRuntime.existingEncounter(player.level().getServer());
        return new InputPolicy.Facts(service != null && service.isMember(player.getUUID()),
            service != null && service.isEntityInsidePausedRegion(player),
            service != null && service.hasPlayerMoveLease(player.getUUID()),
            service == null ? null : service.controlFacts().captureAdmission(player, true),
            false);
    }
    public static InputPolicy.Facts containerInput(ServerPlayer player) {
        var facts = input(player);
        var service = ServerRuntime.existingEncounter(player.level().getServer());
        return new InputPolicy.Facts(facts.member(), facts.regionControlled(), facts.movementLease(), facts.organization(),
            service != null && service.tacticalActions().mayUseContainer(player));
    }
    public static ActorControlPolicy.Facts actor(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return new ActorControlPolicy.Facts(false, false, false, null);
        var service = ServerRuntime.existingEncounter(level.getServer());
        return new ActorControlPolicy.Facts(service != null && service.isEntityInsidePausedRegion(entity),
            service != null && (entity instanceof ServerPlayer ? service.hasPlayerMoveLease(entity.getUUID())
                : service.hasMobMoveLease(entity.getUUID())), ActiveBodyControl.inUseStep(entity),
            service == null ? null : service.controlFacts().controlEvidence(entity));
    }
    public static SimulationPolicy.BlockFacts block(ServerLevel level, BlockPos pos) {
        var service = ServerRuntime.existingEncounter(level.getServer());
        return new SimulationPolicy.BlockFacts(level.tickRateManager().runsNormally(),
            service != null && service.isBlockSimulationPaused(level, pos));
    }
}
