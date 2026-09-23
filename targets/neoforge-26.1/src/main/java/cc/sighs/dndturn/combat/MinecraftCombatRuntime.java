package cc.sighs.dndturn.combat;

import cc.sighs.dndturn.diagnostics.DebugDiagnostics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bridges authoritative encounter lifecycle and simulation gates to Minecraft. */
public final class MinecraftCombatRuntime {
    private MinecraftCombatRuntime() {}

    public static boolean isBodyPaused(ServerPlayer player) {
        ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
        return formal != null && formal.isEntitySimulationPaused(player);
    }

    /** A movement lease opens movement input and client prediction, not the server body tick. */
    public static boolean isPlayerMovementPaused(ServerPlayer player) {
        ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
        return formal != null && formal.isEntityInsidePausedRegion(player)
                && !formal.hasPlayerMoveLease(player.getUUID());
    }

    public static boolean isGameplayInputPaused(ServerPlayer player) {
        ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
        return formal != null && formal.isEntityInsidePausedRegion(player);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level) {
            ServerCombatService formal = ServerCombatService.existing(level.getServer());
            if (formal != null) formal.noteMobTick(entity.getUUID());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        ServerCombatService formal = ServerCombatService.existing(event.getServer());
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
        ServerCombatService formal = ServerCombatService.existing(level.getServer());
        return formal != null && formal.isEntitySimulationPaused(entity);
    }

    public static boolean isFormalBlockSimulationPaused(ServerLevel level, BlockPos pos) {
        ServerCombatService formal = ServerCombatService.existing(level.getServer());
        return formal != null && formal.isBlockSimulationPaused(level, pos);
    }

    public static boolean prepareFormalEntitySimulation(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        ServerCombatService formal = ServerCombatService.existing(level.getServer());
        return formal != null && formal.prepareEntitySimulation(entity);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.level().getServer();
            ServerCombatService formal = ServerCombatService.existing(server);
            if (formal != null) {
                formal.entityProjections().removeViewer(player.getUUID());
                formal.consentDisconnected(player.getUUID());
                DebugDiagnostics.log("player lifecycle release generation={} owner={} dimension={}", formal.generation(), player.getUUID(), player.level().dimension().identifier());
                formal.leave(player.getUUID());
            }
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            ServerCombatService formal = ServerCombatService.existing(level.getServer());
            if (formal != null) formal.noteDeath(event.getEntity().getUUID());
        }
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerCombatService formal = ServerCombatService.existing(level.getServer());
            if (formal != null && (event.getEntity() instanceof AbstractArrow
                || event.getEntity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball))
                formal.noteArrowLeave(event.getEntity());
            if (formal != null) formal.leave(event.getEntity().getUUID());
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball) {
            ServerCombatService formal = ServerCombatService.existing(level.getServer());
            if (formal != null) formal.captureSnowballOrigin(ball, event.loadedFromDisk());
        }
        if (event.getLevel() instanceof ServerLevel level
            && event.getEntity() instanceof AbstractArrow arrow) {
            ServerCombatService formal = ServerCombatService.existing(level.getServer());
            if (formal != null) formal.captureArrowOrigin(arrow, event.loadedFromDisk());
        }
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball && ball.level() instanceof ServerLevel level) {
            ServerCombatService formal = ServerCombatService.existing(level.getServer());
            if (formal != null && formal.handleSnowballImpact(ball, event.getRayTraceResult())) event.setCanceled(true);
        }
        if (event.getProjectile() instanceof AbstractArrow arrow
            && arrow.level() instanceof ServerLevel level) {
            ServerCombatService formal = ServerCombatService.existing(level.getServer());
            if (formal != null && formal.handleArrowImpact(arrow, event.getRayTraceResult()))
                event.setCanceled(true);
        }
    }

    public static boolean allowArrowBlockEffects(AbstractArrow arrow, net.minecraft.world.phys.Vec3 from,
                                                  net.minecraft.world.phys.Vec3 to) {
        if (!(arrow.level() instanceof ServerLevel level)) return true;
        ServerCombatService formal = ServerCombatService.existing(level.getServer());
        return formal == null || formal.allowArrowBlockEffects(arrow, from, to);
    }

    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
            if (formal != null) {
                formal.entityProjections().removeViewer(player.getUUID());
                formal.consentDisconnected(player.getUUID());
                DebugDiagnostics.log("player lifecycle release generation={} owner={} dimension={}", formal.generation(), player.getUUID(), player.level().dimension().identifier());
                formal.leave(player.getUUID());
            }
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ServerCombatService service = ServerCombatService.forServer(event.getServer());
        DebugDiagnostics.log("server started generation={}", service.generation());
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerCombatService service = ServerCombatService.existing(player.level().getServer());
            if (service != null) service.entityProjections().start(player, event.getTarget());
        }
    }

    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerCombatService service = ServerCombatService.existing(player.level().getServer());
            if (service != null) service.entityProjections().stop(player, event.getTarget());
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ServerCombatService.releaseServer(event.getServer());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ServerCombatService.releaseServer(event.getServer());
    }

}
