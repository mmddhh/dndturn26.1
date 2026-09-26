package cc.sighs.dndturn.combat;

import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import cc.sighs.dndturn.mixin.LivingEntityDeathAccessor;
import cc.sighs.dndturn.mixin.ArrowDamageAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** The only target-side owner of formal encounter rules for one server instance. */
public final class ServerCombatService implements RegionalScheduledTicks.RegionAccess {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, ServerCombatService> SERVERS = new IdentityHashMap<>();
    private static final Set<MinecraftServer> CLOSED_SERVERS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private boolean closing;

    private final MinecraftServer server;
    private final UUID generation = UUID.randomUUID();
    private final ServerCombatConfig config;
    private final CombatEngine engine;
    private final ServerConsentCoordinator consent;
    private final ServerEntityProjections entityProjections;
    private final CombatSubscriptions subscriptions;
    private final CombatSavedData savedData;
    private final boolean recoveryFailure;
    private CombatEngine.Revision lastPersistedRevision;
    private CombatEngine.Revision cachedRuleRevision;
    private CombatStateSnapshot cachedRuleSnapshot;
    private long cumulativeServerTicks;
    private long lastPersistedClock;
    private final List<CombatPersistenceEnvelope.LeaseEvidence> restoredLeaseEvidence = new ArrayList<>();
    private final Set<UUID> recoveryPending = new HashSet<>();
    private final Random attackRandom = new Random();
    private final Map<UUID, Random> gameTestAttackRandom = new HashMap<>();
    /** Exact region snapshots are a query projection; CombatEngine owns membership and phase. */
    private final Map<UUID, EncounterRegion> regions = new HashMap<>();
    private final Map<UUID, CombatPersistenceEnvelope.CapturedSettings> capturedSettings = new HashMap<>();
    private final Map<UUID, Set<Long>> regionChunks = new HashMap<>();
    private final Set<UUID> departuresDuringWorldEffect = new HashSet<>();
    private final Set<UUID> pendingDeaths = new HashSet<>();
    private TacticalActions tacticalActions;
    public TacticalActions tacticalActions() {
        requireThread();
        if (tacticalActions == null) tacticalActions = new TacticalActions(this, server, engine);
        return tacticalActions;
    }
    long planClock() { return cumulativeServerTicks; }
    void finishPlanMovement(ServerPlayer player) { finishPlayerMove(player); }
    void finishPlanMovement(UUID owner) { var lease = playerMoves.get(owner); if (lease != null) closePlayerMove(lease); }
    void beginPlanMovement(ServerPlayer player, UUID parent, UUID operation) {
        UUID id = engine.encounterOf(player.getUUID());
        var snapshot = new OperationRecord.Snapshot(operation, parent, id, player.getUUID(), player.getUUID(), null,
            cumulativeServerTicks, engine.stateView(id).version(), null, null, OperationRecord.Kind.MOVE, generation());
        if (unsupportedPlayerMovement(player) || !engine.beginPlanStep(snapshot)) throw new IllegalStateException("plan movement rejected");
        playerMoves.put(player.getUUID(), new PlayerMoveLease(id, operation, player.getUUID()));
        sync(engine.stateView(id)); syncBodyStateTransitions();
    }
    private final Map<UUID, PlayerMoveLease> playerMoves = new HashMap<>();
    /** Short-lived observed force events; vanilla's impulse grace is not proof of movement. */
    private final Map<UUID, ForcedMovementEvidence> forcedMovements = new HashMap<>();
    private final Map<UUID, Long> sessionSequences = new HashMap<>();
    private final Map<UUID, Long> projectionRevisions = new HashMap<>();
    private long nextSessionSequence;
    private final Map<UUID, MobMoveLease> mobMoves = new HashMap<>();
    private record StartRequestReceipt(UUID owner, UUID encounterId) {}
    private final Map<UUID, StartRequestReceipt> startReceipts = new HashMap<>();
    private record ExitRequestReceipt(UUID owner, UUID encounterId, long expectedVersion) {}
    private final Map<UUID, ExitRequestReceipt> exitReceipts = new HashMap<>();
    /** The END intent observes a later version than its MOVE_BEGIN operation snapshot. */
    private record MoveEndReceipt(UUID owner, UUID encounterId, long expectedVersion) {}
    private final Map<UUID, MoveEndReceipt> moveEndReceipts = new HashMap<>();
    private final Map<UUID, CombatEngine.MergePlan> pendingMerges = new HashMap<>();
    private final Map<UUID, String> mergeFailures = new HashMap<>();
    /** Value evidence survives departure of the owner; simulation domain is a scheduler projection. */
    private final Map<UUID, ProjectileOrigin> projectileOrigins = new HashMap<>();
    private final Map<UUID, UUID> projectileSimulationDomains = new HashMap<>();
    private final Set<UUID> retainedProjectileHistory = new HashSet<>();
    /** A verified collision waits here until its source and target domains merge. */
    private record PendingProjectileAttack(UUID targetId, UUID sourceEncounterId) {}
    private final Map<UUID, PendingProjectileAttack> pendingProjectileAttacks = new HashMap<>();
    private final Map<UUID, CombatPersistenceEnvelope.QuarantinedProjectile> quarantinedProjectiles = new HashMap<>();
    private final RegionalScheduledTicks scheduledTicks = new RegionalScheduledTicks(this);
    private final Map<ServerLevel, UUID> activeEnvironmentSteps = new IdentityHashMap<>();
    private final Map<ServerLevel, UUID> activeEnvironmentEncounters = new IdentityHashMap<>();
    private final Map<ServerLevel, UUID> lastEnvironmentEncounter = new IdentityHashMap<>();
    private int worldEffectDepth;

    private static final class PlayerMoveLease {
        final UUID encounterId;
        final UUID operationId;
        final UUID playerId;
        int nextStep;
        int spentTicks;
        int observedSteps;
        long observedTick = Long.MIN_VALUE;
        boolean moved;
        boolean mixedTick;
        String forcedCause;
        int baseCost;
        boolean jumped;
        double presentationHorizontal;

        PlayerMoveLease(UUID encounterId, UUID operationId, UUID playerId) {
            this.encounterId = encounterId;
            this.operationId = operationId;
            this.playerId = playerId;
        }
    }

    private static final class ForcedMovementEvidence {
        final UUID eventId;
        final UUID encounterId;
        final UUID sourceOperation;
        final String cause;
        final long sourceTick;
        long classifiedTick = Long.MIN_VALUE;

        ForcedMovementEvidence(UUID eventId, UUID encounterId, UUID sourceOperation,
                               String cause, long sourceTick) {
            this.eventId = eventId;
            this.encounterId = encounterId;
            this.sourceOperation = sourceOperation;
            this.cause = cause;
            this.sourceTick = sourceTick;
        }

        boolean available(long tick) {
            return tick >= sourceTick && tick <= sourceTick + 1
                && (classifiedTick == Long.MIN_VALUE || classifiedTick == tick);
        }

        String description() {
            return cause + " event=" + eventId
                + (sourceOperation == null ? "" : " sourceOperation=" + sourceOperation);
        }
    }

    private static final class MobMoveLease {
        final UUID encounterId;
        final UUID operationId;
        final UUID mobId;
        Vec3 previous;
        boolean previousGrounded;
        boolean previousWater;
        int step;
        int spent;
        int stalled;
        boolean pathStarted;
        boolean ticked;
        boolean budgetBlocked;
        int authorizedCost;
        boolean underreserveNextExpensiveStepForGameTest;
        net.minecraft.world.level.pathfinder.Path ownedPath;

        MobMoveLease(UUID encounterId, UUID operationId, Mob mob) {
            this.encounterId = encounterId;
            this.operationId = operationId;
            this.mobId = mob.getUUID();
            this.previous = mob.position();
            this.previousGrounded = mob.onGround();
            this.previousWater = mob.isInWater();
        }
    }

    private enum MobObservation { NO_DISPLACEMENT, DISPLACED, TERMINAL_COMPLETED, TERMINAL_UNKNOWN }

    private ServerCombatService(MinecraftServer server) {
        this(server, server.getDataStorage().computeIfAbsent(CombatSavedData.TYPE));
    }

    private ServerCombatService(MinecraftServer server, CombatSavedData savedData) {
        this.server = server;
        this.config = ServerCombatConfig.load(server);
        this.consent = new ServerConsentCoordinator(server, generation, this::isMember,
            this::sampleConsentRegion, this::commitConsentedEncounter);
        this.entityProjections = new ServerEntityProjections(server, generation, this::presentationFacts);
        this.savedData = java.util.Objects.requireNonNull(savedData);
        CombatEngine restored = null;
        CombatPersistenceEnvelope saved = null;
        boolean failed = false;
        try {
            saved = savedData.envelope();
            if (saved != null) {
                restored = CombatRecoveryCandidate.validate(saved, new Random());
                if (restored.movementTicksPerTurn() != config.movementTicks()
                    || restored.environmentTicks() != config.environmentTicks())
                    LOGGER.info("Existing encounters retain captured budgets; new encounters use current server config");
            }
        } catch (RuntimeException invalid) {
            restored = null;
            saved = null;
            failed = true;
            LOGGER.error("Combat save cannot be safely restored; preserving it and disabling new encounters",
                invalid);
        }
        this.engine = restored == null
            ? new CombatEngine(new Random(), config.movementTicks(), config.environmentTicks()) : restored;
        this.engine.bindObservationEpoch(generation);
        this.subscriptions = new CombatSubscriptions(server, engine, generation);
        this.recoveryFailure = failed;
        this.lastPersistedRevision = savedData.json().isBlank() ? null : engine.revision();
        if (!failed && saved != null) {
            cumulativeServerTicks = saved.cumulativeServerTicks();
            lastPersistedClock = cumulativeServerTicks;
            nextSessionSequence = saved.nextSessionSequence();
            sessionSequences.putAll(saved.sessionSequences());
            capturedSettings.putAll(saved.capturedSettings());
            projectileOrigins.putAll(saved.projectileOrigins());
            projectileSimulationDomains.putAll(saved.projectileDomains());
            quarantinedProjectiles.putAll(saved.quarantinedProjectiles());
            for (var encounter : saved.rules().encounters()) {
                for (var operation : encounter.pending().values()) {
                    if (operation.source() != null && saved.projectileOrigins().containsKey(operation.source()))
                        quarantinedProjectiles.putIfAbsent(operation.source(),
                            new CombatPersistenceEnvelope.QuarantinedProjectile(operation.operationId(),
                                encounter.id(), operation.target(), "restored executing projectile effect cannot be replayed"));
                }
            }
            // Existing saved launch records may already be referenced by an ended domain.
            // Retention never grants scheduling or effect authorization.
            retainedProjectileHistory.addAll(saved.projectileOrigins().keySet());
            for (var entry : saved.pendingProjectileAttacks().entrySet()) {
                pendingProjectileAttacks.put(entry.getKey(), new PendingProjectileAttack(
                    entry.getValue().targetId(), entry.getValue().sourceEncounterId()));
                quarantineProjectile(entry.getKey(), entry.getValue().sourceEncounterId(),
                    entry.getValue().targetId(), "restored pending collision requires evidence; replay forbidden");
            }
            for (CombatStateSnapshot.MergePlanState plan : saved.pendingMerges()) {
                CombatEngine.MergePlan restoredPlan = plan.restore();
                pendingMerges.put(restoredPlan.primary(), restoredPlan);
            }
            for (var entry : saved.startReceipts().entrySet())
                startReceipts.put(entry.getKey(), new StartRequestReceipt(
                    entry.getValue().owner(), entry.getValue().encounterId()));
            for (var entry : saved.exitReceipts().entrySet())
                exitReceipts.put(entry.getKey(), new ExitRequestReceipt(
                    entry.getValue().owner(), entry.getValue().encounterId(),
                    entry.getValue().expectedVersion()));
            for (var entry : saved.moveEndReceipts().entrySet())
                moveEndReceipts.put(entry.getKey(), new MoveEndReceipt(
                    entry.getValue().owner(), entry.getValue().encounterId(),
                    entry.getValue().expectedVersion()));
            restoredLeaseEvidence.addAll(saved.leases());
            capturedSettings.keySet().retainAll(engine.encounterIds());
            for (UUID encounterId : engine.encounterIds()) {
                engine.revokeRestoredPermits(encounterId);
                recoveryPending.add(encounterId);
            }
            if (!recoveryPending.isEmpty())
                lastPersistedRevision = null;
        }
        if (!failed) for (UUID encounterId : engine.encounterIds()) {
            EncounterRegion region = engine.stateView(encounterId).region();
            if (region != null) {
                regions.put(encounterId, region);
                regionChunks.put(encounterId, exactRegionChunks(region));
                sessionSequences.put(encounterId, ++nextSessionSequence);
            }
        }
    }

