package cc.sighs.dndturn.platform.server.runtime;

import cc.sighs.dndturn.platform.server.action.ItemWorldMutationScope;
import cc.sighs.dndturn.platform.server.action.MinecraftProjectiles;
import cc.sighs.dndturn.platform.server.control.InputPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import cc.sighs.dndturn.platform.server.world.WorldOutcomeHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bridges authoritative encounter lifecycle and simulation gates to Minecraft. */
public final class MinecraftCombatRuntime {
    private MinecraftCombatRuntime() {}

    public static boolean isBodyPaused(ServerPlayer player) {
        EncounterRuntime formal = ServerRuntime.existingEncounter(player.level().getServer());
        return formal != null && formal.isEntitySimulationPaused(player);
    }

    /** A movement lease opens movement input and client prediction, not the server body tick. */
    public static boolean isPlayerMovementPaused(ServerPlayer player) {
        return !InputPolicy.movement(AuthorityProjection.input(player)).allowed();
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level) {
            EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
            if (formal != null) formal.noteMobTick(entity.getUUID());
            if (formal != null && entity instanceof net.minecraft.world.entity.LivingEntity living
                    && !(living instanceof ServerPlayer)) formal.actorStates().simulated(living);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        EncounterRuntime formal = ServerRuntime.existingEncounter(event.getServer());
        if (formal != null) {
            formal.tickConsent();
            formal.confirmPendingDeaths();
            formal.finishMovementTicks();
            formal.reconcileMemberLocations();
            formal.advanceMobTurns();
            formal.flushResultPages();
            formal.syncBodyStateTransitions();
            formal.advancePersistenceClock();
            formal.persistIfChanged();
        }
    }

    public static boolean isFormalEntitySimulationPaused(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
        return formal != null && formal.isEntitySimulationPaused(entity);
    }

    public static boolean prepareFormalEntitySimulation(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
        return formal != null && formal.prepareEntitySimulation(entity).skipsNativeTick();
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.level().getServer();
            EncounterRuntime formal = ServerRuntime.existingEncounter(server);
            if (formal != null) {
                formal.entityProjections().removeViewer(player.getUUID());
                formal.consentDisconnected(player.getUUID());
                formal.leave(player.getUUID());
            }
        }
    }

    /** A mob that acquires a participating player as its target is pulled into that encounter. */
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)) return;
        if (mob.level().getServer() == null) return;
        EncounterRuntime formal = ServerRuntime.existingEncounter(mob.level().getServer());
        if (formal != null) formal.pullHostile(mob, player.getUUID());
    }

    /**
     * A player attacking an entity already inside a turn-based field is pulled into that encounter;
     * the vanilla attack is cancelled so the attack resolves as a tactical action once joined.
     */
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof Mob mob)) return;
        EncounterRuntime formal = ServerRuntime.existingEncounter(player.level().getServer());
        if (formal != null && formal.pullAttackingPlayer(player, mob.getUUID())) event.setCanceled(true);
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
            if (formal != null) formal.noteDeath(event.getEntity().getUUID());
        }
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
            if (formal != null && (event.getEntity() instanceof AbstractArrow
                || event.getEntity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball || MinecraftProjectiles.supported(event.getEntity())))
                formal.noteArrowLeave(event.getEntity());
            if (formal != null) {
                formal.actorStates().detach(event.getEntity().getUUID());
                formal.vanillaEffects().releaseParticipantEffects(event.getEntity());
                formal.leave(event.getEntity().getUUID());
            }
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() && event.getLevel() instanceof ServerLevel && !WorldOutcomeHooks.admitSpawn(event.getEntity())) {
            event.setCanceled(true); return;
        }
        if (!event.loadedFromDisk() && event.getLevel() instanceof ServerLevel) ItemWorldMutationScope.joining(event.getEntity());
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            var service=ServerRuntime.existingEncounter(level.getServer());
            if (service!=null) service.captureItemProjectile(projectile,event.loadedFromDisk());
        }
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball) {
            EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
            if (formal != null) formal.captureSnowballOrigin(ball, event.loadedFromDisk());
        }
        if (event.getLevel() instanceof ServerLevel level
            && event.getEntity() instanceof AbstractArrow arrow) {
            EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
            if (formal != null) formal.captureArrowOrigin(arrow, event.loadedFromDisk());
        }
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball && ball.level() instanceof ServerLevel level) {
            EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
            if (formal != null && formal.handleSnowballImpact(ball, event.getRayTraceResult())) event.setCanceled(true);
        }
        if (event.getProjectile() instanceof AbstractArrow arrow
            && arrow.level() instanceof ServerLevel level) {
            EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
            if (formal != null && formal.handleArrowImpact(arrow, event.getRayTraceResult()))
                event.setCanceled(true);
        }
    }

    public static boolean allowArrowBlockEffects(AbstractArrow arrow, net.minecraft.world.phys.Vec3 from,
                                                  net.minecraft.world.phys.Vec3 to) {
        if (!(arrow.level() instanceof ServerLevel level)) return true;
        EncounterRuntime formal = ServerRuntime.existingEncounter(level.getServer());
        return formal == null || formal.allowArrowBlockEffects(arrow, from, to);
    }

    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EncounterRuntime formal = ServerRuntime.existingEncounter(player.level().getServer());
            if (formal != null) {
                formal.entityProjections().removeViewer(player.getUUID());
                formal.consentDisconnected(player.getUUID());
                formal.leave(player.getUUID());
            }
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        EncounterRuntime service = ServerRuntime.encounters(event.getServer());
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EncounterRuntime service = ServerRuntime.existingEncounter(player.level().getServer());
            if (service != null) service.entityProjections().start(player, event.getTarget());
        }
    }

    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EncounterRuntime service = ServerRuntime.existingEncounter(player.level().getServer());
            if (service != null) service.entityProjections().stop(player, event.getTarget());
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ServerRuntime.release(event.getServer());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ServerRuntime.release(event.getServer());
    }

}
