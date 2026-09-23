package cc.sighs.dndturn.combat;

import cc.sighs.dndturn.mixin.LivingEntityDeathAccessor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

/** Bridges the administrator-only debug-local clock to Minecraft ticks. */
public final class MinecraftCombatRuntime {
    private static final Map<MinecraftServer, CombatTimeController> CONTROLLERS = new HashMap<>();
    private static final Map<MinecraftServer, Set<UUID>> PENDING_LOCAL_DEATHS = new HashMap<>();

    private MinecraftCombatRuntime() {}

    private static CombatTimeController controller(MinecraftServer server) {
        return CONTROLLERS.computeIfAbsent(server, ignored -> new CombatTimeController());
    }

    public static boolean isLocalMember(MinecraftServer server, UUID member) {
        CombatTimeController local = CONTROLLERS.get(server);
        return local != null && local.isMember(member);
    }

    public static long localControllerTicks(MinecraftServer server, UUID member) {
        CombatTimeController local = CONTROLLERS.get(server);
        return local == null ? 0 : local.controllerTicks(member);
    }

    public static UUID beginLocal(MinecraftServer server, Set<UUID> members) {
        ServerCombatService formal = ServerCombatService.existing(server);
        if (formal != null && members.stream().anyMatch(formal::isMember))
            throw new IllegalStateException("member already in a formal encounter");
        UUID encounterId = controller(server).begin(members);
        syncMembers(server, members);
        return encounterId;
    }

    public static void endLocal(MinecraftServer server, UUID member) {
        Set<UUID> released = controller(server).endFor(member);
        syncMembers(server, released);
    }

    public static void authorizeLocalBodyTicks(MinecraftServer server, UUID member, int ticks) {
        controller(server).authorizeActionTicks(member, ticks);
        syncMember(server, member);
    }

    public static boolean isBodyPaused(ServerPlayer player) {
        CombatTimeController controller = CONTROLLERS.get(player.level().getServer());
        ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
        return controller != null && controller.isBodyPaused(player.getUUID())
            || formal != null && formal.isEntitySimulationPaused(player);
    }

    /** A movement lease opens movement input and client prediction, not the server body tick. */
    public static boolean isPlayerMovementPaused(ServerPlayer player) {
        CombatTimeController controller = CONTROLLERS.get(player.level().getServer());
        ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
        return controller != null && controller.isBodyPaused(player.getUUID())
            || formal != null && formal.isEntityInsidePausedRegion(player)
                && !formal.hasPlayerMoveLease(player.getUUID());
    }

    public static boolean isGameplayInputPaused(ServerPlayer player) {
        CombatTimeController controller = CONTROLLERS.get(player.level().getServer());
        ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
        return controller != null && controller.isBodyPaused(player.getUUID())
            || formal != null && formal.isEntityInsidePausedRegion(player);
    }

    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level) {
            CombatTimeController controller = CONTROLLERS.get(level.getServer());
            if (controller != null && controller.isBodyPaused(entity.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level) {
            ServerCombatService formal = ServerCombatService.existing(level.getServer());
            if (formal != null) formal.noteMobTick(entity.getUUID());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        confirmLocalDeaths(event.getServer());
        CombatTimeController controller = CONTROLLERS.get(event.getServer());
        if (controller != null) {
            List<UUID> newlyPaused = controller.finishServerTick();
            syncMembers(event.getServer(), Set.copyOf(newlyPaused));
        }
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
                formal.leave(player.getUUID());
            }
            Set<UUID> released = controller(server).endFor(player.getUUID());
            for (UUID member : released) {
                if (!member.equals(player.getUUID())) {
                    syncMember(server, member);
                }
            }
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            if (isLocalMember(level.getServer(), event.getEntity().getUUID()))
                PENDING_LOCAL_DEATHS.computeIfAbsent(level.getServer(), ignored -> new HashSet<>())
                    .add(event.getEntity().getUUID());
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
            if (isLocalMember(level.getServer(), event.getEntity().getUUID()))
                endLocal(level.getServer(), event.getEntity().getUUID());
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
                formal.leave(player.getUUID());
            }
            endLocal(player.level().getServer(), player.getUUID());
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ServerCombatService.forServer(event.getServer());
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
        CONTROLLERS.remove(event.getServer());
        PENDING_LOCAL_DEATHS.remove(event.getServer());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ServerCombatService.releaseServer(event.getServer());
    }

    private static void syncMembers(MinecraftServer server, Set<UUID> members) {
        for (UUID member : members) {
            syncMember(server, member);
        }
    }

    private static void confirmLocalDeaths(MinecraftServer server) {
        Set<UUID> pending = PENDING_LOCAL_DEATHS.get(server);
        if (pending == null) return;
        for (UUID member : Set.copyOf(pending)) {
            pending.remove(member);
            for (ServerLevel level : server.getAllLevels()) {
                Entity found = level.getEntity(member);
                if (found instanceof LivingEntity living
                    && ((LivingEntityDeathAccessor) living).dndturn$isDead()) {
                    endLocal(server, member);
                    break;
                }
            }
        }
        if (pending.isEmpty()) PENDING_LOCAL_DEATHS.remove(server);
    }

    private static void syncMember(MinecraftServer server, UUID member) {
        ServerPlayer player = server.getPlayerList().getPlayer(member);
        if (player != null) {
            ServerCombatService service = ServerCombatService.existing(server);
            if (service != null) service.sendBodyState(player, isBodyPaused(player), formalMovementAllowed(player));
        }
    }

    private static boolean formalMovementAllowed(ServerPlayer player) {
        ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
        return formal != null && formal.hasPlayerMoveLease(player.getUUID())
            && !isPlayerMovementPaused(player);
    }
}