    /** Exercises production recovery with isolated data; never registers a running service. */
    public static ServerCombatService restoreForGameTest(MinecraftServer server, CombatSavedData savedData) {
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer) || !server.isSameThread())
            throw new IllegalStateException("isolated recovery requires the GameTest server thread");
        return new ServerCombatService(server, savedData);
    }

    public static ServerCombatService forServer(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("combat service requires server thread");
        if (CLOSED_SERVERS.contains(server)) throw new IllegalStateException("combat service is closed");
        return SERVERS.computeIfAbsent(server, ServerCombatService::new);
    }

    public static ServerCombatService existing(MinecraftServer server) {
        return SERVERS.get(server);
    }

    public static void releaseServer(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("combat service requires server thread");
        ServerCombatService service = SERVERS.get(server);
        if (!CLOSED_SERVERS.add(server) || service == null) return;
        service.closing = true;
        try {
            // Save running state and pending evidence, not artificially ended encounters.
            service.persistNow();
        } finally {
            try {
                RuntimeException releaseFailure = null;
                for (MobMoveLease lease : List.copyOf(service.mobMoves.values())) {
                    try { service.revokeMobLease(lease); }
                    catch (RuntimeException failure) {
                        if (releaseFailure == null) releaseFailure = failure;
                        else releaseFailure.addSuppressed(failure);
                    }
                }
                if (releaseFailure != null) throw releaseFailure;
            } finally {
                try { service.scheduledTicks.releaseAll(); }
                finally {
                    service.playerMoves.clear();
                    service.regions.clear();
                    service.regionChunks.clear();
                    service.subscriptions.clear();
                    service.entityProjections.clear();
                    SERVERS.remove(server);
                }
            }
        }
    }

    public void persistIfChanged() {
        requireThread();
        if (recoveryFailure) return;
        CombatEngine.Revision revision = engine.revision();
        if (!revision.equals(lastPersistedRevision)
            || cumulativeServerTicks - lastPersistedClock >= 20
            || lastPersistedRevision == null) {
            Map<UUID, CombatPersistenceEnvelope.PendingProjectileAttack> pendingArrows = new HashMap<>();
            for (var entry : pendingProjectileAttacks.entrySet())
                pendingArrows.put(entry.getKey(), new CombatPersistenceEnvelope.PendingProjectileAttack(
                    entry.getValue().targetId(), entry.getValue().sourceEncounterId()));
            Map<UUID, CombatPersistenceEnvelope.StartReceipt> starts = new HashMap<>();
            startReceipts.forEach((id, value) -> starts.put(id,
                new CombatPersistenceEnvelope.StartReceipt(value.owner(), value.encounterId())));
            Map<UUID, CombatPersistenceEnvelope.ExitReceipt> exits = new HashMap<>();
            exitReceipts.forEach((id, value) -> exits.put(id,
                new CombatPersistenceEnvelope.ExitReceipt(value.owner(), value.encounterId(),
                    value.expectedVersion())));
            Map<UUID, CombatPersistenceEnvelope.MoveEndReceipt> moveEnds = new HashMap<>();
            moveEndReceipts.forEach((id, value) -> moveEnds.put(id,
                new CombatPersistenceEnvelope.MoveEndReceipt(value.owner(), value.encounterId(),
                    value.expectedVersion())));
            List<CombatPersistenceEnvelope.LeaseEvidence> leases = new ArrayList<>(restoredLeaseEvidence);
            for (PlayerMoveLease lease : playerMoves.values()) leases.add(
                new CombatPersistenceEnvelope.LeaseEvidence(lease.encounterId, lease.operationId,
                    lease.playerId, "PLAYER_MOVE", lease.nextStep, lease.spentTicks));
            for (MobMoveLease lease : mobMoves.values()) leases.add(
                new CombatPersistenceEnvelope.LeaseEvidence(lease.encounterId, lease.operationId,
                    lease.mobId, "MOB_MOVE", lease.step, lease.spent));
            savedData.update(new CombatPersistenceEnvelope(CombatPersistenceEnvelope.CURRENT_SCHEMA,
                captureRules(revision), cumulativeServerTicks, nextSessionSequence,
                sessionSequences, capturedSettings, projectileOrigins,
                projectileSimulationDomains, pendingArrows,
                pendingMerges.values().stream()
                    .map(CombatStateSnapshot.MergePlanState::capture).toList(),
                starts, exits, moveEnds, leases, quarantinedProjectiles));
            lastPersistedRevision = revision;
            lastPersistedClock = cumulativeServerTicks;
        }
    }

    private void persistNow() {
        lastPersistedRevision = null;
        persistIfChanged();
    }

    public void advancePersistenceClock() {
        requireThread();
        cumulativeServerTicks = Math.addExact(cumulativeServerTicks, 1);
        simulationDecisions.clear();
    }

    private CombatPersistenceEnvelope.CapturedSettings settingsFor(UUID encounterId) {
        CombatPersistenceEnvelope.CapturedSettings settings = capturedSettings.get(
            engine.canonicalEncounterId(encounterId));
        if (settings == null) throw new IllegalStateException("encounter lacks captured settings");
        return settings;
    }
    public boolean matchesGeneration(UUID candidate) { return generation.equals(candidate); }
    public UUID generation() { return generation; }

    private CombatStateSnapshot captureRules(CombatEngine.Revision revision) {
        if (cachedRuleSnapshot == null || !revision.equals(cachedRuleRevision)) {
            cachedRuleSnapshot = engine.exportSnapshot();
            cachedRuleRevision = revision;
        }
        return cachedRuleSnapshot;
    }
    public UUID activeEnvironmentStep(ServerLevel level) { return activeEnvironmentSteps.get(level); }
    public boolean isMember(UUID entityId) { return engine.encounterOf(entityId) != null; }
    public void useAttackRandomForGameTest(UUID encounterId, Random random) {
        requireThread();
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer)
            || !engine.encounterIds().contains(encounterId))
            throw new IllegalStateException("attack random injection requires an active GameTest encounter");
        gameTestAttackRandom.put(encounterId, Objects.requireNonNull(random));
    }
    public void noteDeath(UUID entityId) {
        requireThread();
        if (engine.encounterOf(entityId) != null) pendingDeaths.add(entityId);
    }

    public void confirmPendingDeaths() {
        requireThread();
        if (worldEffectDepth != 0) return;
        for (UUID entityId : Set.copyOf(pendingDeaths)) {
            pendingDeaths.remove(entityId);
            Entity entity = resolve(entityId);
            if (entity instanceof LivingEntity living
                && ((LivingEntityDeathAccessor) living).dndturn$isDead()) leave(entityId);
        }
    }
    public UUID encounterOf(UUID entityId) { return engine.encounterOf(entityId); }

    public UUID encounterAtBlock(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().identifier().toString();
        UUID selected = null;
        for (var entry : regions.entrySet()) {
            EncounterRegion region = entry.getValue();
            if (region.dimension().equals(dimension)
                && region.containsBlock(pos.getX(), pos.getY(), pos.getZ())
                && (selected == null || entry.getKey().compareTo(selected) < 0)) selected = entry.getKey();
        }
        return selected;
    }

    public Set<Long> candidateRegionChunks(ServerLevel level) {
        Set<Long> chunks = new HashSet<>();
        String dimension = level.dimension().identifier().toString();
        for (var entry : regions.entrySet()) {
            if (entry.getValue().dimension().equals(dimension))
                chunks.addAll(regionChunks.getOrDefault(entry.getKey(), Set.of()));
        }
        return chunks;
    }

    private static Set<Long> exactRegionChunks(EncounterRegion region) {
        Set<Long> chunks = new HashSet<>();
        int y = (int) Math.ceil(region.minY() - 0.5);
        if (y + 0.5 > region.maxY()) return chunks;
        int minX = Math.floorDiv((int) Math.floor(region.discovery().minX() - region.radius()), 16);
        int maxX = Math.floorDiv((int) Math.floor(region.discovery().maxX() + region.radius()), 16);
        int minZ = Math.floorDiv((int) Math.floor(region.discovery().minZ() - region.radius()), 16);
        int maxZ = Math.floorDiv((int) Math.floor(region.discovery().maxZ() + region.radius()), 16);
        for (int chunkX = minX; chunkX <= maxX; chunkX++) for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
            boolean relevant = false;
            for (int x = chunkX * 16; x < chunkX * 16 + 16 && !relevant; x++)
                for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++)
                    if (region.containsBlock(x, y, z)) { relevant = true; break; }
            if (relevant) chunks.add(net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ));
        }
        return Set.copyOf(chunks);
    }

    /** Called before ServerLevel advances game time or drains either scheduled queue. */
    public void beforeLevelTick(ServerLevel level) {
        requireThread();
        if (regions.isEmpty() || candidateRegionChunks(level).isEmpty()) return;
        auditRestoredEncounters(level);
        // Isolated recovery probes may audit values, but cannot replace the live
        // scheduler's queue ownership (including while the world is frozen).
        if (SERVERS.get(server) != this) return;
        // New encounters become visible atomically before scheduling platform work.
        for (UUID id : engine.encounterIds()) {
            EncounterRegion region = regions.get(id);
            if (region == null || !region.dimension().equals(level.dimension().identifier().toString())
                || pendingMerges.values().stream().anyMatch(plan -> plan.encounters().contains(id))) continue;
            if (engine.encounterIds().stream().anyMatch(other -> !other.equals(id)
                && region.overlaps(engine.stateView(other).region()))) scheduleMerge(id);
        }
        attemptPendingMerges(level);
        scheduledTicks.captureAll(level);
        if (!level.tickRateManager().runsNormally() || level.isDebug()) return;
        List<UUID> candidates = new ArrayList<>();
        for (UUID id : engine.encounterIds()) {
            if (recoveryPending.contains(id)) continue;
            EncounterRegion region = regions.get(id);
            if (region != null && region.dimension().equals(level.dimension().identifier().toString())
                && engine.stateView(id).phase() == EncounterPhase.ENVIRONMENT) candidates.add(id);
        }
        candidates.sort(UUID::compareTo);
        UUID last = lastEnvironmentEncounter.get(level);
        int offset = last == null ? 0 : (candidates.indexOf(last) + 1) % Math.max(1, candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            UUID encounterId = candidates.get((offset + i) % candidates.size());
            CombatEngine.StateView state = engine.stateView(encounterId);
            boolean loadedMember = state.members().keySet().stream()
                .anyMatch(id -> level.getEntity(id) != null);
            if (loadedMember && regionChunksLoaded(level, regionChunks.getOrDefault(encounterId, Set.of()))) {
                UUID stepId = UUID.randomUUID();
                engine.authorizeEnvironmentStep(encounterId, stepId);
                activeEnvironmentSteps.put(level, stepId);
                activeEnvironmentEncounters.put(level, encounterId);
                lastEnvironmentEncounter.put(level, encounterId);
                break;
            }
        }
    }

    private void auditRestoredEncounters(ServerLevel level) {
        // A frozen/debug world has no environment scheduling opportunity. Candidate saves
        // must also be audited once simulation resumes: recoveryPending blocks their inputs.
        if (!level.tickRateManager().runsNormally() || level.isDebug()) return;
        String dimension = level.dimension().identifier().toString();
        for (UUID encounterId : Set.copyOf(recoveryPending)) {
            if (!engine.encounterIds().contains(encounterId)) {
                recoveryPending.remove(encounterId);
                continue;
            }
            CombatEngine.StateView state = engine.stateView(encounterId);
            if (!dimension.equals(state.region().dimension())) continue;
            boolean regionLoaded = regionChunksLoaded(level, regionChunks.getOrDefault(encounterId, Set.of()));
            boolean allMembersPresent = state.members().keySet().stream()
                .allMatch(id -> level.getEntity(id) instanceof LivingEntity member && member.isAlive());
            String behaviorRecovery = engine.exportSnapshot().encounters().stream().filter(e -> e.id().equals(encounterId))
                .flatMap(e -> e.pending().values().stream()).filter(e -> e.intent() != null)
                .map(e -> TacticalCapabilities.recoveryReason(e.intent())).distinct().collect(java.util.stream.Collectors.joining("; "));
            int unknown = engine.failRestoredWork(encounterId, cumulativeServerTicks,
                behaviorRecovery.isEmpty() ? "restart evidence could not prove previous world effect" : behaviorRecovery);
            if (!regionLoaded || !allMembersPresent) {
                UUID owner = state.members().keySet().iterator().next();
                engine.recordRestoredUnknown(encounterId, UUID.randomUUID(), owner, owner, null,
                    cumulativeServerTicks, "restart cannot confirm loaded region and living members; control released without loading chunks");
                unknown++;
            }
            for (var entry : Map.copyOf(pendingProjectileAttacks).entrySet()) {
                UUID projectileId = entry.getKey();
                UUID domain = projectileSimulationDomains.get(projectileId);
                if (domain == null || !engine.canonicalEncounterId(domain).equals(encounterId)) continue;
                ProjectileOrigin origin = projectileOrigins.get(projectileId);
                {
                    UUID owner = origin == null || origin.ownerId() == null
                        ? projectileId : origin.ownerId();
                    engine.recordRestoredUnknown(encounterId,
                        arrowOperationId(projectileId, entry.getValue().targetId()), owner,
                        projectileId, entry.getValue().targetId(), cumulativeServerTicks,
                        "pending collision effects cannot be reconciled after restart; not replayed");
                    pendingProjectileAttacks.remove(projectileId);
                    unknown++;
                }
            }
            if (!allMembersPresent || unknown > 0) {
                LOGGER.warn("Restored encounter {} released: membersPresent={} unknownOperations={}",
                    encounterId, allMembersPresent, unknown);
                stop(encounterId);
            } else {
                Math.addExact(projectionRevisions.getOrDefault(encounterId, 0L), 1);
                if (SERVERS.get(server) == this) scheduledTicks.captureAll(level);
                recoveryPending.remove(encounterId);
                sync(engine.stateView(encounterId));
            }
            recoveryPending.remove(encounterId);
        }
    }

    private void scheduleMerge(UUID seed) {
        queueMerge(engine.planMerge(seed));
    }

    private void queueMerge(CombatEngine.MergePlan plan) {
        pendingMerges.entrySet().removeIf(entry -> {
            boolean replaced = entry.getValue().encounters().stream().anyMatch(plan.encounters()::contains);
            if (replaced) mergeFailures.remove(entry.getKey());
            return replaced;
        });
        pendingMerges.put(plan.primary(), plan);
        mergeFailures.remove(plan.primary());
    }

    private void discardMergesContaining(UUID encounterId) {
        pendingMerges.entrySet().removeIf(entry -> {
            boolean removed = entry.getValue().encounters().contains(encounterId);
            if (removed) mergeFailures.remove(entry.getKey());
            return removed;
        });
    }

    /** Runs before any scheduled queue or world callback sees the newly merged projection. */
    private void attemptPendingMerges(ServerLevel level) {
        for (CombatEngine.MergePlan plan : List.copyOf(pendingMerges.values())) {
            if (!plan.equals(pendingMerges.get(plan.primary()))) continue;
            if (!engine.encounterIds().containsAll(plan.encounters())) {
                pendingMerges.remove(plan.primary());
                mergeFailures.remove(plan.primary());
                continue;
            }
            CombatEngine.StateView primary = engine.stateView(plan.primary());
            if (!primary.region().dimension().equals(level.dimension().identifier().toString())) continue;
            if (engine.mergeWindowExpired(plan)) {
                try {
                    pendingMerges.put(plan.primary(), engine.renewMergePlan(plan));
                } catch (IllegalStateException changed) {
                    scheduleMerge(plan.primary());
                }
                continue;
            }
            if (primary.phase() != EncounterPhase.ENVIRONMENT
                || primary.round() != plan.targetEnvironmentRound()) continue;
            CombatEngine.MergePlan working = plan;
            boolean ruleCommitted = false;
            boolean projectionCommitted = false;
            try {
                EncounterRegion sampled = null;
                boolean closureStable = false;
                for (int expansion = 0; expansion < engine.encounterIds().size(); expansion++) {
                    sampled = sampleMergedRegion(level, working);
                    CombatEngine.MergePlan discovered = engine.planMergeProjected(working.primary(), sampled);
                    if (discovered.encounters().equals(working.encounters())) {
                        closureStable = true;
                        break;
                    }
                    if (!discovered.encounters().containsAll(working.encounters()))
                        throw new IllegalStateException("resampling no longer covers a captured merge participant");
                    queueMerge(discovered);
                    LOGGER.info("Expanded pending merge from {} to {} encounters after region resampling",
                        working.encounters().size(), discovered.encounters().size());
                    working = discovered;
                }
                if (!closureStable) throw new IllegalStateException("merge overlap closure did not stabilize");
                CombatEngine.StateView ready = engine.stateView(working.primary());
                if (ready.phase() != EncounterPhase.ENVIRONMENT
                    || ready.round() != working.targetEnvironmentRound()
                    || engine.mergeWindowExpired(working)) continue;
                for (PlayerMoveLease lease : List.copyOf(playerMoves.values()))
                    if (working.encounters().contains(lease.encounterId)) closePlayerMove(lease);
                for (MobMoveLease lease : List.copyOf(mobMoves.values()))
                    if (working.encounters().contains(lease.encounterId))
                        closeMobMove(lease, "movement settled before merge boundary");
                Set<Long> chunks = exactRegionChunks(sampled);
                if (!regionChunksLoaded(level, chunks)
                    || !scheduledTicks.canRebindMerge(working.encounters(), sampled))
                    throw new IllegalStateException("merged region or held tick chunks are not loaded and ticking");
                long mergedProjectionSequence = Math.addExact(nextSessionSequence, 1);
                OperationRecord.Result result = engine.commitMerge(working, sampled,
                    UUID.randomUUID(), cumulativeServerTicks);
                ruleCommitted = true;
                // The logical primary keeps its identity and result prefix, but every client
                // must see this projection after all source snapshots and tombstones.
                sessionSequences.put(working.primary(), mergedProjectionSequence);
                nextSessionSequence = mergedProjectionSequence;
                for (UUID id : working.encounters()) {
                    if (id.equals(working.primary())) continue;
                    CombatEngine.View closed = engine.closedView(id);
                    regions.remove(id);
                    capturedSettings.remove(id);
                    regionChunks.remove(id);
                    subscriptions.end(id);
                    gameTestAttackRandom.remove(id);
                    if (closed != null) for (UUID member : closed.members().keySet())
                        clear(member, id, closed.version());
                }
                regions.put(working.primary(), sampled);

                regionChunks.put(working.primary(), chunks);
                scheduledTicks.rebindMerge(working.encounters(), working.primary(), sampled);
                projectionCommitted = true;
                pendingMerges.remove(working.primary());
                mergeFailures.remove(working.primary());
                LOGGER.info("Merged {} encounters into {} at environment round {}: {}",
                    working.encounters().size(), working.primary(), working.targetEnvironmentRound(),
                    result.snapshot().operationId());
                sync(engine.stateView(working.primary()));
                syncBodyStateTransitions();
            } catch (RuntimeException failure) {
                if (ruleCommitted && !projectionCommitted)
                    throw new IllegalStateException("merge rules committed but world projection did not; "
                        + "refusing to advance a world tick with conflicting owners", failure);
                if (projectionCommitted) {
                    LOGGER.error("Merge {} committed, but client synchronization failed",
                        working.primary(), failure);
                    continue;
                }
                String reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                if (!reason.equals(mergeFailures.put(working.primary(), reason)))
                    LOGGER.warn("Encounter merge {} is waiting: {}", working.primary(), reason);
            }
        }
    }

    private EncounterRegion sampleMergedRegion(ServerLevel level, CombatEngine.MergePlan plan) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        long version = 0;
        for (UUID id : plan.encounters()) {
            EncounterRegion region = engine.stateView(id).region();
            var area = region.discovery();
            minX = Math.min(minX, area.minX()); minY = Math.min(minY, area.minY());
            minZ = Math.min(minZ, area.minZ());
            maxX = Math.max(maxX, area.maxX()); maxY = Math.max(maxY, area.maxY());
            maxZ = Math.max(maxZ, area.maxZ());
            version = Math.max(version, region.version());
        }
        var discovery = new EncounterRegion.Discovery(minX, minY, minZ, maxX, maxY, maxZ);
        Entity anchor = null;
        for (UUID id : plan.encounters()) {
            for (UUID member : engine.stateView(id).members().keySet()) {
                Entity found = level.getEntity(member);
                if (found != null && discovery.contains(pointOf(found))) { anchor = found; break; }
            }
            if (anchor != null) break;
        }
        if (anchor == null) throw new IllegalStateException("no loaded anchor in merged discovery");
        CombatPersistenceEnvelope.CapturedSettings settings = settingsFor(plan.primary());
        return MinecraftRegionSampler.capture(level, anchor, discovery, settings.regionRadius(),
            Math.addExact(version, 1), settings.maxSampledChunks(), settings.maxAnchors());
    }

    public void beforeBlockQueue(ServerLevel level) {
        requireThread();
        if (!candidateRegionChunks(level).isEmpty())
            scheduledTicks.beforeBlockQueue(level, activeEnvironmentEncounters.get(level));
    }

    public void beforeFluidQueue(ServerLevel level) {
        requireThread();
        if (!candidateRegionChunks(level).isEmpty())
            scheduledTicks.beforeFluidQueue(level, activeEnvironmentEncounters.get(level));
    }

    /** Reached only after the normal ServerLevel tick returned through all world stages. */
    public void afterLevelTick(ServerLevel level) {
        requireThread();
        UUID stepId = activeEnvironmentSteps.get(level);
        UUID encounterId = activeEnvironmentEncounters.get(level);
        if (stepId == null) return;
        if (encounterId != null && engine.encounterIds().contains(encounterId)) {
            engine.commitEnvironmentStep(encounterId, stepId);
            activeEnvironmentSteps.remove(level);
            activeEnvironmentEncounters.remove(level);
            sync(engine.stateView(encounterId));
            syncBodyStateTransitions();
        } else {
            activeEnvironmentSteps.remove(level);
            activeEnvironmentEncounters.remove(level);
        }
    }

    /** Called by the outer vanilla world-tick exception boundary, before it reports the crash. */
    public void abortLevelTick(ServerLevel level, Throwable failure) {
        requireThread();
        UUID stepId = activeEnvironmentSteps.remove(level);
        UUID encounterId = activeEnvironmentEncounters.remove(level);
        if (stepId == null || encounterId == null || !engine.encounterIds().contains(encounterId)) return;
        LOGGER.error("Environment step {} in encounter {} has an unknown partial world outcome; "
            + "releasing its control instead of retrying", stepId, encounterId, failure);
        engine.failEnvironmentStep(encounterId, stepId, cumulativeServerTicks,
            "world tick aborted after partial execution: " + failure.getClass().getSimpleName());
        stop(encounterId);
    }

    public void beforeChunkUnload(ServerLevel level, net.minecraft.world.level.ChunkPos chunk) {
        requireThread();
        scheduledTicks.releaseChunk(level, chunk);
    }

    public net.minecraft.world.level.chunk.ChunkAccess.PackedTicks savedTicksForChunk(
        ServerLevel level, net.minecraft.world.level.ChunkPos chunk,
        net.minecraft.world.level.chunk.ChunkAccess.PackedTicks vanilla) {
        requireThread();
        List<net.minecraft.world.ticks.SavedTick<net.minecraft.world.level.block.Block>> blocks =
            new ArrayList<>(vanilla.blocks());
        blocks.addAll(scheduledTicks.savedInChunk(level.getBlockTicks(), chunk));
        List<net.minecraft.world.ticks.SavedTick<net.minecraft.world.level.material.Fluid>> fluids =
            new ArrayList<>(vanilla.fluids());
        fluids.addAll(scheduledTicks.savedInChunk(level.getFluidTicks(), chunk));
        return new net.minecraft.world.level.chunk.ChunkAccess.PackedTicks(blocks, fluids);
    }

    private static boolean regionChunksLoaded(ServerLevel level, Set<Long> chunks) {
        for (long chunk : chunks) {
            net.minecraft.world.level.ChunkPos pos = net.minecraft.world.level.ChunkPos.unpack(chunk);
            if (!level.hasChunk(pos.x(), pos.z()) || !level.shouldTickBlocksAt(chunk)) return false;
        }
        return true;
    }

    /** A movement lease permits only vanilla movement packets, not body simulation or other input. */
    public boolean hasPlayerMoveLease(UUID playerId) { return playerMoves.containsKey(playerId); }
    public boolean hasMobMoveLease(UUID mobId) {
        MobMoveLease lease = mobMoves.get(mobId);
        return lease != null && activeMobLease(lease);
    }

    /** Fault injection after path inspection; the body still runs through vanilla's real tick. */
    public void underreserveNextExpensiveMobStepForGameTest(UUID mobId) {
        requireThread();
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer))
            throw new IllegalStateException("Mob reservation fault injection requires GameTestServer");
        MobMoveLease lease = mobMoves.get(mobId);
        if (lease == null || !activeMobLease(lease))
            throw new IllegalStateException("no active Mob movement lease");
        lease.underreserveNextExpensiveStepForGameTest = true;
    }

    private boolean activeMobLease(MobMoveLease lease) {
        if (mobMoves.get(lease.mobId) != lease
            || !engine.encounterIds().contains(lease.encounterId)
            || !lease.encounterId.equals(engine.encounterOf(lease.mobId))) return false;
        CombatEngine.StateView state = engine.stateView(lease.encounterId);
        return (state.phase() == EncounterPhase.ACTIVE || state.phase() == EncounterPhase.CANDIDATE)
            && lease.mobId.equals(state.current())
            && state.members().containsKey(lease.mobId)
            && engine.pendingOperation(lease.encounterId, lease.operationId) != null;
    }

    private void revokeMobLease(MobMoveLease lease) {
        if (mobMoves.remove(lease.mobId, lease)) stopMobNavigation(lease);
    }
    /** A completed vanilla knockback changed velocity; it can explain at most one movement tick. */
    public void noteKnockbackImpulse(LivingEntity target, UUID sourceOperation) {
        requireThread();
        UUID encounterId = engine.encounterOf(target.getUUID());
        if (encounterId == null) return;
        long tick = cumulativeServerTicks;
        forcedMovements.put(target.getUUID(), new ForcedMovementEvidence(
            UUID.randomUUID(), encounterId, sourceOperation, "vanilla knockback impulse", tick));
        PlayerMoveLease lease = playerMoves.get(target.getUUID());
        if (lease != null && lease.moved && lease.observedTick == tick) {
            lease.mixedTick = true;
            lease.forcedCause = forcedMovements.get(target.getUUID()).description();
        }
    }

    private ForcedMovementEvidence availableForce(UUID entityId, long tick) {
        ForcedMovementEvidence evidence = forcedMovements.get(entityId);
        return evidence != null && evidence.encounterId.equals(engine.encounterOf(entityId))
            && evidence.available(tick) ? evidence : null;
    }
    public void noteMobTick(UUID mobId) {
        MobMoveLease lease = mobMoves.get(mobId);
        if (lease != null) {
            if (activeMobLease(lease)) lease.ticked = true;
            else revokeMobLease(lease);
        }
    }

    public OperationRecord.Result beginPlayerMove(ServerPlayer actor, UUID operationId,
                                                                 long expectedVersion) {
        requireThread();
        Objects.requireNonNull(operationId);
        UUID encounterId = engine.encounterOf(actor.getUUID());
        if (encounterId == null) throw new IllegalStateException("player is not a member");
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) {
            if (previous.snapshot().kind() != OperationRecord.Kind.MOVE
                || !previous.snapshot().owner().equals(actor.getUUID())
                || previous.snapshot().encounterVersion() != expectedVersion)
                throw new IllegalStateException("operation ID payload conflict");
            return previous;
        }
        OperationRecord.Snapshot pending = engine.pendingOperation(encounterId, operationId);
        if (pending != null) {
            if (pending.kind() != OperationRecord.Kind.MOVE
                || !pending.owner().equals(actor.getUUID())
                || pending.encounterVersion() != expectedVersion)
                throw new IllegalStateException("operation ID payload conflict");
            return null;
        }
        if (moveEndReceipts.containsKey(operationId))
            throw new IllegalStateException("operation ID payload conflict");
        CombatEngine.StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion || (state.phase() != EncounterPhase.ACTIVE && state.phase() != EncounterPhase.CANDIDATE)
            || !actor.getUUID().equals(state.current()) || unsupportedPlayerMovement(actor)
            || !state.region().dimension().equals(actor.level().dimension().identifier().toString())
            || !state.region().containsPoint(pointOf(actor).x(), pointOf(actor).y(), pointOf(actor).z())
            || state.members().get(actor.getUUID()).movementTicks() < 1
            || playerMoves.containsKey(actor.getUUID()))
            throw new IllegalStateException("player movement is not authorized in this phase");
        BlockPos pos = actor.blockPosition();
        OperationRecord.Snapshot snapshot = operationSnapshot(operationId, null,
            encounterId, actor.getUUID(), actor.getUUID(), null, cumulativeServerTicks,
            expectedVersion, new GridCell(pos.getX(), pos.getY(), pos.getZ()), null,
            OperationRecord.Kind.MOVE);
        if (!engine.beginOperation(snapshot)) throw new IllegalStateException("movement operation rejected");
        playerMoves.put(actor.getUUID(), new PlayerMoveLease(encounterId, operationId, actor.getUUID()));
        sync(engine.stateView(encounterId));
        syncBodyStateTransitions();
        return null; // Accepted operation has no terminal result until observed movement is settled.
    }

    /** Check the proposed packet before vanilla movement can consume a lease budget. */
    public boolean preparePlayerMovePacket(ServerPlayer actor, ServerboundMovePlayerPacket packet,
                                           boolean correctionPending) {
        requireThread();
        PlayerMoveLease lease = playerMoves.get(actor.getUUID());
        if (lease == null || !packet.hasPosition()) return true;
        if (unsupportedPlayerMovement(actor)) {
            if (lease.moved && lease.observedTick == cumulativeServerTicks)
                lease.mixedTick = true;
            closePlayerMove(lease, "special movement mode has no active movement permit");
            return correctionPending || actor.isChangingDimension();
        }
        long tick = cumulativeServerTicks;
        if (lease.moved && lease.observedTick != tick) settleMoveTick(lease);
        if (playerMoves.get(actor.getUUID()) != lease) return false;
        Vec3 before = actor.position();
        double wantedX = packet.getX(before.x), wantedY = packet.getY(before.y), wantedZ = packet.getZ(before.z);
        if (!Double.isFinite(wantedX) || !Double.isFinite(wantedY) || !Double.isFinite(wantedZ))
            return true; // Vanilla rejects invalid packet coordinates before any world movement.
        if (tacticalActions != null && !tacticalActions.allowsMove(actor, new Vec3(wantedX, wantedY, wantedZ))) return false;
        boolean horizontal = Math.abs(wantedX - before.x) > 1.0E-5
                || Math.abs(wantedZ - before.z) > 1.0E-5;
        boolean rising = actor.onGround() && wantedY > before.y + 0.05;
        boolean verticalWater = (actor.isInWater() || actor.getFluidHeight(FluidTags.WATER) > 0)
            && Math.abs(wantedY - before.y) > 1.0E-5;
        if (correctionPending || actor.isChangingDimension()
            || availableForce(actor.getUUID(), tick) != null
            || (!horizontal && !rising && !verticalWater)) return true;
        AABB proposedBody = actor.getBoundingBox().move(
            wantedX - before.x, wantedY - before.y, wantedZ - before.z);
        int proposedWater = waterInLoadedBody(actor.level(), proposedBody);
        if (proposedWater < 0) {
            closePlayerMove(lease);
            return false;
        }
        boolean water = actor.isInWater() || proposedWater > 0;
        int required = Math.max(lease.baseCost, water ? 2 : 1)
            + (lease.jumped || rising ? 1 : 0);
        if (engine.stateView(lease.encounterId).members().get(lease.playerId).movementTicks() >= required)
            return true;
        closePlayerMove(lease);
        return false;
    }

    public void observePlayerMovePacket(ServerPlayer actor, Vec3 before, boolean hasPosition,
                                        boolean groundedBefore, boolean waterBefore,
                                        boolean correctionPending) {
        requireThread();
        PlayerMoveLease lease = playerMoves.get(actor.getUUID());
        if (lease != null && unsupportedPlayerMovement(actor)) {
            if (lease.moved && lease.observedTick == cumulativeServerTicks)
                lease.mixedTick = true;
            closePlayerMove(lease, "special movement mode has no active movement permit");
            return;
        }
        if (lease == null || !hasPosition || before.distanceToSqr(actor.position()) <= 1.0E-10) return;
        long tick = cumulativeServerTicks;
        if (lease.moved && lease.observedTick != tick) settleMoveTick(lease);
        if (playerMoves.get(actor.getUUID()) != lease) return;
        ForcedMovementEvidence force = availableForce(actor.getUUID(), tick);
        if (correctionPending || force != null || actor.isChangingDimension()) {
            // A position packet cannot separate intentional input from this forced move.
            // If an active packet also moved the player this tick, neither contribution is charged.
            if (lease.observedTick != tick) lease.mixedTick = false;
            lease.observedTick = tick;
            lease.mixedTick = true;
            lease.moved = true;
            lease.forcedCause = force == null
                ? correctionPending ? "vanilla pending teleport correction" : "dimension correction"
                : force.description();
            if (force != null) force.classifiedTick = tick;
            return;
        }
        boolean horizontalDisplacement = Math.abs(actor.getX() - before.x) > 1.0E-5
            || Math.abs(actor.getZ() - before.z) > 1.0E-5;
        boolean jumped = groundedBefore && !actor.onGround()
            && actor.getY() > before.y + 0.05;
        boolean verticalWater = (waterBefore || actor.isInWater())
            && Math.abs(actor.getY() - before.y) > 1.0E-5;
        if (!jumped && !verticalWater && !horizontalDisplacement) return;
        if (lease.observedTick != tick) lease.mixedTick = false;
        if (lease.observedTick != tick) lease.forcedCause = null;
        lease.observedTick = tick;
        lease.moved = true;
        lease.presentationHorizontal += actor.position().subtract(before).horizontalDistance();
        lease.baseCost = Math.max(lease.baseCost, waterBefore || actor.isInWater()
            || waterInLoadedBody(actor.level(), actor.getBoundingBox()) != 0 ? 2 : 1);
        lease.jumped |= jumped;
        CombatEngine.StateView state = engine.stateView(lease.encounterId);
        Vec3 center = actor.getBoundingBox().getCenter();
        if (!state.region().containsPoint(center.x, center.y, center.z)) {
            settleMoveTick(lease);
            if (playerMoves.get(actor.getUUID()) == lease) finishPlayerMove(actor);
        }
    }

    private static boolean unsupportedPlayerMovement(ServerPlayer player) {
        return player.isPassenger() || player.isFallFlying() || player.isAutoSpinAttack()
            || player.getAbilities().flying;
    }

    private static int observedMoveCost(boolean water, boolean jumped) {
        return (water ? 2 : 1) + (jumped ? 1 : 0);
    }

    /** Settle only server ticks that actually contained accepted vanilla displacement. */
    public void finishMovementTicks() {
        requireThread();
        for (PlayerMoveLease lease : Set.copyOf(playerMoves.values())) {
            ServerPlayer actor = server.getPlayerList().getPlayer(lease.playerId);
            if (actor != null && unsupportedPlayerMovement(actor)) {
                // This runs once at the end of the server tick, after Level advanced gameTime.
                // An unsettled move here belongs to this tick even if the level clock changed.
                if (lease.moved) lease.mixedTick = true;
                closePlayerMove(lease, "special movement mode has no active movement permit");
                continue;
            }
            if (lease.moved) settleMoveTick(lease);
        }
        if (tacticalActions != null) tacticalActions.tick();
        long tick = cumulativeServerTicks;
        forcedMovements.entrySet().removeIf(entry -> entry.getValue().sourceTick + 1 < tick);
    }

    /** -1 means the body cannot be checked without loading terrain; never authorize it. */
    private static int waterInLoadedBody(ServerLevel level, AABB body) {
        BlockPos min = BlockPos.containing(body.minX, body.minY, body.minZ);
        BlockPos max = BlockPos.containing(body.maxX - 1.0E-7,
            body.maxY - 1.0E-7, body.maxZ - 1.0E-7);
        long width = (long) max.getX() - min.getX() + 1;
        long height = (long) max.getY() - min.getY() + 1;
        long depth = (long) max.getZ() - min.getZ() + 1;
        if (width < 1 || width > 4 || height < 1 || height > 4
            || depth < 1 || depth > 4 || width * height * depth > 32) return -1;
        boolean water = false;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) return -1;
            water |= level.getFluidState(pos).is(FluidTags.WATER);
        }
        return water ? 1 : 0;
    }

    /** Advances only a leased Mob body. Decision and navigation remain separate. */
    public void advanceMobTurns() {
        requireThread();
        for (UUID encounterId : engine.encounterIds()) {
            if (recoveryPending.contains(encounterId) || tacticalActions().runningIn(encounterId)) continue;
            CombatEngine.StateView state = engine.stateView(encounterId);
            if (state.phase() == EncounterPhase.ENVIRONMENT || state.current() == null) continue;
            Entity found = resolve(state.current());
            if (!(found instanceof Mob mob)) continue;
            Vec3 mobCenter = mob.getBoundingBox().getCenter();
            if (!state.region().containsPoint(mobCenter.x, mobCenter.y, mobCenter.z)) {
                MobMoveLease outsideLease = mobMoves.get(mob.getUUID());
                if (outsideLease != null) closeMobMove(outsideLease, "member moved outside fixed region");
                endCurrentTurn(encounterId, UUID.randomUUID(),
                    engine.stateView(encounterId).version());
                continue;
            }
            MobMoveLease lease = mobMoves.get(mob.getUUID());
            if (lease != null) {
                if (!activeMobLease(lease)) {
                    revokeMobLease(lease);
                    continue;
                }
                if (lease.budgetBlocked) {
                    if (engine.pendingOperation(lease.encounterId, lease.operationId) != null)
                        engine.publish(lease.encounterId, lease.operationId, lease.step,
                            lease.spent == 0 ? OperationRecord.Outcome.REJECTED : OperationRecord.Outcome.COMPLETED,
                            "remaining budget cannot authorize the next navigation tick", 0, 0, true);
                    revokeMobLease(lease);
                    endCurrentTurn(encounterId, UUID.randomUUID(),
                        engine.stateView(encounterId).version());
                } else observeMobLease(mob, lease);
            } else {
                ServerPlayer target = mob instanceof Zombie hostile && state.phase() == EncounterPhase.ACTIVE
                    ? selectZombieTarget(hostile, state) : null;
                if (target != null && inMeleeReach(mob, target)) {
                    takeZombieTurn(encounterId, target.getUUID());
                } else {
                    beginMobMove(mob, state);
                }
            }
        }
    }

    private void beginMobMove(Mob mob, CombatEngine.StateView state) {
        if (state.members().get(mob.getUUID()).movementTicks() < 1) {
            endCurrentTurn(state.id(), UUID.randomUUID(), state.version());
            return;
        }
        UUID operationId = UUID.randomUUID();
        BlockPos pos = mob.blockPosition();
        OperationRecord.Snapshot snapshot = operationSnapshot(operationId, null,
            state.id(), mob.getUUID(), mob.getUUID(), null, cumulativeServerTicks,
            state.version(), new GridCell(pos.getX(), pos.getY(), pos.getZ()), null,
            OperationRecord.Kind.MOVE);
        if (!engine.beginOperation(snapshot)) return;
        MobMoveLease lease = new MobMoveLease(state.id(), operationId, mob);
        mobMoves.put(mob.getUUID(), lease);
        ServerPlayer target = mob instanceof Zombie hostile && state.phase() == EncounterPhase.ACTIVE
            ? selectZombieTarget(hostile, state) : null;
        if (target != null) {
            // Existing hostile navigation first observes the leased body, then plans its path.
            mob.getNavigation().stop();
        } else if (startMobPath(mob, state, null)) {
            lease.pathStarted = true;
            lease.ownedPath = mob.getNavigation().getPath();
        } else {
            engine.publish(state.id(), operationId, 0, OperationRecord.Outcome.REJECTED,
                "no supported vanilla navigation proposal for this Mob turn", 0, 0, true);
            revokeMobLease(lease);
            endCurrentTurn(state.id(), UUID.randomUUID(), engine.stateView(state.id()).version());
        }
        sync(engine.stateView(state.id()));
    }

    private boolean startMobPath(Mob mob, CombatEngine.StateView state, ServerPlayer target) {
        if (target != null) return mob.getNavigation().moveTo(target, 1.0);
        if (mob.isNoAi() || mob.isPassenger() || mob.hasControllingPassenger()) return false;
        if (!(mob instanceof net.minecraft.world.entity.PathfinderMob walker)
            || !(mob.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation)) return false;
        if (!mob.getNavigation().isDone()) return true;
        // RandomStrollGoal's vanilla proposal bounds, not a movement budget or a forced displacement.
        BlockPos origin = mob.blockPosition();
        if (!mob.level().hasChunksAt(origin.offset(-10, -7, -10), origin.offset(10, 7, 10))) return false;
        Vec3 proposal = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(walker, 10, 7);
        return proposal != null && state.region().containsPoint(proposal.x, proposal.y, proposal.z)
            && mob.getNavigation().moveTo(proposal.x, proposal.y, proposal.z, 1.0);
    }

    private void observeMobLease(Mob mob, MobMoveLease lease) {
        if (!activeMobLease(lease)) {
            revokeMobLease(lease);
            return;
        }
        if (!lease.ticked) return;
        lease.ticked = false;
        CombatEngine.StateView state = engine.stateView(lease.encounterId);
        ServerPlayer target = mob instanceof Zombie hostile && state.phase() == EncounterPhase.ACTIVE
                    ? selectZombieTarget(hostile, state) : null;
        if (!lease.pathStarted) {
            MobObservation observation = settleMobDisplacement(mob, lease, state, false);
            if (observation == MobObservation.TERMINAL_UNKNOWN
                || observation == MobObservation.TERMINAL_COMPLETED) {
                finishMobTurnAfterLease(lease, mob, target, observation);
                return;
            }
            if (startMobPath(mob, state, target)) {
                lease.pathStarted = true;
                lease.ownedPath = mob.getNavigation().getPath();
                lease.stalled = 0;
                return;
            }
            if (++lease.stalled < 5) return;
            engine.publish(lease.encounterId, lease.operationId, lease.step,
                OperationRecord.Outcome.REJECTED, "vanilla navigation found no path", 0, 0, true);
            revokeMobLease(lease);
            endCurrentTurn(lease.encounterId, UUID.randomUUID(),
                engine.stateView(lease.encounterId).version());
            return;
        }
        boolean pathChanged = lease.ownedPath != mob.getNavigation().getPath();
        MobObservation observation = settleMobDisplacement(mob, lease, state, !pathChanged);
        if (observation == MobObservation.TERMINAL_UNKNOWN
            || observation == MobObservation.TERMINAL_COMPLETED) {
            finishMobTurnAfterLease(lease, mob, target, observation);
            return;
        }
        if (observation == MobObservation.NO_DISPLACEMENT) lease.stalled++;
        boolean done = pathChanged || target != null && inMeleeReach(mob, target) || mob.getNavigation().isDone()
            || lease.stalled >= 5 || engine.stateView(lease.encounterId).members()
                .get(mob.getUUID()).movementTicks() == 0;
        if (!done) return;
        if (engine.pendingOperation(lease.encounterId, lease.operationId) != null)
            engine.publish(lease.encounterId, lease.operationId, lease.step,
                lease.spent == 0 ? OperationRecord.Outcome.REJECTED : OperationRecord.Outcome.COMPLETED,
                "Mob navigation finished", 0, 0, true);
        sync(engine.stateView(lease.encounterId));
        revokeMobLease(lease);
        CombatEngine.StateView after = engine.stateView(lease.encounterId);
        if (target != null && inMeleeReach(mob, target)) takeZombieTurn(lease.encounterId, target.getUUID());
        else endCurrentTurn(lease.encounterId, UUID.randomUUID(), after.version());
    }

    private void finishMobTurnAfterLease(MobMoveLease lease, Mob mob,
                                         ServerPlayer target, MobObservation observation) {
        if (!engine.encounterIds().contains(lease.encounterId)) return;
        CombatEngine.StateView after = engine.stateView(lease.encounterId);
        if (!lease.mobId.equals(after.current())) return;
        if (observation == MobObservation.TERMINAL_COMPLETED
            && target != null && inMeleeReach(mob, target))
            takeZombieTurn(lease.encounterId, target.getUUID());
        else endCurrentTurn(lease.encounterId, UUID.randomUUID(), after.version());
    }

    private MobObservation settleMobDisplacement(Mob mob, MobMoveLease lease,
                                                  CombatEngine.StateView state, boolean navigationOwned) {
        Vec3 before = lease.previous;
        boolean groundedBefore = lease.previousGrounded;
        boolean waterBefore = lease.previousWater;
        boolean displaced = before.distanceToSqr(mob.position()) > 1.0E-10;
        lease.previous = mob.position();
        lease.previousGrounded = mob.onGround();
        lease.previousWater = mob.isInWater();
        if (!engine.encounterIds().contains(lease.encounterId)
            || engine.pendingOperation(lease.encounterId, lease.operationId) == null) {
            revokeMobLease(lease);
            return MobObservation.TERMINAL_UNKNOWN;
        }
        if (!displaced) return MobObservation.NO_DISPLACEMENT;
        lease.stalled = 0;
        boolean horizontal = Math.abs(before.x - mob.getX()) > 1.0E-5
            || Math.abs(before.z - mob.getZ()) > 1.0E-5;
        boolean jumped = groundedBefore && !mob.onGround() && mob.getY() > before.y + 0.05;
        ForcedMovementEvidence force = availableForce(mob.getUUID(), cumulativeServerTicks);
        boolean active = navigationOwned && force == null
            && (horizontal || jumped || ((waterBefore || mob.isInWater()) && mob.isJumping()));
        if (force != null) force.classifiedTick = cumulativeServerTicks;
        int cost = active ? observedMoveCost(waterBefore || mob.isInWater(), jumped) : 0;
        if (cost >= 2 && lease.underreserveNextExpensiveStepForGameTest
            && lease.authorizedCost < cost)
            lease.underreserveNextExpensiveStepForGameTest = false;
        int remaining = state.members().get(mob.getUUID()).movementTicks();
        if (cost > remaining || navigationOwned && cost > lease.authorizedCost) {
            try {
                engine.publish(lease.encounterId, lease.operationId, lease.step,
                    OperationRecord.Outcome.UNKNOWN,
                    "navigation displaced beyond preauthorized movement mode: delta="
                        + mob.position().subtract(before) + " reserved=" + lease.authorizedCost
                        + " observed=" + cost + " remaining=" + remaining
                        + " pathOwned=" + navigationOwned,
                    0, 0, true);
                lease.step++;
            } finally {
                revokeMobLease(lease);
            }
            sync(engine.stateView(lease.encounterId));
            return MobObservation.TERMINAL_UNKNOWN;
        }
        boolean terminal = cost > 0 && remaining == cost;
        engine.publish(lease.encounterId, lease.operationId, lease.step,
            terminal ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.ACCEPTED,
            active ? "vanilla Mob navigation displacement, preauthorized=" + lease.authorizedCost
                : "passive Mob displacement" + (force == null ? "" : " " + force.description()),
            cost, 0, terminal);
        entityProjections.movement(mob, new PresentationState.Movement(lease.operationId, lease.step,
            ++presentationMovementSequence, active ? PresentationState.Motion.ACTIVE
                : force != null ? PresentationState.Motion.FORCED : PresentationState.Motion.PASSIVE,
            mob.position().subtract(before).horizontalDistance()));
        lease.step++;
        lease.spent += cost;
        if (terminal) revokeMobLease(lease);
        sync(engine.stateView(lease.encounterId));
        return terminal ? MobObservation.TERMINAL_COMPLETED : MobObservation.DISPLACED;
    }

    private static boolean inMeleeReach(LivingEntity actor, LivingEntity target) {
        BlockPos from = actor.blockPosition();
        BlockPos to = target.blockPosition();
        return from.distManhattan(to) <= 3
            && new GridCell(from.getX(), from.getY(), from.getZ()).chebyshev(
                new GridCell(to.getX(), to.getY(), to.getZ())) <= CombatRules.MELEE_RANGE
            && actor.hasLineOfSight(target);
    }

    private Entity resolve(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private void stopMobNavigation(MobMoveLease lease) {
        Entity entity = resolve(lease.mobId);
        if (entity instanceof Mob mob && lease.ownedPath != null
            && mob.getNavigation().getPath() == lease.ownedPath) mob.getNavigation().stop();
    }

    private void closeMobMove(MobMoveLease lease, String reason) {
        if (mobMoves.get(lease.mobId) != lease) return;
        Entity entity = resolve(lease.mobId);
        if (lease.ticked && entity instanceof Mob mob
            && engine.encounterIds().contains(lease.encounterId)) {
            lease.ticked = false;
            settleMobDisplacement(mob, lease, engine.stateView(lease.encounterId),
                lease.pathStarted && lease.ownedPath == mob.getNavigation().getPath());
        }
        if (mobMoves.get(lease.mobId) != lease) return;
        if (engine.encounterIds().contains(lease.encounterId)
            && engine.pendingOperation(lease.encounterId, lease.operationId) != null)
            engine.publish(lease.encounterId, lease.operationId, lease.step,
                lease.spent == 0 ? OperationRecord.Outcome.REJECTED : OperationRecord.Outcome.COMPLETED,
                reason, 0, 0, true);
        revokeMobLease(lease);
    }

    /** Space controls leases; membership survives crossing the fixed region boundary. */
    public void reconcileMemberLocations() {
        requireThread();
        for (UUID encounterId : engine.encounterIds()) {
            CombatEngine.StateView state = engine.stateView(encounterId);
            for (UUID memberId : state.members().keySet()) {
                Entity member = null;
                for (ServerLevel level : server.getAllLevels()) {
                    member = level.getEntity(memberId);
                    if (member != null) break;
                }
                if (member == null) continue;
                Vec3 center = member.getBoundingBox().getCenter();
                if (!state.region().dimension().equals(member.level().dimension().identifier().toString())) {
                    leave(memberId);
                    continue;
                }
                if (!state.region().containsPoint(center.x, center.y, center.z)) {
                    PlayerMoveLease playerLease = playerMoves.get(memberId);
                    if (playerLease != null) closePlayerMove(playerLease);
                    MobMoveLease mobLease = mobMoves.get(memberId);
                    if (mobLease != null) closeMobMove(mobLease, "member moved outside fixed region");
                }
            }
        }
    }

    public OperationRecord.Result finishPlayerMove(ServerPlayer actor) {
        requireThread();
        PlayerMoveLease lease = playerMoves.get(actor.getUUID());
        if (lease == null) throw new IllegalStateException("no active player movement operation");
        return closePlayerMove(lease);
    }

    /** Settle the observed tick before releasing the operation, including lifecycle exits. */
    private OperationRecord.Result closePlayerMove(PlayerMoveLease lease) {
        return closePlayerMove(lease, "movement finished");
    }

    private OperationRecord.Result closePlayerMove(PlayerMoveLease lease, String reason) {
        OperationRecord.Result terminal = engine.resultFor(lease.encounterId, lease.operationId);
        if (terminal != null) { playerMoves.remove(lease.playerId, lease); return terminal; }
        if (lease.moved) settleMoveTick(lease);
        if (playerMoves.get(lease.playerId) != lease)
            return engine.resultFor(lease.encounterId, lease.operationId);
        OperationRecord.Result result = engine.publish(lease.encounterId, lease.operationId,
            lease.nextStep, lease.observedSteps == 0 ? OperationRecord.Outcome.REJECTED
                : OperationRecord.Outcome.COMPLETED,
            reason + "; observed displacement ticks=" + lease.observedSteps
                + "; movement cost=" + lease.spentTicks,
            0, 0, true);
        playerMoves.remove(lease.playerId);
        sync(engine.stateView(lease.encounterId));
        syncBodyStateTransitions();
        return result;
    }

    public OperationRecord.Result finishPlayerMove(ServerPlayer actor, UUID operationId,
                                                               long expectedVersion) {
        requireThread();
        PlayerMoveLease lease = playerMoves.get(actor.getUUID());
        if (lease != null) {
            if (!lease.operationId.equals(operationId))
                throw new IllegalStateException("movement operation ID payload conflict");
            OperationRecord.Snapshot pending = engine.pendingOperation(lease.encounterId, operationId);
            if (pending == null || expectedVersion < pending.encounterVersion()
                || expectedVersion > engine.stateView(lease.encounterId).version())
                throw new IllegalStateException("stale encounter version");
            MoveEndReceipt prior = moveEndReceipts.get(operationId);
            if (prior != null && (!prior.owner().equals(actor.getUUID())
                || !prior.encounterId().equals(lease.encounterId)
                || prior.expectedVersion() != expectedVersion))
                throw new IllegalStateException("operation ID payload conflict");
            OperationRecord.Result result = finishPlayerMove(actor);
            validateMoveEndReceipt(actor.getUUID(), lease.encounterId, operationId,
                expectedVersion, result);
            return result;
        }
        UUID encounterId = engine.encounterOf(actor.getUUID());
        if (encounterId != null) {
            OperationRecord.Result prior = engine.resultFor(encounterId, operationId);
            if (prior != null && prior.snapshot().kind() == OperationRecord.Kind.MOVE
                && prior.snapshot().owner().equals(actor.getUUID())
                && prior.snapshot().encounterId().equals(encounterId)) {
                validateMoveEndReceipt(actor.getUUID(), encounterId, operationId,
                    expectedVersion, prior);
                return prior;
            }
        }
        throw new IllegalStateException("no matching movement operation");
    }

    private void validateMoveEndReceipt(UUID owner, UUID encounterId, UUID operationId,
                                        long expectedVersion, OperationRecord.Result result) {
        MoveEndReceipt receipt = moveEndReceipts.get(operationId);
        if (receipt != null) {
            if (!receipt.owner().equals(owner) || !receipt.encounterId().equals(encounterId)
                || receipt.expectedVersion() != expectedVersion)
                throw new IllegalStateException("operation ID payload conflict");
            return;
        }
        // An automatically completed MOVE can receive its first END request after the
        // terminal publication. Its observed version must lie in that operation's window.
        if (expectedVersion < result.snapshot().encounterVersion()
            || expectedVersion > result.publishedVersion())
            throw new IllegalStateException("operation ID payload conflict");
        moveEndReceipts.put(operationId, new MoveEndReceipt(owner, encounterId, expectedVersion));
    }

    private void settleMoveTick(PlayerMoveLease lease) {
        if (!lease.moved || playerMoves.get(lease.playerId) != lease) return;
        CombatEngine.StateView state = engine.stateView(lease.encounterId);
        int remaining = state.members().get(lease.playerId).movementTicks();
        int cost = lease.mixedTick ? 0 : lease.baseCost + (lease.jumped ? 1 : 0);
        if (cost > remaining) throw new IllegalStateException("observed movement exceeded authorized budget");
        boolean terminal = remaining == cost;
        engine.publish(lease.encounterId, lease.operationId, lease.nextStep,
            terminal ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.ACCEPTED,
            (lease.mixedTick ? "mixed active/forced displacement, contribution unproven: "
                + lease.forcedCause + " at server tick "
                : "vanilla player displacement at server tick ") + lease.observedTick,
            cost, 0, terminal);
        var presentationActor = server.getPlayerList().getPlayer(lease.playerId);
        if (presentationActor != null) entityProjections.movement(presentationActor, new PresentationState.Movement(
            lease.operationId, lease.nextStep, ++presentationMovementSequence,
            lease.mixedTick ? PresentationState.Motion.MIXED : PresentationState.Motion.ACTIVE,
            lease.mixedTick ? 0 : lease.presentationHorizontal));
        lease.presentationHorizontal = 0;
        lease.nextStep++;
        lease.observedSteps++;
        lease.spentTicks += cost;
        lease.moved = false;
        lease.mixedTick = false;
        lease.forcedCause = null;
        lease.baseCost = 0;
        lease.jumped = false;
        if (terminal) playerMoves.remove(lease.playerId);
        sync(engine.stateView(lease.encounterId));
        if (terminal) syncBodyStateTransitions();
    }

    public void tickConsent() { requireThread(); consent.tick(); }
    public void consentDisconnected(UUID player) { requireThread(); consent.disconnected(player); }
    public void respondToConsent(ServerPlayer player, CombatNetwork.ConsentReply reply) {
        requireThread(); consent.reply(player, reply);
    }
    public void resyncConsent(ServerPlayer player, UUID request) { requireThread(); consent.resend(player, request); }
    public ConsentWindow.View consentView(UUID request) { requireThread(); return consent.view(request); }

    public record StartResult(StartDisposition status, CombatEngine.StateView state, String reason) {
        public StartResult {
            Objects.requireNonNull(status); Objects.requireNonNull(reason);
            if (status == StartDisposition.NONE || (status == StartDisposition.STARTED) != (state != null))
                throw new IllegalArgumentException("invalid start result");
        }
    }

    public boolean mayOrganizeInventory(ServerPlayer player) {
        requireThread();
        UUID id = engine.encounterOf(player.getUUID());
        if (closing || recoveryFailure || id == null || recoveryPending.contains(id)
            || worldEffectDepth != 0 || server.tickRateManager().isFrozen()
            || MinecraftCombatRuntime.isLocalMember(server, player.getUUID())) return false;
        CombatEngine.StateView state = engine.stateView(id);
        return player.getUUID().equals(state.current())
            && (state.phase() == EncounterPhase.ACTIVE || state.phase() == EncounterPhase.CANDIDATE)
            && isEntityInsidePausedRegion(player);
    }

    public StartResult requestStart(ServerPlayer player, UUID operationId) {
        requireThread();
        rejectExitIdReuse(operationId);
        StartRequestReceipt prior = startReceipts.get(operationId);
        if (prior != null) {
            if (!prior.owner().equals(player.getUUID())) throw new IllegalStateException("start operation ID payload conflict");
            UUID canonical = engine.canonicalEncounterId(prior.encounterId());
            if (!engine.encounterIds().contains(canonical))
                return new StartResult(StartDisposition.CLOSED, null, "start request already completed and its encounter ended");
            sync(engine.stateView(canonical));
            return new StartResult(StartDisposition.STARTED, engine.stateView(canonical), "started");
        }
        if (consent.view(operationId) == null && (recoveryFailure || isMember(player.getUUID())
            || MinecraftCombatRuntime.isLocalMember(server, player.getUUID())))
            throw new IllegalStateException("player is unavailable for a new consent request");
        rejectExitIdReuse(operationId);
        consent.request(player, operationId);
        StartRequestReceipt started = startReceipts.get(operationId);
        if (started != null) return new StartResult(StartDisposition.STARTED,
            engine.stateView(engine.canonicalEncounterId(started.encounterId())), "started");
        ConsentWindow.View window = consent.view(operationId);
        return new StartResult(window != null && window.status() == ConsentWindow.Status.WAITING
            ? StartDisposition.WAITING : StartDisposition.FAILED, null, consent.reason(operationId));
    }

    EncounterRegion sampleConsentRegion(ServerPlayer player, EncounterRegion.Discovery captured) {
        Vec3 center = player.getBoundingBox().getCenter();
        var discovery = captured == null ? new EncounterRegion.Discovery(
            center.x - config.discoveryHorizontal(), center.y - config.discoveryVertical(), center.z - config.discoveryHorizontal(),
            center.x + config.discoveryHorizontal(), center.y + config.discoveryVertical(), center.z + config.discoveryHorizontal()) : captured;
        return MinecraftRegionSampler.capture(player.level(), player, discovery, config.regionRadius(), 1,
            config.maxSampledChunks(), config.maxAnchors());
    }

    void commitConsentedEncounter(ServerPlayer anchor, EncounterRegion region, Set<UUID> players, Map<UUID, UUID> requestOwners) {
        CombatEngine.StateView state = beginEncounter(anchor, region, players);
        requestOwners.forEach((id, owner) -> startReceipts.put(id, new StartRequestReceipt(owner, state.id())));
        lastPersistedRevision = null;
        try {
            scheduledTicks.captureAll(anchor.level());
        } catch (RuntimeException failure) {
            // Rules and receipts are already committed. Keep the encounter noninteractive
            // until the next pre-world audit retries platform preparation; never cancel START.
            recoveryPending.add(state.id());
            LOGGER.error("Encounter {} committed but queue capture needs recovery", state.id(), failure);
        }
        sync(state);
    }

    private CombatEngine.StateView beginEncounter(ServerPlayer initiator,
                                                 EncounterRegion region, Set<UUID> approvedPlayers) {
        requireThread();
        if (recoveryFailure) throw new IllegalStateException("combat save needs recovery before new encounters");
        if (MinecraftCombatRuntime.isLocalMember(server, initiator.getUUID()))
            throw new IllegalStateException("player already controlled by /dndturn local");
        if (isMember(initiator.getUUID())) throw new IllegalStateException("player already in an encounter");
        ServerLevel level = initiator.level();
        EncounterRegion.Discovery discovery = region.discovery();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!approvedPlayers.contains(player.getUUID()) && player.level() == level
                && region.containsPoint(pointOf(player).x(), pointOf(player).y(), pointOf(player).z()))
                throw new IllegalStateException("another player is inside the proposed encounter region");
            if (player.level() == level && region.containsPoint(pointOf(player).x(), pointOf(player).y(), pointOf(player).z())
                && MinecraftCombatRuntime.isLocalMember(server, player.getUUID()))
                throw new IllegalStateException("region contains a player controlled by /dndturn local");
        }
        AABB search = new AABB(discovery.minX(), discovery.minY(), discovery.minZ(),
            discovery.maxX(), discovery.maxY(), discovery.maxZ());
        Set<UUID> members = new HashSet<>();
        for (UUID playerId : approvedPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.level() != level
                || MinecraftCombatRuntime.isLocalMember(server, playerId))
                throw new IllegalStateException("consenting player became unavailable");
            var point = pointOf(player);
            if (!region.containsPoint(point.x(), point.y(), point.z()))
                throw new IllegalStateException("consenting player left the proposed region");
            // Already participating players consent to the new proposal but remain in their
            // authoritative encounter until the shared safe merge boundary commits.
            if (!isMember(playerId)) members.add(playerId);
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, search,
                mob -> mob.isAlive() && discovery.contains(pointOf(mob)))) {
            if (engine.encounterOf(mob.getUUID()) == null
                && !MinecraftCombatRuntime.isLocalMember(server, mob.getUUID())) members.add(mob.getUUID());
        }
        UUID encounterId = UUID.randomUUID();
        long nextSequence = Math.addExact(nextSessionSequence, 1);
        var captured = CombatPersistenceEnvelope.CapturedSettings.from(config);
        Set<Long> chunks = exactRegionChunks(region);
        engine.beginCandidate(encounterId, region, members,
            config.movementTicks(), config.environmentTicks());
        capturedSettings.put(encounterId, captured);
        sessionSequences.put(encounterId, nextSequence);
        nextSessionSequence = nextSequence;
        regions.put(encounterId, region);
        regionChunks.put(encounterId, chunks);
        // Merge planning runs at beforeLevelTick. Queue capture follows the receipt commit.
        return engine.stateView(encounterId);
    }

    public CombatEngine.StateView state(UUID encounterId) {
        requireThread();
        return engine.stateView(encounterId);
    }

    public long sessionProjectionSequence(UUID encounterId) {
        requireThread();
        Long sequence = sessionSequences.get(encounterId);
        if (sequence == null) throw new IllegalArgumentException("unknown session projection");
        return sequence;
    }

    public CombatEngine.ResultPage results(UUID encounterId, int fromIndex, int limit) {
        requireThread();
        return engine.resultPage(encounterId, fromIndex, limit);
    }

    /** Resolve a terminal retry before checking current membership; this grants no new execution. */
    public OperationRecord.Result completedRetry(ServerPlayer player, UUID requestedEncounter,
                                                  UUID operationId, OperationRecord.Kind kind,
                                                  long expectedVersion, UUID targetId,
                                                  boolean checkVersion) {
        requireThread();
        Objects.requireNonNull(requestedEncounter);
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(kind);
        OperationRecord.Result result = engine.resultFor(requestedEncounter, operationId);
        if (result == null) return null;
        OperationRecord.Snapshot snapshot = result.snapshot();
        if (snapshot.kind() != kind || !snapshot.owner().equals(player.getUUID())
            || !snapshot.encounterId().equals(requestedEncounter)
            || !Objects.equals(snapshot.target(), targetId)
            || checkVersion && snapshot.encounterVersion() != expectedVersion)
            throw new IllegalStateException("operation ID payload conflict");
        if (!checkVersion) {
            if (kind != OperationRecord.Kind.MOVE)
                throw new IllegalStateException("only MOVE_END uses the later version contract");
            validateMoveEndReceipt(player.getUUID(), requestedEncounter, operationId,
                expectedVersion, result);
        }
        return result;
    }

    /** A successful EXIT stays queryable after membership and body control are released. */
    public boolean completedExitRetry(ServerPlayer player, UUID encounterId,
                                               UUID operationId, long expectedVersion) {
        requireThread();
        Objects.requireNonNull(player);
        Objects.requireNonNull(encounterId);
        Objects.requireNonNull(operationId);
        ExitRequestReceipt receipt = exitReceipts.get(operationId);
        if (receipt == null) return false;
        if (!receipt.owner().equals(player.getUUID())
            || !receipt.encounterId().equals(encounterId)
            || receipt.expectedVersion() != expectedVersion)
            throw new IllegalStateException("operation ID payload conflict");
        return true;
    }

    public void rejectExitIdReuse(UUID operationId) {
        requireThread();
        if (exitReceipts.containsKey(operationId))
            throw new IllegalStateException("operation ID payload conflict");
    }

    public void exitEncounter(ServerPlayer player, UUID encounterId, UUID operationId,
                              long expectedVersion) {
        requireThread();
        if (completedExitRetry(player, encounterId, operationId, expectedVersion)) return;
        if (!encounterId.equals(engine.encounterOf(player.getUUID()))
)
            throw new IllegalStateException("combat session is no longer active");
        if (engine.stateView(encounterId).version() != expectedVersion)
            throw new IllegalStateException("stale encounter version");
        EncounterRegion region = engine.stateView(encounterId).region();
        Vec3 center = player.getBoundingBox().getCenter();
        if (region.dimension().equals(player.level().dimension().identifier().toString())
            && region.containsPoint(center.x, center.y, center.z) && isTargetedByMob(player))
            throw new IllegalStateException("a Mob is targeting you; leave the encounter region before exiting");
        if (startReceipts.containsKey(operationId)
            || engine.resultFor(encounterId, operationId) != null
            || engine.pendingOperation(encounterId, operationId) != null)
            throw new IllegalStateException("operation ID payload conflict");
        boolean otherPlayer = engine.stateView(encounterId).members().keySet().stream()
            .anyMatch(id -> !id.equals(player.getUUID()) && server.getPlayerList().getPlayer(id) != null);
        if (!otherPlayer && hasPlayerInCombat(encounterId))
            throw new IllegalStateException("cannot end turn-based mode while a player is targeted by a Mob");
        if (otherPlayer) leave(player.getUUID());
        else stop(encounterId);
        exitReceipts.put(operationId,
            new ExitRequestReceipt(player.getUUID(), encounterId, expectedVersion));
        syncBodyStateTransitions();
    }

    /** Current vanilla target semantics, including loaded non-member mobs; never loads chunks. */
    private boolean isTargetedByMob(ServerPlayer player) {
        for (Entity entity : player.level().getAllEntities()) {
            if (entity instanceof Mob mob && mob.isAlive() && mob.getTarget() == player) return true;
        }
        return false;
    }

    private boolean hasPlayerInCombat(UUID encounterId) {
        return engine.stateView(encounterId).members().keySet().stream().map(this::resolve)
            .filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast)
            .anyMatch(this::isTargetedByMob);
    }

    private boolean exitedRegion(Entity entity, UUID encounterId) {
        return entity instanceof ServerPlayer && engine.encounterOf(entity.getUUID()) == null
            && exitReceipts.values().stream().anyMatch(receipt -> receipt.owner().equals(entity.getUUID())
                && engine.canonicalEncounterId(receipt.encounterId()).equals(encounterId));
    }

    public void resendResults(ServerPlayer player, UUID encounterId, int fromIndex) {
        requireThread();
        subscriptions.resync(player, encounterId, fromIndex);
    }

    public void resyncMember(ServerPlayer player, UUID requestedEncounter) {
        requireThread();
        UUID encounterId = engine.encounterOf(player.getUUID());
        if (requestedEncounter != null && !requestedEncounter.equals(encounterId)) {
            CombatEngine.View closed = engine.closedView(requestedEncounter);
            if (closed != null && closed.members().containsKey(player.getUUID()))
                clear(player, requestedEncounter, closed.version());
        }
        if (encounterId != null) sync(engine.stateView(encounterId));
    }

    public void flushResultPages() {
        requireThread();
        subscriptions.flush();
    }

    public OperationRecord.Result endTurn(UUID encounterId, ServerPlayer actor,
                                          UUID operationId, long expectedVersion) {
        requireThread();
        return endTurnAs(encounterId, actor.getUUID(), actor, operationId, expectedVersion);
    }

    /** Prototype DASH spends one action and grants one captured movement budget immediately. */
    public OperationRecord.Result dash(ServerPlayer actor, UUID operationId,
                                                   long expectedVersion) {
        requireThread();
        Objects.requireNonNull(operationId);
        UUID encounterId = engine.encounterOf(actor.getUUID());
        if (encounterId == null)
            throw new IllegalStateException("DASH requires active membership");
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) {
            if (previous.snapshot().kind() != OperationRecord.Kind.DASH
                || !previous.snapshot().owner().equals(actor.getUUID())
                || previous.snapshot().encounterVersion() != expectedVersion)
                throw new IllegalStateException("operation ID payload conflict");
            return previous;
        }
        CombatEngine.StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion || (state.phase() != EncounterPhase.ACTIVE && state.phase() != EncounterPhase.CANDIDATE)
            || !actor.getUUID().equals(state.current()) || unsupportedPlayerMovement(actor)
            || playerMoves.containsKey(actor.getUUID())
            || !state.region().dimension().equals(actor.level().dimension().identifier().toString())
            || !state.region().containsPoint(pointOf(actor).x(), pointOf(actor).y(), pointOf(actor).z()))
            throw new IllegalStateException("DASH is not authorized in this phase or position");
        Math.addExact(expectedVersion, 2);
        BlockPos pos = actor.blockPosition();
        OperationRecord.Snapshot snapshot = operationSnapshot(operationId, null,
            encounterId, actor.getUUID(), actor.getUUID(), null, cumulativeServerTicks,
            expectedVersion, new GridCell(pos.getX(), pos.getY(), pos.getZ()), null,
            OperationRecord.Kind.DASH);
        if (!engine.beginOperation(snapshot)) throw new IllegalStateException("DASH action is unavailable");
        OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
            OperationRecord.Outcome.COMPLETED,
            "DASH granted " + engine.movementTicksPerTurn(encounterId) + " movement ticks", 0, 0, true);
        sync(engine.stateView(encounterId));
        return result;
    }

    /** Prototype defensive actions use the same server-owned action budget as ATTACK and DASH. */
    public OperationRecord.Result defensiveAction(ServerPlayer actor, UUID operationId,
                                                               long expectedVersion,
                                                               OperationRecord.Kind kind) {
        requireThread();
        Objects.requireNonNull(operationId);
        if (kind != OperationRecord.Kind.DODGE && kind != OperationRecord.Kind.DISENGAGE)
            throw new IllegalArgumentException("unsupported defensive action");
        UUID encounterId = engine.encounterOf(actor.getUUID());
        if (encounterId == null)
            throw new IllegalStateException(kind + " requires active membership");
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) {
            if (previous.snapshot().kind() != kind
                || !previous.snapshot().owner().equals(actor.getUUID())
                || previous.snapshot().target() != null
                || previous.snapshot().encounterVersion() != expectedVersion)
                throw new IllegalStateException("operation ID payload conflict");
            return previous;
        }
        CombatEngine.StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion || state.phase() != EncounterPhase.ACTIVE
            || !actor.getUUID().equals(state.current()) || playerMoves.containsKey(actor.getUUID())
            || !state.region().dimension().equals(actor.level().dimension().identifier().toString())
            || !state.region().containsPoint(pointOf(actor).x(), pointOf(actor).y(), pointOf(actor).z()))
            throw new IllegalStateException(kind + " is not authorized in this phase or position");
        Math.addExact(expectedVersion, 2);
        BlockPos pos = actor.blockPosition();
        OperationRecord.Snapshot snapshot = operationSnapshot(operationId, null,
            encounterId, actor.getUUID(), actor.getUUID(), null, cumulativeServerTicks,
            expectedVersion, new GridCell(pos.getX(), pos.getY(), pos.getZ()), null, kind);
        if (!engine.beginOperation(snapshot)) throw new IllegalStateException(kind + " action is unavailable");
        OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
            OperationRecord.Outcome.COMPLETED,
            kind == OperationRecord.Kind.DODGE ? "dodge until next own turn"
                : "disengage until end of this turn", 0, 0, true);
        sync(engine.stateView(encounterId));
        return result;
    }

    /** Explicit administrator capability: end the current member's turn, including a Mob turn. */
    public OperationRecord.Result endCurrentTurn(UUID encounterId,
                                                                UUID operationId, long expectedVersion) {
        requireThread();
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) return matchingEndTurn(previous, previous.snapshot().owner(), expectedVersion);
        CombatEngine.StateView state = engine.stateView(encounterId);
        if (state.current() == null) throw new IllegalStateException("no current member in " + state.phase());
        Entity actor = null;
        for (ServerLevel level : server.getAllLevels()) {
            actor = level.getEntity(state.current());
            if (actor != null) break;
        }
        if (actor == null) throw new IllegalStateException("current member is not loaded");
        return endTurnAs(encounterId, state.current(), actor, operationId, expectedVersion);
    }

    private OperationRecord.Result endTurnAs(UUID encounterId, UUID owner, Entity actor,
                                             UUID operationId, long expectedVersion) {
        Objects.requireNonNull(operationId);
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) return matchingEndTurn(previous, owner, expectedVersion);
        CombatEngine.StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion) throw new IllegalStateException("stale encounter version");
        if (tacticalActions != null && tacticalActions.running(owner))
            throw new IllegalStateException("cancel the active plan before ending the turn");
        var pos = actor.blockPosition();
        OperationRecord.Snapshot snapshot = operationSnapshot(operationId, null,
            encounterId, owner, owner, null, cumulativeServerTicks,
            state.version(), new GridCell(pos.getX(), pos.getY(), pos.getZ()), null,
            OperationRecord.Kind.END_TURN);
        if (!engine.beginOperation(snapshot)) throw new IllegalStateException("end turn not authorized");
        if (state.phase() == EncounterPhase.CANDIDATE) {
            // A candidate turn boundary reviews every directed Mob target, including player turns.
            for (UUID sourceId : state.members().keySet()) {
                Entity found = resolve(sourceId);
                if (!(found instanceof Mob mob)) continue;
                LivingEntity target = mob.getTarget();
                for (UUID memberId : state.members().keySet()) {
                    if (!memberId.equals(sourceId)) engine.setHostile(encounterId, sourceId, memberId,
                        target != null && target.getUUID().equals(memberId));
                }
            }
        }
        OperationRecord.Result result = engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
            "turn ended", 0, 0, true);
        sync(engine.stateView(encounterId));
        return result;
    }

    private static OperationRecord.Result matchingEndTurn(OperationRecord.Result previous, UUID owner,
                                                           long expectedVersion) {
        if (previous.snapshot().kind() != OperationRecord.Kind.END_TURN
            || !previous.snapshot().owner().equals(owner)
            || previous.snapshot().encounterVersion() != expectedVersion)
            throw new IllegalStateException("operation ID payload conflict");
        return previous;
    }

    /** Shared selection contract for planning, navigation completion and execution. */
    private boolean eligibleZombieTarget(Zombie actor, ServerPlayer target, CombatEngine.StateView state) {
        if (target == null || actor.isRemoved() || actor.isDeadOrDying()
            || target.isRemoved() || target.isDeadOrDying() || target.level() != actor.level()
            || !state.members().containsKey(actor.getUUID()) || !state.members().containsKey(target.getUUID())
            || !state.id().equals(engine.encounterOf(target.getUUID()))
            || !(actor.level() instanceof ServerLevel level) || level.getEntity(target.getUUID()) != target
            || !state.region().dimension().equals(actor.level().dimension().identifier().toString())) return false;
        Vec3 origin = actor.getBoundingBox().getCenter();
        Vec3 destination = target.getBoundingBox().getCenter();
        return state.region().containsPoint(origin.x, origin.y, origin.z)
            && state.region().containsPoint(destination.x, destination.y, destination.z);
    }

    private ServerPlayer selectZombieTarget(Zombie actor, CombatEngine.StateView state) {
        return state.members().keySet().stream().map(id -> server.getPlayerList().getPlayer(id))
            .filter(player -> eligibleZombieTarget(actor, player, state))
            .min(Comparator.<ServerPlayer>comparingInt(player -> inMeleeReach(actor, player) ? 0 : 1)
                .thenComparingDouble(actor::distanceToSqr).thenComparing(ServerPlayer::getUUID)).orElse(null);
    }

    /** Development command selects once using the same contract as automatic turns. */
    public OperationRecord.Result takeZombieTurn(UUID encounterId) {
        requireThread();
        CombatEngine.StateView state = engine.stateView(encounterId);
        Entity found = state.current() == null ? null : resolve(state.current());
        if (!(found instanceof Zombie actor)) throw new IllegalStateException("current member is not a loaded Zombie");
        ServerPlayer target = selectZombieTarget(actor, state);
        return takeZombieTurn(encounterId, target == null ? null : target.getUUID());
    }

    /** Executes only the selected identity; a vanished/out-of-bounds target ends this choice without attacking. */
    public OperationRecord.Result takeZombieTurn(UUID encounterId, UUID selectedTarget) {
        requireThread();
        CombatEngine.StateView state = engine.stateView(encounterId);
        if (state.phase() != EncounterPhase.ACTIVE || state.current() == null)
            throw new IllegalStateException("not a Zombie action phase");
        Entity found = resolve(state.current());
        if (!(found instanceof Zombie actor)) throw new IllegalStateException("current member is not a loaded Zombie");
        ServerPlayer target = selectedTarget == null ? null : server.getPlayerList().getPlayer(selectedTarget);
        OperationRecord.Result action = eligibleZombieTarget(actor, target, state)
            && state.members().get(actor.getUUID()).action() && inMeleeReach(actor, target)
            ? attack(actor, selectedTarget, UUID.randomUUID(), state.version()) : null;
        if (engine.encounterIds().contains(encounterId)) {
            CombatEngine.StateView after = engine.stateView(encounterId);
            if (actor.getUUID().equals(after.current()))
                endCurrentTurn(encounterId, UUID.randomUUID(), after.version());
        }
        return action;
    }

    public OperationRecord.Result attack(LivingEntity actor, UUID targetId,
                                                                  UUID operationId, long expectedVersion) {
        return attack(actor, targetId, operationId, expectedVersion, null);
    }
    OperationRecord.Result attackPlan(ServerPlayer actor, UUID target, UUID operation, UUID parent) {
        worldEffectDepth++;
        try {
            var result = attack(actor, target, operation, engine.stateView(engine.encounterOf(actor.getUUID())).version(), parent);
            // The confirmed synchronous attack is the final step of this melee plan.
            engine.publish(result.snapshot().encounterId(), parent, 0, result.outcome(), result.reason(), 0, 0, true);
            return result;
        } finally {
            worldEffectDepth--;
            if (worldEffectDepth == 0) {
                confirmPendingDeaths();
                for (UUID departed : worldEffectDepth == 0 ? Set.copyOf(departuresDuringWorldEffect) : Set.<UUID>of()) {
                    departuresDuringWorldEffect.remove(departed);
                    leave(departed);
                }
            }
        }
    }
    private OperationRecord.Result attack(LivingEntity actor, UUID targetId,
                                          UUID operationId, long expectedVersion, UUID planParent) {
        requireThread();
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(operationId);
        UUID encounterId = engine.encounterOf(actor.getUUID());
        if (encounterId == null) throw new IllegalStateException("attacker is not a member");
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) {
            OperationRecord.Snapshot snapshot = previous.snapshot();
            if (snapshot.kind() != OperationRecord.Kind.ATTACK || !snapshot.owner().equals(actor.getUUID())
                || !targetId.equals(snapshot.target()) || snapshot.encounterVersion() != expectedVersion)
                throw new IllegalStateException("operation ID payload conflict");
            return previous;
        }
        CombatEngine.StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion) throw new IllegalStateException("stale encounter version");
        if (state.phase() != EncounterPhase.CANDIDATE && state.phase() != EncounterPhase.ACTIVE)
            throw new IllegalStateException("not a member attack phase");
        if (!actor.getUUID().equals(state.current()) || !state.members().containsKey(targetId))
            throw new IllegalStateException("attacker or target not authorized");
        if (!(actor.level() instanceof ServerLevel level) || actor.isDeadOrDying())
            throw new IllegalStateException("attacker is not a loaded living member");
        Entity found = level.getEntity(targetId);
        if (!(found instanceof LivingEntity target)
            || !(actor instanceof ServerPlayer && found.getType() == EntityType.ZOMBIE
                || actor instanceof Zombie && found instanceof ServerPlayer)
            || target.isDeadOrDying())
            throw new IllegalStateException("only Player/Zombie melee is supported");
        Vec3 actorCenter = actor.getBoundingBox().getCenter();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        if (!state.region().dimension().equals(level.dimension().identifier().toString())
            || !state.region().containsPoint(actorCenter.x, actorCenter.y, actorCenter.z)
            || !state.region().containsPoint(targetCenter.x, targetCenter.y, targetCenter.z)
            || !inMeleeReach(actor, target)) throw new IllegalStateException("target is outside supported melee reach");
        boolean openingAdvantage = actor instanceof ServerPlayer && target instanceof Zombie zombie
            && !engine.hasAttemptedAttack(encounterId, actor.getUUID())
            && zombie.getTarget() != actor;
        double weaponAttribute = actor instanceof ServerPlayer
            ? actor.getMainHandItem().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND)
            : actor.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (!Double.isFinite(weaponAttribute) || !Double.isFinite(toughness)
            || weaponAttribute > Integer.MAX_VALUE || toughness > Integer.MAX_VALUE)
            throw new IllegalStateException("unsupported weapon or toughness attribute");
        double weaponDamage = Math.max(0, weaponAttribute);
        int reduction = CombatRules.damageReduction(toughness);
        CombatRules.damageAfterReduction(weaponDamage, reduction, true);
        boolean holdingShield = target.getMainHandItem().is(Items.SHIELD)
            || target.getOffhandItem().is(Items.SHIELD);
        int ac = CombatRules.armorClass(target.getArmorValue(), holdingShield);
        var origin = actor.blockPosition();
        var destination = target.blockPosition();
        OperationRecord.Snapshot root = operationSnapshot(operationId, planParent,
            encounterId, actor.getUUID(), actor.getUUID(), targetId, cumulativeServerTicks, expectedVersion,
            new GridCell(origin.getX(), origin.getY(), origin.getZ()),
            new GridCell(destination.getX(), destination.getY(), destination.getZ()), OperationRecord.Kind.ATTACK);
        if (!(planParent == null ? engine.beginOperation(root) : engine.beginPlanStep(root))) throw new IllegalStateException("attack already executing or unauthorized");
        UUID permitId = null;
        UUID damageId = null;
        boolean effectStarted = false;
        try {
        // Admission above is the once-only operation boundary, including misses and zero damage.
        entityProjections.swing(actor, encounterId, operationId, net.minecraft.world.InteractionHand.MAIN_HAND);
        CombatRules.RollMode rollMode = CombatRules.mode(openingAdvantage,
            state.members().get(targetId).dodging());
        CombatRules.AttackRoll roll = CombatRules.rollAttack(
            gameTestAttackRandom.getOrDefault(encounterId, attackRandom), rollMode, ac);
        if (!roll.hit()) {
            DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
                weaponDamage, reduction, 0, false, 0, 0,
                damageEvidence(actor, state, DamageTrace.Stage.MISS, null, List.of()));
            OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
                OperationRecord.Outcome.COMPLETED, "miss: die=" + roll.die() + " AC=" + ac, 0, 0, true, trace);
            sync(engine.stateView(encounterId));
            return result;
        }
        int tacticalDamage = CombatRules.damageAfterReduction(weaponDamage, reduction, roll.critical());
        if (tacticalDamage == 0) {
            DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
                weaponDamage, reduction, 0, false, 0, 0,
                damageEvidence(actor, state, DamageTrace.Stage.ZERO_DAMAGE, null, List.of()));
            OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
                OperationRecord.Outcome.COMPLETED, "hit with zero tactical damage", 0, 0, true, trace);
            sync(engine.stateView(encounterId));
            return result;
        }
        permitId = UUID.randomUUID();
        engine.issueEffectPermit(new CombatEngine.EffectPermit(permitId, encounterId, operationId,
            actor.getUUID(), Set.of(targetId), Set.of(EncounterPhase.ACTIVE), 1,
            engine.stateView(encounterId).round()));
        damageId = UUID.randomUUID();
        OperationRecord.Snapshot child = operationSnapshot(damageId, operationId,
            encounterId, actor.getUUID(), actor.getUUID(), targetId, cumulativeServerTicks,
            engine.stateView(encounterId).version(), root.sourceCell(), root.targetCell(),
            OperationRecord.Kind.DAMAGE);
        if (!engine.beginOperation(child, permitId)) throw new IllegalStateException("damage permit rejected");
        DamageSource source = level.damageSources().source(TacticalDamageContext.DAMAGE_TYPE, actor);
        float healthBefore = target.getHealth();
        float absorptionBefore = target.getAbsorptionAmount();
        Map<EquipmentKey, EquipmentValue> equipmentBefore = equipmentSnapshot(actor, target);
        effectStarted = true;
        worldEffectDepth++;
        try {
            var observed = TacticalDamageContext.hurtObserved(level, target, source, tacticalDamage,
                settingsFor(encounterId).tacticalKnockbackEnabled(), damageId);
            boolean accepted = observed.accepted();
            if (accepted && roll.critical()) level.getChunkSource().sendToTrackingPlayersAndSelf(target,
                new net.minecraft.network.protocol.game.ClientboundAnimatePacket(target,
                    net.minecraft.network.protocol.game.ClientboundAnimatePacket.CRITICAL_HIT));
            if (accepted && actor instanceof ServerPlayer player) {
                ItemStack weapon = player.getMainHandItem();
                if (weapon.hurtEnemy(target, player)) weapon.postHurtEnemy(target, player);
            }
            float healthLoss = Math.max(0, healthBefore - target.getHealth());
            float absorptionLoss = Math.max(0, absorptionBefore - target.getAbsorptionAmount());
            OperationRecord.Outcome effectOutcome = accepted ? OperationRecord.Outcome.COMPLETED
                : healthLoss > 0 || absorptionLoss > 0 ? OperationRecord.Outcome.PARTIAL
                : OperationRecord.Outcome.REJECTED;
            DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
                weaponDamage, reduction, tacticalDamage, accepted, absorptionLoss, healthLoss,
                damageEvidence(actor, state, accepted ? DamageTrace.Stage.VANILLA_ACCEPTED
                    : DamageTrace.Stage.VANILLA_REJECTED, observed,
                    equipmentChanges(equipmentBefore, equipmentSnapshot(actor, target))));
            engine.publish(encounterId, damageId, 0, effectOutcome,
                "vanilla hurtServer=" + accepted + " absorptionLoss=" + absorptionLoss
                    + " healthLoss=" + healthLoss, 0, healthLoss, true);
            return engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
                "attack hit: die=" + roll.die() + " effect=" + effectOutcome, 0, healthLoss, true, trace);
        } catch (RuntimeException error) {
            if (engine.resultFor(encounterId, damageId) == null)
                engine.publish(encounterId, damageId, 0, OperationRecord.Outcome.UNKNOWN,
                    "world damage outcome unknown: " + error.getClass().getSimpleName(), 0, 0, true);
            if (engine.resultFor(encounterId, operationId) == null)
                return engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.UNKNOWN,
                    "world damage outcome unknown", 0, 0, true);
            return engine.resultFor(encounterId, operationId);
        } finally {
            worldEffectDepth--;
            confirmPendingDeaths();
            for (UUID departed : worldEffectDepth == 0 ? Set.copyOf(departuresDuringWorldEffect) : Set.<UUID>of()) {
                departuresDuringWorldEffect.remove(departed);
                leave(departed);
            }
            if (engine.encounterIds().contains(encounterId)) sync(engine.stateView(encounterId));
        }
        } catch (RuntimeException error) {
            OperationRecord.Result known = engine.resultFor(encounterId, operationId);
            if (known != null) return known;
            if (!engine.encounterIds().contains(encounterId)) throw error;
            OperationRecord.Outcome outcome = effectStarted
                ? OperationRecord.Outcome.UNKNOWN : OperationRecord.Outcome.REJECTED;
            if (damageId != null && engine.pendingOperation(encounterId, damageId) != null)
                engine.publish(encounterId, damageId, 0, outcome,
                    effectStarted ? "damage effect outcome unknown" : "damage preparation failed", 0, 0, true);
            if (permitId != null && !effectStarted) engine.revokeEffectPermit(encounterId, permitId);
            if (engine.pendingOperation(encounterId, operationId) != null) {
                OperationRecord.Result failed = engine.publish(encounterId, operationId, 0, outcome,
                    (effectStarted ? "attack effect outcome unknown: " : "attack preparation failed: ")
                        + error.getClass().getSimpleName(), 0, 0, true);
                sync(engine.stateView(encounterId));
                return failed;
            }
            throw error;
        }
    }

    DamageTrace rangedTrace(ServerPlayer player, UUID targetId, UUID operation, boolean opening) {
        var state = engine.stateView(engine.encounterOf(player.getUUID()));
        LivingEntity target = (LivingEntity) player.level().getEntity(targetId);
        int ac = CombatRules.armorClass(target.getArmorValue(), target.getMainHandItem().is(Items.SHIELD) || target.getOffhandItem().is(Items.SHIELD));
        var mode = CombatRules.mode(opening, state.members().get(targetId).dodging());
        var roll = CombatRules.rollAttack(gameTestAttackRandom.getOrDefault(state.id(), attackRandom), mode, ac);
        int reduction = CombatRules.damageReduction(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        // The launch intent captures the roll; actual projectile base damage is captured at insertion.
        double base = 0;
        return attackTrace(operation, targetId, mode, roll, ac, base, reduction,
            roll.hit() ? CombatRules.damageAfterReduction(base, reduction, roll.critical()) : 0, false, 0, 0, null);
    }

    private static DamageTrace attackTrace(UUID operationId, UUID targetId,
                                           CombatRules.RollMode mode, CombatRules.AttackRoll roll,
                                           int armorClass, double weaponDamage, int reduction,
                                           int tacticalDamage, boolean accepted,
                                           float absorptionLoss, float healthLoss,
                                           DamageTrace.DamageEvidence evidence) {
        return new DamageTrace(operationId, targetId, mode, roll.first(), roll.second(),
            roll.die(), roll.total(), armorClass, roll.hit(), roll.critical(), weaponDamage,
            reduction, tacticalDamage, accepted, absorptionLoss, healthLoss, evidence);
    }

    private DamageTrace.DamageEvidence damageEvidence(Entity actor, CombatEngine.StateView state,
                                                     DamageTrace.Stage stage,
                                                     TacticalDamageContext.HurtObservation observed,
                                                     List<DamageTrace.EquipmentChange> equipmentChanges) {
        ProjectileOrigin origin = projectileOrigins.get(actor.getUUID());
        return new DamageTrace.DamageEvidence(actor.getUUID(), CombatRules.RULES_REVISION,
            state.region().version(), settingsFor(state.id()).tacticalKnockbackEnabled(), stage,
            observed == null ? 0 : observed.shieldBlockedContribution(),
            observed != null && observed.shieldWearCalled(),
            observed != null && observed.knockbackEventObserved(),
            observed != null && observed.knockbackMoved(), equipmentChanges,
            origin == null ? actor.getUUID() : origin.ownerId(),
            origin == null ? state.id() : origin.sourceEncounterId(),
            origin == null ? null : origin.rootOperationId());
    }

    private record EquipmentKey(UUID ownerId, String slot) {}
    private record EquipmentValue(String item, int count, int damage) {}

    private static Map<EquipmentKey, EquipmentValue> equipmentSnapshot(LivingEntity actor,
                                                                        LivingEntity target) {
        Map<EquipmentKey, EquipmentValue> values = new LinkedHashMap<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND))
            values.put(new EquipmentKey(target.getUUID(), slot.getName()),
                equipmentValue(target.getItemBySlot(slot)));
        if (actor != null) values.put(new EquipmentKey(actor.getUUID(), EquipmentSlot.MAINHAND.getName()),
            equipmentValue(actor.getMainHandItem()));
        return values;
    }

    private static EquipmentValue equipmentValue(ItemStack stack) {
        return new EquipmentValue(stack.isEmpty() ? "minecraft:air"
            : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
            stack.getCount(), stack.getDamageValue());
    }

    private static List<DamageTrace.EquipmentChange> equipmentChanges(
        Map<EquipmentKey, EquipmentValue> before, Map<EquipmentKey, EquipmentValue> after) {
        List<DamageTrace.EquipmentChange> changes = new ArrayList<>();
        for (var entry : before.entrySet()) {
            EquipmentValue oldValue = entry.getValue();
            EquipmentValue newValue = after.get(entry.getKey());
            if (newValue == null) throw new IllegalStateException("equipment slot disappeared");
            if (!oldValue.equals(newValue)) changes.add(new DamageTrace.EquipmentChange(
                entry.getKey().ownerId(), entry.getKey().slot(), oldValue.item(), newValue.item(),
                oldValue.count(), newValue.count(), oldValue.damage(), newValue.damage()));
        }
        return List.copyOf(changes);
    }

    public Set<UUID> stop(UUID encounterId) {
        requireThread();
        if (tacticalActions != null && engine.encounterIds().contains(encounterId)) for (UUID owner : engine.stateView(encounterId).members().keySet()) tacticalActions.cancel(owner, "encounter stopped");
        for (PlayerMoveLease lease : Set.copyOf(playerMoves.values())) {
            if (lease.encounterId.equals(encounterId)) closePlayerMove(lease);
        }
        for (MobMoveLease lease : List.copyOf(mobMoves.values()))
            if (lease.encounterId.equals(encounterId)) closeMobMove(lease, "encounter stopped");
        Set<UUID> released = engine.end(encounterId);
        revokeEndedProjectileDomains();
        released.forEach(forcedMovements::remove);
        regions.remove(encounterId);
        capturedSettings.remove(encounterId);
        regionChunks.remove(encounterId);
        subscriptions.end(encounterId);
        discardMergesContaining(encounterId);
        mergeFailures.remove(encounterId);
        gameTestAttackRandom.remove(encounterId);
        scheduledTicks.releaseEncounter(encounterId);
        CombatEngine.View closed = engine.closedView(encounterId);
        if (closed != null) for (UUID member : released) clear(member, encounterId, closed.version());
        syncBodyStateTransitions();
        return released;
    }

    public void leave(UUID entityId) {
        requireThread();
        if (worldEffectDepth > 0) {
            if (engine.encounterOf(entityId) != null) departuresDuringWorldEffect.add(entityId);
            return;
        }
        UUID encounterId = engine.encounterOf(entityId);
        if (encounterId == null) return;
        if (tacticalActions != null) tacticalActions.cancel(entityId, "member left");
        subscriptions.leave(encounterId, entityId);
        PlayerMoveLease playerLease = playerMoves.get(entityId);
        if (playerLease != null) closePlayerMove(playerLease);
        ServerPlayer departingPlayer = server.getPlayerList().getPlayer(entityId);
        MobMoveLease mobLease = mobMoves.get(entityId);
        if (mobLease != null) closeMobMove(mobLease, "member left after observed movement");
        engine.leave(encounterId, entityId);
        revokeEndedProjectileDomains();
        forcedMovements.remove(entityId);
        CombatEngine.View closed = engine.closedView(encounterId);
        if (closed != null) {
            regions.remove(encounterId);
            capturedSettings.remove(encounterId);
            regionChunks.remove(encounterId);
            subscriptions.end(encounterId);
                    discardMergesContaining(encounterId);
            mergeFailures.remove(encounterId);
            gameTestAttackRandom.remove(encounterId);
            scheduledTicks.releaseEncounter(encounterId);
            if (departingPlayer != null) clear(departingPlayer, encounterId, closed.version());
            for (UUID member : closed.members().keySet()) clear(member, encounterId, closed.version());
        } else {
            CombatEngine.StateView state = engine.stateView(encounterId);
            clear(entityId, encounterId, state.version());
            sync(state);
        }
        syncBodyStateTransitions();
    }

    /** Capture the shooter while it is still resolvable; reloading never overwrites old evidence. */
    public void captureArrowOrigin(AbstractArrow arrow, boolean loadedFromDisk) {
        requireThread();
        if (loadedFromDisk || !(arrow.level() instanceof ServerLevel level)) return;
        Entity owner = arrow.getOwner();
        ItemStack weapon = arrow.getWeaponItem();
        String weaponId = weapon == null || weapon.isEmpty() ? ""
            : BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
        ItemStack ammunition = arrow.getPickupItemStackOrigin();
        String ammoId = ammunition.isEmpty() ? ""
            : BuiltInRegistries.ITEM.getKey(ammunition.getItem()).toString();
        projectileOrigins.putIfAbsent(arrow.getUUID(), new ProjectileOrigin(arrow.getUUID(),
            owner == null ? null : owner.getUUID(), TacticalLaunchContext.current() == null ? null : TacticalLaunchContext.current().operation(),
            owner == null ? null : engine.encounterOf(owner.getUUID()),
            level.dimension().identifier().toString(), cumulativeServerTicks,
            ((ArrowDamageAccessor) arrow).dndturn$getBaseDamage(), weaponId, ammoId,
            owner instanceof ServerPlayer, true,
            weapon != null && weapon.getOrDefault(DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).size() > 0,
            TacticalLaunchContext.current() == null ? null : TacticalLaunchContext.current().trace()));
        if (TacticalLaunchContext.current() != null) {
            projectileSimulationDomains.put(arrow.getUUID(), TacticalLaunchContext.current().encounter());
            retainedProjectileHistory.add(arrow.getUUID());
        }
        lastPersistedRevision = null;
    }

    public void captureSnowballOrigin(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball, boolean loaded) {
        var launch = TacticalLaunchContext.current();
        if (loaded || launch == null || ball.getOwner() == null || !launch.owner().equals(ball.getOwner().getUUID())) return;
        projectileOrigins.put(ball.getUUID(), new ProjectileOrigin(ball.getUUID(), launch.owner(), launch.operation(), launch.encounter(),
            ball.level().dimension().identifier().toString(), cumulativeServerTicks, 0, "minecraft:snowball", "minecraft:snowball", true, true, false, launch.trace()));
        projectileSimulationDomains.put(ball.getUUID(), launch.encounter());
        retainedProjectileHistory.add(ball.getUUID());
        lastPersistedRevision = null;
    }
    public boolean handleSnowballImpact(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball, HitResult hit) {
        var origin = projectileOrigins.get(ball.getUUID());
        UUID domain = liveProjectileDomain(ball.getUUID());
        if (origin == null || domain == null) return isEntityInsidePausedRegion(ball);
        if (!domain.equals(activeEnvironmentEncounters.get(ball.level()))) return true;
        UUID target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity().getUUID() : null;
        UUID operation = arrowOperationId(ball.getUUID(), target);
        if (engine.resultFor(domain, operation) != null) { ball.discard(); return true; }
        var state = engine.stateView(domain);
        var selected = origin.launchTrace();
        Entity contacted = target == null ? null : ball.level().getEntity(target);
        if (target == null) {
            engine.recordCausalProjectileContact(domain, operation, origin.ownerId(), ball.getUUID(), null,
                cumulativeServerTicks, OperationRecord.Outcome.COMPLETED, "snowball hit block");
        } else if (selected == null || !selected.targetId().equals(target)
            || contacted == null || contacted.getType() != EntityType.ZOMBIE
            || !domain.equals(engine.encounterOf(target))) {
            engine.rejectCausalProjectileEffect(domain, operation, origin.ownerId(), ball.getUUID(), target,
                cumulativeServerTicks, "snowball contact outside selected supported target");
        } else {
            var snapshot = operationSnapshot(operation, null, domain, origin.ownerId(), ball.getUUID(), target,
                cumulativeServerTicks, state.version(), null, null, OperationRecord.Kind.ATTACK);
            if (!engine.beginCausalProjectileAttack(origin, snapshot)) return true;
            var trace = new DamageTrace(operation, target, selected.rollMode(), selected.firstDie(), selected.secondDie(),
                selected.selectedDie(), selected.rollTotal(), selected.targetArmorClass(), selected.hit(), selected.critical(),
                0, selected.toughnessReduction(), 0, false, 0, 0,
                damageEvidence(ball, state, selected.hit() ? DamageTrace.Stage.ZERO_DAMAGE : DamageTrace.Stage.MISS, null, List.of()));
            engine.publish(domain, operation, 0, OperationRecord.Outcome.COMPLETED,
                selected.hit() ? "snowball hit: vanilla base damage is zero for supported target" : "snowball missed",
                0, 0, true, trace);
        }
        ball.discard();
        sync(engine.stateView(domain));
        return true;
    }

    public void noteArrowLeave(Entity arrow) {
        requireThread();
        UUID projectileId = arrow.getUUID();
        Entity.RemovalReason reason = arrow.getRemovalReason();
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
            || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) return;
        UUID recorded = projectileSimulationDomains.remove(projectileId);
        PendingProjectileAttack waiting = pendingProjectileAttacks.remove(projectileId);
        if (recorded != null || waiting != null) lastPersistedRevision = null;
        if (waiting != null) {
            // The collision target can still belong to B while the arrow is simulated by A.
            // Removing the arrow before the merge must settle that pending collision in B.
            UUID encounterId = engine.encounterOf(waiting.targetId());
            if (encounterId != null && engine.encounterIds().contains(encounterId)) {
                ProjectileOrigin origin = projectileOrigins.get(projectileId);
                UUID owner = origin == null || origin.ownerId() == null
                    ? projectileId : origin.ownerId();
                engine.recordRestoredUnknown(encounterId,
                    arrowOperationId(projectileId, waiting.targetId()), owner,
                    projectileId, waiting.targetId(), cumulativeServerTicks,
                    "projectile removed before pending cross-domain collision settled");
                sync(engine.stateView(encounterId));
            }
        }
        // A revoked scheduler lease is not evidence that the arrow never participated.
        // Launch history is retained for causal retries and reference-aware archival.
        ProjectileOrigin origin = projectileOrigins.get(projectileId);
        if (recorded == null && !retainedProjectileHistory.contains(projectileId)
            && (origin == null || origin.sourceEncounterId() == null)) {
            if (projectileOrigins.remove(projectileId) != null) lastPersistedRevision = null;
        }
    }

    /** Scheduling is revocable; immutable launch evidence is retained independently. */
    private UUID liveProjectileDomain(UUID projectileId) {
        UUID recorded = projectileSimulationDomains.get(projectileId);
        if (recorded == null) return null;
        UUID canonical = engine.canonicalEncounterId(recorded);
        if (!engine.encounterIds().contains(canonical)) {
            projectileSimulationDomains.remove(projectileId);
            lastPersistedRevision = null;
            return null;
        }
        if (!canonical.equals(recorded)) {
            projectileSimulationDomains.put(projectileId, canonical);
            lastPersistedRevision = null;
        }
        return canonical;
    }

    private void revokeEndedProjectileDomains() {
        for (UUID projectileId : Set.copyOf(projectileSimulationDomains.keySet()))
            liveProjectileDomain(projectileId);
    }

    /** Called by the fixed-version entity gate before an arrow's collision or despawn logic. */
    private boolean arrowSimulationPaused(AbstractArrow arrow) {
        if (quarantinedProjectiles.containsKey(arrow.getUUID())) return true;
        if (!(arrow.level() instanceof ServerLevel level)) return false;
        UUID projectileId = arrow.getUUID();
        UUID domain = liveProjectileDomain(projectileId);
        if (domain == null && !regions.isEmpty()) {
            Vec3 start = arrow.position();
            Vec3 end = start.add(arrow.getDeltaMovement());
            EncounterRegion.Point from = new EncounterRegion.Point(start.x, start.y, start.z);
            EncounterRegion.Point to = new EncounterRegion.Point(end.x, end.y, end.z);
            double first = Double.POSITIVE_INFINITY;
            String dimension = level.dimension().identifier().toString();
            for (var entry : regions.entrySet()) {
                EncounterRegion region = entry.getValue();
                if (!region.dimension().equals(dimension)) continue;
                double hit = region.firstIntersectionFraction(from, to);
                if (hit < first || hit == first && domain != null
                    && entry.getKey().compareTo(domain) < 0) {
                    first = hit;
                    domain = entry.getKey();
                }
            }
            if (domain != null) {
                projectileSimulationDomains.put(projectileId, domain);
                retainedProjectileHistory.add(projectileId);
                lastPersistedRevision = null;
            }
        }
        if (domain == null) return false;
        UUID canonical = engine.canonicalEncounterId(domain);
        if (!engine.encounterIds().contains(canonical)) return false;
        if (!domain.equals(canonical)) projectileSimulationDomains.put(projectileId, canonical);
        PendingProjectileAttack waiting = pendingProjectileAttacks.get(projectileId);
        if (waiting != null) {
            UUID sourceCanonical = engine.canonicalEncounterId(waiting.sourceEncounterId());
            UUID targetDomain = engine.encounterOf(waiting.targetId());
            Entity pendingTarget = level.getEntity(waiting.targetId());
            if (targetDomain == null || !(pendingTarget instanceof LivingEntity living)
                || living.isDeadOrDying()
                || !living.getBoundingBox().inflate(0.3).contains(arrow.position())) {
                pendingProjectileAttacks.remove(projectileId);
                lastPersistedRevision = null;
                rejectArrowTransit(arrow, canonical, "pending-target-unavailable",
                    "pending arrow collision no longer has a live member at the observed impact");
                return true;
            }
            if (targetDomain != null
                && (targetDomain.equals(sourceCanonical) || !engine.encounterIds().contains(sourceCanonical))
                && targetDomain.equals(activeEnvironmentEncounters.get(level))) {
                Entity target = pendingTarget;
                if (target != null) {
                    projectileSimulationDomains.put(projectileId, targetDomain);
                    pendingProjectileAttacks.remove(projectileId);
                    lastPersistedRevision = null;
                    handleArrowImpact(arrow, new EntityHitResult(target));
                }
            }
            return true;
        }
        return !canonical.equals(activeEnvironmentEncounters.get(level));
    }

    /** Called before the fixed-version arrow path applies block/fluid effects. */
    public boolean allowArrowBlockEffects(AbstractArrow arrow, Vec3 from, Vec3 to) {
        requireThread();
        if (quarantinedProjectiles.containsKey(arrow.getUUID())) return false;
        if (!(arrow.level() instanceof ServerLevel level)) return true;
        UUID recorded = liveProjectileDomain(arrow.getUUID());
        if (recorded == null) return true;
        UUID encounterId = engine.canonicalEncounterId(recorded);
        EncounterRegion region = regions.get(encounterId);
        if (region == null) return true;
        if (!encounterId.equals(activeEnvironmentEncounters.get(level))) return false;
        if (!region.intersectsSegment(new EncounterRegion.Point(from.x, from.y, from.z),
            new EncounterRegion.Point(to.x, to.y, to.z))) return true;
        if (arrow.isOnFire()) {
            rejectArrowTransit(arrow, encounterId, "fire", "arrow fire traversal unsupported");
            return false;
        }
        // Block effects run before ProjectileImpactEvent. A closed whitelist avoids invoking
        // a stateful block callback before the rule owner can classify that callback.
        int minX = (int) Math.floor(Math.min(from.x, to.x) - 0.3);
        int maxX = (int) Math.floor(Math.max(from.x, to.x) + 0.3);
        int minY = (int) Math.floor(Math.min(from.y, to.y) - 0.3);
        int maxY = (int) Math.floor(Math.max(from.y, to.y) + 0.3);
        int minZ = (int) Math.floor(Math.min(from.z, to.z) - 0.3);
        int maxZ = (int) Math.floor(Math.max(from.z, to.z) + 0.3);
        double cells = (double) ((long) maxX - minX + 1) * ((long) maxY - minY + 1)
            * ((long) maxZ - minZ + 1);
        if (cells > 512) {
            rejectArrowTransit(arrow, encounterId, "oversized-path",
                "arrow block-effect path exceeds supported sweep");
            return false;
        }
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!region.containsBlock(x, y, z)) continue;
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()
                    || !level.getFluidState(pos).isEmpty()) {
                    rejectArrowTransit(arrow, encounterId, pos.toShortString(),
                        "arrow block/fluid traversal unsupported at " + pos.toShortString());
                    return false;
                }
            }
        return true;
    }

    private void rejectArrowTransit(AbstractArrow arrow, UUID encounterId, String key, String reason) {
        ProjectileOrigin origin = projectileOrigins.get(arrow.getUUID());
        UUID operationId = UUID.nameUUIDFromBytes(("dndturn:arrow-transit:" + arrow.getUUID()
            + ':' + key).getBytes(StandardCharsets.UTF_8));
        engine.rejectCausalProjectileEffect(encounterId, operationId,
            origin == null || origin.ownerId() == null ? arrow.getUUID() : origin.ownerId(),
            arrow.getUUID(), null, cumulativeServerTicks, reason);
        arrow.discard();
        sync(engine.stateView(encounterId));
    }

    /** Intercept before vanilla deflection, ignition, hurt, and arrow destruction. */
    public boolean handleArrowImpact(AbstractArrow arrow, HitResult hit) {
        requireThread();
        if (quarantinedProjectiles.containsKey(arrow.getUUID())) return true;
        if (!(arrow.level() instanceof ServerLevel level)) return false;
        UUID recorded = liveProjectileDomain(arrow.getUUID());
        if (recorded == null) return false;
        UUID encounterId = engine.canonicalEncounterId(recorded);
        if (!engine.encounterIds().contains(encounterId)) return false;
        if (!encounterId.equals(activeEnvironmentEncounters.get(level))) return true;
        ProjectileOrigin origin = projectileOrigins.get(arrow.getUUID());
        if (origin == null) origin = new ProjectileOrigin(arrow.getUUID(), null, null, null,
            level.dimension().identifier().toString(), cumulativeServerTicks, 0, "", "", false, false, false);
        if (!(hit instanceof EntityHitResult entityHit)
            || !(entityHit.getEntity() instanceof LivingEntity target)
            || engine.encounterOf(target.getUUID()) == null) {
            rejectArrowImpact(arrow, hit, encounterId, origin,
                "arrow impact target is outside the authorized living member set");
            return true;
        }
        UUID ownerId = origin.ownerId();
        if (origin.ownerWasPlayer() && target instanceof ServerPlayer) {
            rejectArrowImpact(arrow, hit, encounterId, origin, "PvP arrow forbidden");
            return true;
        }
        if (origin.launchTrace() != null && !origin.launchTrace().targetId().equals(target.getUUID())) {
            rejectArrowImpact(arrow, hit, encounterId, origin, "projectile collided with unselected target"); return true;
        }
        String unsupported = !origin.launchVerified() ? "launch evidence unavailable"
            : !"minecraft:arrow".equals(origin.ammoId()) ? "arrow ammunition effect unsupported"
            : origin.weaponEnchanted() ? "bow enchantment effect unsupported"
            : arrow.isOnFire() ? "arrow fire effect unsupported"
            : arrow.getPierceLevel() > 0 ? "piercing arrow effect unsupported"
            : !"".equals(origin.weaponId()) && !"minecraft:bow".equals(origin.weaponId())
                && !"minecraft:crossbow".equals(origin.weaponId()) ? "weapon effect unsupported" : null;
        if (unsupported != null) {
            rejectArrowImpact(arrow, hit, encounterId, origin, unsupported);
            return true;
        }
        Entity owner = ownerId == null ? null : resolve(ownerId);
        UUID shooterDomain = ownerId == null ? null : engine.encounterOf(ownerId);
        UUID targetDomain = engine.encounterOf(target.getUUID());
        if (!encounterId.equals(targetDomain)) {
            UUID sourceDomain = encounterId;
            queueMerge(engine.requestCausalMerge(sourceDomain, targetDomain));
            pendingProjectileAttacks.put(arrow.getUUID(),
                new PendingProjectileAttack(target.getUUID(), sourceDomain));
            lastPersistedRevision = null;
            return true;
        }
        if (shooterDomain != null && !engine.canonicalEncounterId(shooterDomain).equals(encounterId)) {
            queueMerge(engine.requestCausalMerge(shooterDomain, encounterId));
            pendingProjectileAttacks.put(arrow.getUUID(),
                new PendingProjectileAttack(target.getUUID(), shooterDomain));
            lastPersistedRevision = null;
            return true;
        }
        if (owner instanceof LivingEntity living && !living.isDeadOrDying()
            && shooterDomain == null && owner.level() == level) engine.join(encounterId, ownerId);
        UUID operationId = arrowOperationId(arrow.getUUID(), target.getUUID());
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) {
            arrow.discard();
            return true;
        }
        CombatEngine.StateView state = engine.stateView(encounterId);
        boolean openingAdvantage = owner instanceof LivingEntity livingOwner
            && target instanceof Mob mob && mob.getTarget() != livingOwner
            && state.members().containsKey(ownerId)
            && !engine.hasAttemptedAttack(encounterId, ownerId);
        OperationRecord.Snapshot root = operationSnapshot(operationId, null,
            encounterId, ownerId == null ? arrow.getUUID() : ownerId, arrow.getUUID(),
            target.getUUID(), cumulativeServerTicks, state.version(), null, null,
            OperationRecord.Kind.ATTACK);
        UUID permitId = null;
        UUID damageId = null;
        DamageTrace attempted = null;
        float healthBefore = target.getHealth();
        float absorptionBefore = target.getAbsorptionAmount();
        LivingEntity equipmentOwner = owner instanceof LivingEntity living ? living : null;
        Map<EquipmentKey, EquipmentValue> equipmentBefore = equipmentSnapshot(equipmentOwner, target);
        if (!engine.beginCausalProjectileAttack(origin, root)) return true;
        try {
            if (ownerId != null && state.members().containsKey(ownerId))
                engine.setHostile(encounterId, ownerId, target.getUUID(), true);
            int armorClass = CombatRules.armorClass(target.getArmorValue(),
                target.getMainHandItem().is(Items.SHIELD) || target.getOffhandItem().is(Items.SHIELD));
            double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            if (!Double.isFinite(toughness) || toughness > Integer.MAX_VALUE)
                throw new IllegalStateException("unsupported target toughness");
            int reduction = CombatRules.damageReduction(toughness);
            CombatRules.RollMode mode = CombatRules.mode(openingAdvantage,
                state.members().get(target.getUUID()).dodging());
            DamageTrace launchTrace = origin.launchTrace();
            if (launchTrace != null) { mode = launchTrace.rollMode(); armorClass = launchTrace.targetArmorClass(); reduction = launchTrace.toughnessReduction(); }
            CombatRules.AttackRoll roll = launchTrace == null ? CombatRules.rollAttack(
                gameTestAttackRandom.getOrDefault(encounterId, attackRandom), mode, armorClass)
                : new CombatRules.AttackRoll(launchTrace.firstDie(), launchTrace.secondDie(), launchTrace.selectedDie(),
                    launchTrace.rollTotal(), launchTrace.hit(), launchTrace.critical());
            if (!roll.hit()) {
                DamageTrace trace = attackTrace(operationId, target.getUUID(), mode, roll, armorClass,
                    origin.arrowBaseDamage(), reduction, 0, false, 0, 0,
                    damageEvidence(arrow, state, DamageTrace.Stage.MISS, null, List.of()));
                engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
                    "arrow missed: die=" + roll.die() + " AC=" + armorClass, 0, 0, true, trace);
                arrow.discard();
                sync(engine.stateView(encounterId));
                return true;
            }
            int tacticalDamage = CombatRules.damageAfterReduction(origin.arrowBaseDamage(),
                reduction, roll.critical());
            attempted = attackTrace(operationId, target.getUUID(), mode, roll, armorClass,
                origin.arrowBaseDamage(), reduction, tacticalDamage, false, 0, 0,
                damageEvidence(arrow, state, DamageTrace.Stage.UNKNOWN, null, List.of()));
            if (tacticalDamage == 0) {
                DamageTrace trace = attackTrace(operationId, target.getUUID(), mode, roll, armorClass,
                    origin.arrowBaseDamage(), reduction, 0, false, 0, 0,
                    damageEvidence(arrow, state, DamageTrace.Stage.ZERO_DAMAGE, null, List.of()));
                engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
                    "arrow hit with zero tactical damage", 0, 0, true, trace);
                arrow.discard();
                sync(engine.stateView(encounterId));
                return true;
            }
            permitId = UUID.randomUUID();
            engine.issueEffectPermit(new CombatEngine.EffectPermit(permitId, encounterId,
                operationId, arrow.getUUID(), Set.of(target.getUUID()),
                Set.of(EncounterPhase.ENVIRONMENT), 1, engine.stateView(encounterId).round()));
            damageId = UUID.randomUUID();
            OperationRecord.Snapshot child = operationSnapshot(damageId, operationId,
                encounterId, root.owner(), arrow.getUUID(), target.getUUID(), cumulativeServerTicks,
                engine.stateView(encounterId).version(), null, null, OperationRecord.Kind.DAMAGE);
            if (!engine.beginOperation(child, permitId))
                throw new IllegalStateException("arrow damage permit rejected");
            worldEffectDepth++;
            try {
                DamageSource source = level.damageSources().source(TacticalDamageContext.DAMAGE_TYPE,
                    arrow, owner);
                var observation = TacticalDamageContext.hurtObserved(level, target, source,
                    tacticalDamage, settingsFor(encounterId).tacticalKnockbackEnabled(), damageId);
                float healthLoss = Math.max(0, healthBefore - target.getHealth());
                float absorptionLoss = Math.max(0, absorptionBefore - target.getAbsorptionAmount());
                DamageTrace trace = attackTrace(operationId, target.getUUID(), mode, roll, armorClass,
                    origin.arrowBaseDamage(), reduction, tacticalDamage, observation.accepted(),
                    absorptionLoss, healthLoss,
                    damageEvidence(arrow, state, observation.accepted() ? DamageTrace.Stage.VANILLA_ACCEPTED
                        : DamageTrace.Stage.VANILLA_REJECTED, observation,
                        equipmentChanges(equipmentBefore, equipmentSnapshot(equipmentOwner, target))));
                engine.publish(encounterId, damageId, 0,
                    observation.accepted() ? OperationRecord.Outcome.COMPLETED
                        : healthLoss > 0 || absorptionLoss > 0 ? OperationRecord.Outcome.PARTIAL
                        : OperationRecord.Outcome.REJECTED,
                    "arrow hurtServer=" + observation.accepted() + " absorptionLoss=" + absorptionLoss,
                    0, healthLoss, true);
                engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
                    "arrow hit: die=" + roll.die(), 0, healthLoss, true, trace);
                arrow.discard();
                sync(engine.stateView(encounterId));
                return true;
            } finally {
                worldEffectDepth--;
            }
        } catch (RuntimeException failure) {
            quarantineProjectile(arrow.getUUID(), encounterId, target.getUUID(),
                "arrow world effect uncertain: " + failure.getClass().getSimpleName());
            float healthLoss = Math.max(0, healthBefore - target.getHealth());
            float absorptionLoss = Math.max(0, absorptionBefore - target.getAbsorptionAmount());
            DamageTrace unknown = attempted == null ? null : new DamageTrace(operationId,
                target.getUUID(), attempted.rollMode(), attempted.firstDie(), attempted.secondDie(),
                attempted.selectedDie(), attempted.rollTotal(), attempted.targetArmorClass(),
                attempted.hit(), attempted.critical(), attempted.weaponDamage(), attempted.toughnessReduction(),
                attempted.tacticalDamage(), false, absorptionLoss, healthLoss,
                damageEvidence(arrow, state, DamageTrace.Stage.UNKNOWN, null,
                    equipmentChanges(equipmentBefore, equipmentSnapshot(equipmentOwner, target))));
            if (damageId != null && engine.pendingOperation(encounterId, damageId) != null)
                engine.publish(encounterId, damageId, 0, OperationRecord.Outcome.UNKNOWN,
                    "arrow world effect uncertain: " + failure.getClass().getSimpleName(), 0, healthLoss, true);
            if (engine.pendingOperation(encounterId, operationId) != null)
                engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.UNKNOWN,
                    "arrow world effect uncertain: " + failure.getClass().getSimpleName(), 0, healthLoss, true, unknown);
            sync(engine.stateView(encounterId));
            LOGGER.error("Arrow {} tactical settlement failed", arrow.getUUID(), failure);
            return true;
        } finally {
            if (permitId != null && engine.encounterIds().contains(encounterId))
                engine.revokeEffectPermit(encounterId, permitId);
            confirmPendingDeaths();
            for (UUID departed : worldEffectDepth == 0 ? Set.copyOf(departuresDuringWorldEffect) : Set.<UUID>of()) {
                departuresDuringWorldEffect.remove(departed);
                leave(departed);
            }
        }
    }

    private static UUID arrowOperationId(UUID projectileId, UUID targetId) {
        return UUID.nameUUIDFromBytes(("dndturn:arrow:" + projectileId + ':' + targetId)
            .getBytes(StandardCharsets.UTF_8));
    }

    private void rejectArrowImpact(AbstractArrow arrow, HitResult hit, UUID encounterId,
                                   ProjectileOrigin origin, String reason) {
        String collision = hit instanceof EntityHitResult entityHit
            ? "entity:" + entityHit.getEntity().getUUID()
            : hit.getType() + ":" + BlockPos.containing(hit.getLocation());
        UUID operationId = UUID.nameUUIDFromBytes(("dndturn:arrow-effect:" + arrow.getUUID()
            + ':' + collision).getBytes(StandardCharsets.UTF_8));
        engine.rejectCausalProjectileEffect(encounterId, operationId,
            origin.ownerId() == null ? arrow.getUUID() : origin.ownerId(), arrow.getUUID(),
            hit instanceof EntityHitResult entityHit ? entityHit.getEntity().getUUID() : null,
            cumulativeServerTicks, reason);
        arrow.discard();
        sync(engine.stateView(encounterId));
    }

    /** Pure query. Preparing a vanilla simulation step is a separate execution boundary. */
    public boolean isEntitySimulationPaused(Entity entity) {
        if (quarantinedProjectiles.containsKey(entity.getUUID())) return true;
        if (entity instanceof AbstractArrow || entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball && projectileOrigins.containsKey(entity.getUUID())) {
            UUID recorded = projectileSimulationDomains.get(entity.getUUID());
            UUID domain = recorded == null ? null : engine.canonicalEncounterId(recorded);
            if (domain == null || !engine.encounterIds().contains(domain)) return isEntityInsidePausedRegion(entity);
            return pendingProjectileAttacks.containsKey(entity.getUUID())
                || !domain.equals(activeEnvironmentEncounters.get(entity.level()));
        }
        if (!isEntityInsidePausedRegion(entity)) return false;
        MobMoveLease lease = mobMoves.get(entity.getUUID());
        if (lease == null || !activeMobLease(lease)) return true;
        if (lease.pathStarted && entity instanceof Mob mob) {
            int cost = nextMobStepCost(mob, lease);
            return cost < 0 || engine.stateView(lease.encounterId).members().get(lease.mobId).movementTicks() < cost;
        }
        return false;
    }

    private record SimulationDecision(long tick, CombatEngine.Revision revision, UUID environment, boolean paused) {}
    private final Map<UUID, SimulationDecision> simulationDecisions = new HashMap<>();

    /** Called only from the vanilla entity/ride execution gate; repeated visits cannot replay effects. */
    public boolean prepareEntitySimulation(Entity entity) {
        requireThread();
        SimulationDecision prior = simulationDecisions.get(entity.getUUID());
        UUID environment = activeEnvironmentEncounters.get(entity.level());
        if (prior != null && prior.tick() == cumulativeServerTicks
            && prior.revision().equals(engine.revision()) && Objects.equals(prior.environment(), environment))
            return prior.paused();
        boolean paused = prepareEntitySimulationStep(entity);
        simulationDecisions.put(entity.getUUID(), new SimulationDecision(cumulativeServerTicks,
            engine.revision(), environment, paused));
        return paused;
    }

    private boolean prepareEntitySimulationStep(Entity entity) {
        if (quarantinedProjectiles.containsKey(entity.getUUID())) return true;
        if (entity instanceof AbstractArrow arrow) return arrowSimulationPaused(arrow);
        if (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball && projectileOrigins.containsKey(entity.getUUID())) {
            UUID domain = liveProjectileDomain(entity.getUUID());
            return domain != null && !domain.equals(activeEnvironmentEncounters.get(entity.level()));
        }
        MobMoveLease lease = mobMoves.get(entity.getUUID());
        if (lease != null && !activeMobLease(lease)) {
            revokeMobLease(lease);
            lease = null;
        }
        if (!isEntityInsidePausedRegion(entity)) {
            if (lease != null) closeMobMove(lease, "member moved outside fixed region");
            return false;
        }
        if (lease == null) return true;
        if (lease.pathStarted && entity instanceof Mob mob) {
            int remaining = engine.stateView(lease.encounterId).members().get(entity.getUUID()).movementTicks();
            // Inspect the next vanilla navigation/control proposal and its loaded collision
            // corridor. Flat dry steps cost one; water and possible jumps need their own budget.
            int possibleCost = nextMobStepCost(mob, lease);
            if (possibleCost < 0 || remaining < possibleCost) {
                lease.budgetBlocked = true;
                return true;
            }
            lease.authorizedCost = possibleCost;
            if (lease.underreserveNextExpensiveStepForGameTest && possibleCost >= 2) {
                lease.authorizedCost = 1;
            }
        }
        return false;
    }

    /** -1 means this path or its terrain cannot be authorized without another world load. */
    private static int nextMobStepCost(Mob mob, MobMoveLease lease) {
        var path = mob.getNavigation().getPath();
        if (path == null || path != lease.ownedPath || path.isDone()) return -1;
        int index = path.getNextNodeIndex();
        int last = Math.min(path.getNodeCount() - 1, index + 1);
        if (index < 0 || last < index) return -1;
        int cost = mob.isInWater() ? 2 : 1;
        boolean couldJump = mob.onGround() && (mob.isJumping() || mob.horizontalCollision
            || mob.getMoveControl().hasWanted()
                && mob.getMoveControl().getWantedY() > mob.getY() + 0.25);
        for (int node = index; node <= last; node++) {
            Vec3 destination = path.getEntityPosAtNode(mob, node);
            Vec3 delta = destination.subtract(mob.position());
            if (delta.lengthSqr() > 9.0) return -1;
            if (destination.y > mob.getY() + 0.25) couldJump = true;
            AABB swept = mob.getBoundingBox().expandTowards(delta.x, Math.max(0, delta.y), delta.z);
            if (!mob.level().noCollision(mob, swept)) couldJump = true;
            int samples = Math.max(1, (int) Math.ceil(delta.length() * 4));
            for (int sample = 0; sample <= samples; sample++) {
                AABB body = mob.getBoundingBox().move(delta.scale((double) sample / samples));
                int water = waterInLoadedBody((ServerLevel) mob.level(), body);
                if (water < 0) return -1;
                if (water > 0) cost = 2;
            }
        }
        return cost + (couldJump ? 1 : 0);
    }

    public boolean isEntityInsidePausedRegion(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        Vec3 center = entity.getBoundingBox().getCenter();
        String dimension = level.dimension().identifier().toString();
        for (var entry : regions.entrySet()) {
            EncounterRegion region = entry.getValue();
            if (region.dimension().equals(dimension)
                && region.containsPoint(center.x, center.y, center.z)
                && !exitedRegion(entity, entry.getKey())) return true;
        }
        return false;
    }

    /** Use the same continuous region with a block's center, not its whole chunk. */
    public boolean isBlockSimulationPaused(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().identifier().toString();
        for (var entry : regions.entrySet()) {
            EncounterRegion region = entry.getValue();
            if (region.dimension().equals(dimension)
                && region.containsBlock(pos.getX(), pos.getY(), pos.getZ()))
                if (!entry.getKey().equals(activeEnvironmentEncounters.get(level))) return true;
        }
        return false;
    }

    /** The local client receives changes only when it enters or leaves a formal paused region. */
    public void syncBodyStateTransitions() {
        requireThread();
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            sendBodyState(player, MinecraftCombatRuntime.isBodyPaused(player),
                hasPlayerMoveLease(player.getUUID()) && !MinecraftCombatRuntime.isPlayerMovementPaused(player));
        }
        subscriptions.retainOnline(online);
        entityProjections.sync();
    }

    public ServerEntityProjections entityProjections() { return entityProjections; }

    private long presentationMovementSequence;

    /** Read-only projection of the same membership, region and lease facts used by simulation gates. */
    public PresentationState.Facts presentationFacts(Entity entity) {
        requireThread();
        UUID member = engine.encounterOf(entity.getUUID());
        UUID controller = null;
        Vec3 center = entity.getBoundingBox().getCenter();
        for (var entry : regions.entrySet()) {
            if (entry.getValue().dimension().equals(entity.level().dimension().identifier().toString())
                && entry.getValue().containsPoint(center.x, center.y, center.z) && !exitedRegion(entity, entry.getKey())) {
                controller = entry.getKey(); break;
            }
        }
        var use = PresentationState.Use.STOPPED;
        if (controller != null && entity instanceof LivingEntity living && living.isUsingItem()) {
            UUID identity = tacticalActions == null ? null : tacticalActions.presentationUseId(entity.getUUID());
            if (identity == null) identity = ((PresentationUseIdentity)living).dndturn$useIdentity();
            if (identity != null) use = new PresentationState.Use(identity, living.getUsedItemHand(), TacticalItems.revision(living, living.getUseItem()),
                Math.max(0, living.getTicksUsingItem()), Math.max(0, living.getUseItemRemainingTicks()));
        }
        boolean paused = entity instanceof ServerPlayer player ? MinecraftCombatRuntime.isBodyPaused(player) : clientEntityPaused(entity);
        return new PresentationState.Facts(member, controller,
            controller == null ? "RELEASED" : engine.stateView(controller).phase().name(), paused, use);
    }

    private boolean clientEntityPaused(Entity entity) {
        if (quarantinedProjectiles.containsKey(entity.getUUID())) return true;
        if (entity instanceof AbstractArrow || entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball && projectileOrigins.containsKey(entity.getUUID())) {
            UUID recorded = projectileSimulationDomains.get(entity.getUUID());
            UUID domain = recorded == null ? null : engine.canonicalEncounterId(recorded);
            if (domain != null && engine.encounterIds().contains(domain)) {
                var state = engine.stateView(domain);
                return recoveryPending.contains(domain) || pendingProjectileAttacks.containsKey(entity.getUUID())
                    || state.phase() != EncounterPhase.ENVIRONMENT || server.tickRateManager().isFrozen();
            }
        }
        return isEntitySimulationPaused(entity);
    }

    public void sendBodyState(ServerPlayer player, boolean paused, boolean movementAllowed) {
        requireThread();
        if (closing) return;
        subscriptions.body(player, paused, movementAllowed);
    }

    private void sync(CombatEngine.StateView state) {
        long projectionRevision = Math.addExact(projectionRevisions.getOrDefault(state.id(), 0L), 1);
        List<CombatNetwork.MemberNotice> roster = state.members().values().stream()
            .sorted(java.util.Comparator.comparingInt(CombatEngine.MemberView::initiative).reversed()
                .thenComparingInt(CombatEngine.MemberView::tieBreak)
                .thenComparing(CombatEngine.MemberView::id))
            .map(member -> {
                Entity entity = resolve(member.id());
                return new CombatNetwork.MemberNotice(member.id(),
                    entity == null ? member.id().toString() : entity.getName().getString(),
                    member.initiative(), member.eligibleRound(), member.dodging(), member.disengaged(),
                    member.movementTicks(), member.action(), member.reaction());
            }).toList();
        projectionRevisions.put(state.id(), projectionRevision);
        for (var entry : state.members().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            CombatEngine.MemberView member = entry.getValue();
            subscriptions.publish(player, new CombatNetwork.EncounterState(generation,
                state.id(), sessionSequences.getOrDefault(state.id(), 0L), true,
                state.version(), state.phase(), state.round(),
                state.current(), state.region().version(), member.movementTicks(),
                member.action(), member.reaction(), state.environmentRemaining(),
                playerMoves.containsKey(player.getUUID()),
                playerMoves.containsKey(player.getUUID())
                    ? playerMoves.get(player.getUUID()).operationId : null, roster,
                engine.resultPage(state.id(), 0, 1).total(), !recoveryPending.contains(state.id()), projectionRevision));
            subscriptions.subscribe(player, state.id());
        }
    }

    private void clear(UUID member, UUID encounterId, long version) {
        ServerPlayer player = server.getPlayerList().getPlayer(member);
        if (player != null) clear(player, encounterId, version);
    }

    private void clear(ServerPlayer player, UUID encounterId, long version) {
        long revision = projectionRevisions.merge(encounterId, 1L, Math::addExact);
        subscriptions.publish(player, new CombatNetwork.EncounterState(
            generation, encounterId, sessionSequences.getOrDefault(encounterId, 0L), false,
            version, EncounterPhase.ENDED,
            0, null, 0, 0, false, false, 0, false, null, List.of(), 0, false, revision));
    }

    private static EncounterRegion.Point pointOf(Entity entity) {
        Vec3 center = entity.getBoundingBox().getCenter();
        return new EncounterRegion.Point(center.x, center.y, center.z);
    }

    private void requireThread() {
        if (!server.isSameThread()) throw new IllegalStateException("combat service requires server thread");
    }

    private OperationRecord.Snapshot operationSnapshot(UUID operationId, UUID parentId, UUID encounterId,
            UUID owner, UUID source, UUID target, long tick, long version, GridCell sourceCell,
            GridCell targetCell, OperationRecord.Kind kind) {
        if (closing || recoveryFailure || recoveryPending.contains(encounterId))
            throw new IllegalStateException("encounter platform state is not ready");
        if (tick != cumulativeServerTicks) throw new IllegalArgumentException("operation clock must be server observation time");
        return new OperationRecord.Snapshot(operationId, parentId, encounterId, owner, source, target,
            tick, version, sourceCell, targetCell, kind, generation);
    }

    private void quarantineProjectile(UUID projectile, UUID encounter, UUID target, String reason) {
        quarantinedProjectiles.putIfAbsent(projectile, new CombatPersistenceEnvelope.QuarantinedProjectile(
            arrowOperationId(projectile, target), encounter, target, reason));
        lastPersistedRevision = null;
    }

    public boolean isProjectileQuarantined(UUID projectile) {
        requireThread();
        return quarantinedProjectiles.containsKey(projectile);
    }
}
