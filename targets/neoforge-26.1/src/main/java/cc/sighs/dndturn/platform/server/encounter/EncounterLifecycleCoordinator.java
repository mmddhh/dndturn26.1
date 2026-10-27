package cc.sighs.dndturn.platform.server.encounter;
import cc.sighs.dndturn.domain.encounter.StateView;
import net.minecraft.core.BlockPos;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import cc.sighs.dndturn.platform.server.entity.MinecraftDeaths;
import cc.sighs.dndturn.platform.server.action.AbilityExecutors;
import cc.sighs.dndturn.platform.server.world.WorldSimulationCoordinator;
import static cc.sighs.dndturn.platform.server.world.WorldSimulationCoordinator.exactRegionChunks;
import static cc.sighs.dndturn.platform.server.world.WorldSimulationCoordinator.regionChunksLoaded;
import cc.sighs.dndturn.platform.server.projectile.ProjectileExecutionCoordinator;
import cc.sighs.dndturn.platform.server.damage.DamageResolution;
import cc.sighs.dndturn.platform.server.movement.MovementExecutionCoordinator;
import cc.sighs.dndturn.domain.ability.ProcessState;
import cc.sighs.dndturn.platform.server.action.ExecutionProcesses;
import cc.sighs.dndturn.domain.encounter.ConsentWindow;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.EncounterRegion;
import cc.sighs.dndturn.domain.encounter.EncounterStateSnapshot;
import cc.sighs.dndturn.domain.encounter.StartDisposition;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.checkpoint.AbilityCheckpoint;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.control.ExitAuthorizations;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController;
import cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope;
import cc.sighs.dndturn.platform.server.persistence.EncounterPersistence;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Owns consent, admission receipts, departures, merge/recovery state and outcome-boundary cleanup. */
public final class EncounterLifecycleCoordinator {
    public interface Host {
        long clock();
        boolean active();
        boolean closing();
        void cancelAction(UUID actor, String reason);
        boolean actionRunning(UUID actor);
        boolean actionControlFault(UUID actor);
        void drainImmediateTriggers(LiveActorContext actor, UUID encounter, UUID parent);
        void syncBodyStateTransitions();
        void persistNow();
        void endTurn(UUID encounter, ServerPlayer player, UUID operation, long version);
        OperationRecord.Snapshot operationSnapshot(UUID operationId, UUID parentId, UUID encounterId,
            UUID owner, UUID source, UUID target, long tick, long version, GridCell sourceCell,
            GridCell targetCell, OperationRecord.Kind kind);
    }
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final EncounterAuthority engine;
    private final UUID generation;
    private final Host host;
    private final ActorStateAuthority actorStates;
    private final WorldSimulationCoordinator world;
    private final ProjectileExecutionCoordinator projectiles;
    private final MovementExecutionCoordinator movement;
    private final EncounterPersistence persistence;
    private final ProjectionPublisher projections;
    private final VanillaEffectRoundController vanillaEffects;
    private final ExecutionProcesses processes;
    private final DamageResolution damageResolution;
    public EncounterLifecycleCoordinator(MinecraftServer server, EncounterAuthority engine, UUID generation,
            ServerCombatConfig config, Host host, ActorStateAuthority actorStates, WorldSimulationCoordinator world,
            ProjectileExecutionCoordinator projectiles, MovementExecutionCoordinator movement, EncounterPersistence persistence,
            ProjectionPublisher projections, VanillaEffectRoundController vanillaEffects, ExecutionProcesses processes, DamageResolution damageResolution) {
        this.server = server; this.engine = engine; this.generation = generation; this.config = config; this.host = host;
        this.actorStates = actorStates; this.world = world; this.projectiles = projectiles; this.movement = movement;
        this.persistence = persistence; this.projections = projections; this.vanillaEffects = vanillaEffects;
        this.processes = processes; this.damageResolution = damageResolution;
        this.consent = new ServerConsentCoordinator(server, generation, this::isMember, this::sampleConsentRegion, this::commitConsentedEncounter);
    }

    public boolean prepareWorldBoundary(ServerLevel level) {
        auditRestoredEncounters(level);
        // Isolated recovery probes may audit values, but cannot replace the live
        // scheduler's queue ownership (including while the world is frozen).
        if (!host.active()) return false;
        // New encounters become visible atomically before scheduling platform work.
        for (UUID id : engine.encounterIds()) {
            EncounterRegion region = world.regions().get(id);
            if (recovering(id) || region == null || !region.dimension().equals(level.dimension().identifier().toString())
                || pendingMerges.values().stream().anyMatch(plan -> plan.encounters().contains(id))) continue;
            if (engine.encounterIds().stream().anyMatch(other -> !other.equals(id)
                && region.overlaps(engine.stateView(other).region()))) scheduleMerge(id);
        }
        attemptPendingMerges(level);
        return true;
    }
    public void flushWorldOutcome() {
        confirmPendingDeaths();
        if (worldOutcomeDepth == 0) for (UUID departed : Set.copyOf(departuresDuringWorldOutcome)) {
            departuresDuringWorldOutcome.remove(departed); leave(departed);
        }
    }
    private final ServerCombatConfig config;
    private final ServerConsentCoordinator consent;
    private final Map<UUID, CombatPersistenceEnvelope.MergeBinding> mergeBindings = new HashMap<>();
    private final Set<UUID> recoveryPending = new HashSet<>();
    private final Set<UUID> stopping = new HashSet<>();
    private final Set<UUID> pendingStops = new HashSet<>();
    private final Map<UUID, CombatPersistenceEnvelope.CapturedSettings> capturedSettings = new HashMap<>();
    private final Set<UUID> departuresDuringWorldOutcome = new HashSet<>();
    private final Set<UUID> pendingDeaths = new HashSet<>();
    private final Map<UUID, UUID> completedPlayerDeaths = new HashMap<>();
    public void completeWorldOutcome(Runnable completion) {
        requireThread();
        worldOutcomeDepth++;
        try { completion.run(); }
        finally {
            worldOutcomeDepth--;
            if (worldOutcomeDepth == 0) {
                confirmPendingDeaths();
                for (UUID departed : Set.copyOf(departuresDuringWorldOutcome)) {
                    departuresDuringWorldOutcome.remove(departed);
                    leave(departed);
                }
            }
        }
    }
    private record StartRequestReceipt(UUID owner, UUID encounterId) {}
    private final Map<UUID, StartRequestReceipt> startReceipts = new HashMap<>();
    private record ExitRequestReceipt(UUID owner, UUID encounterId, long expectedVersion) {}
    private final Map<UUID, ExitRequestReceipt> exitReceipts = new HashMap<>();
    private final ExitAuthorizations exitAuthorizations = new ExitAuthorizations();
    private final Map<UUID, EncounterAuthority.MergePlan> pendingMerges = new HashMap<>();
    private final Map<UUID, String> mergeFailures = new HashMap<>();
    private int worldOutcomeDepth;

    public CombatPersistenceEnvelope.CapturedSettings settingsFor(UUID encounterId) {
        CombatPersistenceEnvelope.CapturedSettings settings = capturedSettings.get(
            engine.canonicalEncounterId(encounterId));
        if (settings == null) throw new IllegalStateException("encounter lacks captured settings");
        return settings;
    }
    public void noteDeath(UUID entityId) {
        requireThread();
        pendingDeaths.add(entityId);
    }

    /** ServerPlayer.die does not set LivingEntity.dead; called after its uncancelled native death path. */
    public void noteCompletedPlayerDeath(ServerPlayer player) {
        requireThread();
        completedPlayerDeaths.put(player.getUUID(), ((PresentationIdentity) player).dndturn$presentationInstance());
        pendingDeaths.add(player.getUUID());
    }

    public void confirmPendingDeaths() {
        requireThread();
        if (worldOutcomeDepth != 0) return;
        for (UUID entityId : Set.copyOf(pendingDeaths)) {
            pendingDeaths.remove(entityId);
            UUID completedInstance = completedPlayerDeaths.remove(entityId);
            Entity entity = resolve(entityId);
            if (entity instanceof LivingEntity living
                && (cc.sighs.dndturn.platform.server.entity.MinecraftDeaths.deathCommitted(living)
                    || living instanceof ServerPlayer && !living.isAlive()
                        && ((PresentationIdentity) living).dndturn$presentationInstance().equals(completedInstance))) {
                actorStates.death(entityId);
                leave(entityId);
            }
        }
    }

    /** Appends evidence without rewriting old results, acquiring leases or invoking behavior hooks. */
    private void auditAbilityRecovery(ServerLevel level, UUID encounter) {
        for (var process : processes.snapshot().values()) {
            if (process.terminal() != null || !engine.canonicalEncounterId(process.encounter()).equals(encounter)) continue;
            processes.observe(process.id(), "TERMINAL", process.steps(), process.nativePending(),
                    ProcessState.Release.RELEASED, OperationRecord.Outcome.UNKNOWN,
                    "restart revoked old controls; semantic state retained; native callback not replayed");
        }
        for (var evidence : persistence.checkpoints().values()) {
            if (!engine.canonicalEncounterId(evidence.encounter()).equals(encounter)
                || evidence.phase() == AbilityCheckpoint.Phase.TERMINAL) continue;
            ActionFailure.Code code = ActionFailure.Code.UNKNOWN_EFFECT;
            String reason = "captured effects retained; old execution control revoked; action not replayed";
            try {
                AbilityExecutors.resolve(evidence.invocation());
                if (!(level.getEntity(evidence.owner()) instanceof LivingEntity body)) {
                    code = ActionFailure.Code.SOURCE_INVALID; reason = "actor unavailable; no chunks loaded; action not replayed";
                } else if (!evidence.ownerInstance().equals(((PresentationIdentity)body).dndturn$presentationInstance())) {
                    code = ActionFailure.Code.SOURCE_INVALID; reason = "actor instance replaced; old lease not restored";
                } else {
                    var owned = AbilityAdapterRegistry.facts(evidence.invocation()).source(actorStates.context(body), evidence.invocation().hand());
                    if (!java.util.Objects.equals(owned, evidence.invocation().source())) {
                        code = ActionFailure.Code.SOURCE_INVALID; reason = "source changed; confirmed effects retained; action not replayed";
                    }
                }
            } catch (RuntimeException unavailable) {
                code = ActionFailure.Code.ABILITY_UNAVAILABLE; reason = "ability version or source adapter unavailable; action not replayed";
            }
            // RECONCILE_NATIVE: compare the recorded pre-state with the live world. This only
            // narrows the evidence; it never replays the callback or grants a new effect.
            if (code == ActionFailure.Code.UNKNOWN_EFFECT && evidence.before() != null) {
                int changed = 0;
                for (var block : evidence.before().blocks()) {
                    BlockPos pos = new BlockPos(block.cell().x(), block.cell().y(), block.cell().z());
                    if (!level.hasChunkAt(pos)) continue;
                    if (!level.getBlockState(pos).toString().equals(block.state())) changed++;
                }
                reason = changed == 0
                    ? "native reconciliation: recorded pre-state unchanged; no effect confirmed; not replayed"
                    : "native reconciliation: world differs from pre-state; effect unverified; not replayed";
            }
            persistence.appendAudit(new CombatPersistenceEnvelope.RecoveryAudit(evidence.operation(), generation,
                host.clock(), code, reason));
        }
        projectiles.auditAbilityRecovery(encounter);
        persistence.changed();
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
            StateView state = engine.stateView(encounterId);
            if (!dimension.equals(state.region().dimension())) continue;
            boolean regionLoaded = regionChunksLoaded(level, world.chunks(encounterId));
            boolean allMembersPresent = state.members().keySet().stream()
                .allMatch(id -> level.getEntity(id) instanceof LivingEntity member && member.isAlive());
            // Availability is not evidence of a failed effect. Wait for natural loading/login;
            // recoveryPending keeps every execution path closed without advancing simulation.
            if (!regionLoaded || !allMembersPresent) continue;
            var binding = mergeBindings.get(encounterId);
            if (binding != null && binding.phase() != CombatPersistenceEnvelope.MergePhase.BOUND
                && binding.sources().stream().allMatch(id -> engine.canonicalEncounterId(id).equals(encounterId))) {
                if (!world.scheduledTicks().canRebindMerge(binding.sources(), state.region())) continue;
                bindMergedOwners(level, binding, state.region());
            }
            String behaviorRecovery = engine.exportSnapshot().encounters().stream().filter(e -> e.id().equals(encounterId))
                .flatMap(e -> e.pending().values().stream()).filter(e -> e.intent() != null)
                .map(e -> AbilityExecutors.recoveryReason(e.intent())).distinct().collect(java.util.stream.Collectors.joining("; "));
            auditAbilityRecovery(level, encounterId);
            resolveRestoredWork(encounterId);
            int unknown = engine.failRestoredWork(encounterId, host.clock(),
                behaviorRecovery.isEmpty() ? "restart evidence could not prove previous world effect" : behaviorRecovery);
            for(UUID owner:state.members().keySet()) if(level.getEntity(owner) instanceof LivingEntity living
                && !vanillaEffects.reconcile(living)) {
                engine.recordRestoredUnknown(encounterId,UUID.randomUUID(),owner,owner,owner,host.clock(),
                    "participant effect/native save order mismatch; prior settlement is not replayed");
                unknown++;
            }
            unknown += projectiles.reconcilePending(encounterId);
            if (unknown > 0) {
                LOGGER.warn("Restored encounter {} released: membersPresent={} unknownOperations={}",
                    encounterId, allMembersPresent, unknown);
                stop(encounterId);
            } else {
                projections.nextRevision(encounterId);
                if (host.active()) world.scheduledTicks().captureAll(level);
                recoveryPending.remove(encounterId);
                turnStartedAt.put(encounterId, host.clock());
                sync(engine.stateView(encounterId));
            }
            recoveryPending.remove(encounterId);
        }
    }

    private void scheduleMerge(UUID seed) {
        queueMerge(engine.planMerge(seed));
    }

    public void queueMerge(EncounterAuthority.MergePlan plan) {
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

    private void repairMergeBindings(ServerLevel level) {
        for (var binding : List.copyOf(mergeBindings.values())) {
            // The recovery audit binds committed merges only after all participants are ready.
            if (recoveryPending.contains(binding.primary())) continue;
            if (binding.phase() == CombatPersistenceEnvelope.MergePhase.BOUND || !engine.encounterIds().contains(binding.primary())) continue;
            // PREPARED is not proof that the rules committed. Only aliases/result evidence can establish that.
            boolean committed = binding.sources().stream().allMatch(id -> engine.canonicalEncounterId(id).equals(binding.primary()));
            if (!committed) continue;
            var region = engine.stateView(binding.primary()).region();
            if (!region.dimension().equals(level.dimension().identifier().toString())) continue;
            if (!regionChunksLoaded(level, exactRegionChunks(region)) || !world.scheduledTicks().canRebindMerge(binding.sources(), region))
                throw new IllegalStateException("committed merge awaits loaded owner bindings; simulation cannot advance");
            bindMergedOwners(level, binding, region);
        }
    }
    private void bindMergedOwners(ServerLevel level, CombatPersistenceEnvelope.MergeBinding binding, EncounterRegion region) {
        projections.bind(binding.primary(), binding.sequence());
        for (UUID id : binding.sources()) {
            if (id.equals(binding.primary())) continue;
            EncounterAuthority.View closed = engine.closedView(id);
            world.unbind(id); capturedSettings.remove(id);
            damageResolution.forget(id);
            if (closed != null) for (UUID member : closed.members().keySet()) clear(member, id, closed.version());
        }
        world.bind(binding.primary(), region);
        world.scheduledTicks().rebindMerge(binding.sources(), binding.primary(), region);
        exitAuthorizations.reconcile(engine, this::resolve);
        pendingMerges.remove(binding.primary());
        mergeFailures.remove(binding.primary());
        mergeBindings.put(binding.primary(), binding.at(CombatPersistenceEnvelope.MergePhase.BOUND));
        host.persistNow();
    }

    /** Runs before any scheduled queue or world callback sees the newly merged projection. */
    private void attemptPendingMerges(ServerLevel level) {
        repairMergeBindings(level);
        for (EncounterAuthority.MergePlan plan : List.copyOf(pendingMerges.values())) {
            if (plan.encounters().stream().anyMatch(this::recovering)) continue;
            if (!plan.equals(pendingMerges.get(plan.primary()))) continue;
            if (!engine.encounterIds().containsAll(plan.encounters())) {
                pendingMerges.remove(plan.primary());
                mergeFailures.remove(plan.primary());
                continue;
            }
            StateView primary = engine.stateView(plan.primary());
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
            EncounterAuthority.MergePlan working = plan;
            boolean ruleCommitted = false;
            boolean projectionCommitted = false;
            try {
                EncounterRegion sampled = null;
                boolean closureStable = false;
                for (int expansion = 0; expansion < engine.encounterIds().size(); expansion++) {
                    sampled = sampleMergedRegion(level, working);
                    EncounterAuthority.MergePlan discovered = engine.planMergeProjected(working.primary(), sampled);
                    if (discovered.encounters().equals(working.encounters())) {
                        closureStable = true;
                        break;
                    }
                    if (!discovered.encounters().containsAll(working.encounters()))
                        throw new IllegalStateException("resampling no longer covers a captured merge participant");
                    queueMerge(discovered);
                    working = discovered;
                }
                if (!closureStable) throw new IllegalStateException("merge overlap closure did not stabilize");
                StateView ready = engine.stateView(working.primary());
                if (ready.phase() != EncounterPhase.ENVIRONMENT
                    || ready.round() != working.targetEnvironmentRound()
                    || engine.mergeWindowExpired(working)) continue;
                movement.finishEncounters(Set.copyOf(working.encounters()), "movement settled before merge boundary");
                Set<Long> chunks = exactRegionChunks(sampled);
                if (!regionChunksLoaded(level, chunks)
                    || !world.scheduledTicks().canRebindMerge(working.encounters(), sampled))
                    throw new IllegalStateException("merged region or held tick chunks are not loaded and ticking");
                long mergedProjectionSequence = projections.nextSequence();
                UUID mergeOperation = UUID.randomUUID();
                var binding = new CombatPersistenceEnvelope.MergeBinding(mergeOperation, working.primary(), working.encounters(),
                    CombatPersistenceEnvelope.MergePhase.PREPARED, mergedProjectionSequence);
                mergeBindings.put(working.primary(), binding);
                host.persistNow();
                engine.commitMerge(working, sampled,
                    mergeOperation, host.clock());
                ruleCommitted = true;
                mergeBindings.put(working.primary(), binding.at(CombatPersistenceEnvelope.MergePhase.RULES_COMMITTED));
                host.persistNow();
                ruleCommitted = true;
                bindMergedOwners(level, binding, sampled);
                projectionCommitted = true;
                pendingMerges.remove(working.primary());
                mergeFailures.remove(working.primary());
                sync(engine.stateView(working.primary()));
                host.syncBodyStateTransitions();
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

    private EncounterRegion sampleMergedRegion(ServerLevel level, EncounterAuthority.MergePlan plan) {
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
        // Only participating players anchor the merged field.
        List<Entity> anchors = new ArrayList<>();
        for (UUID id : plan.encounters()) {
            for (UUID member : engine.stateView(id).members().keySet()) {
                Entity found = level.getEntity(member);
                if (found == null || !discovery.contains(pointOf(found))) continue;
                if (found instanceof ServerPlayer) anchors.add(found);
            }
        }
        if (anchors.isEmpty()) throw new IllegalStateException("no loaded anchor in merged discovery");
        CombatPersistenceEnvelope.CapturedSettings settings = settingsFor(plan.primary());
        return MinecraftRegionSampler.capturePlayers(level, anchors.get(0), discovery, anchors,
            settings.regionRadius(), Math.addExact(version, 1), settings.maxSampledChunks(), settings.maxAnchors());
    }
    /** Successful insertion evidence is supplied by the bounded item execution scope. */
    public void joinGeneratedMob(ServerPlayer owner, Mob mob, UUID encounter) {
        requireThread();
        if (!encounter.equals(engine.encounterOf(owner.getUUID())) || mob.level() != owner.level()
            || mob.isRemoved() || !mob.isAlive()) throw new IllegalStateException("spawn membership context changed");
        var center = mob.getBoundingBox().getCenter();
        if (!engine.stateView(encounter).region().containsPoint(center.x, center.y, center.z))
            throw new IllegalStateException("spawn outside encounter");
        UUID existing = engine.encounterOf(mob.getUUID());
        if (existing != null && !existing.equals(encounter)) throw new IllegalStateException("spawn already belongs to another encounter");
        if (engine.join(encounter, mob.getUUID())) {
            vanillaEffects.capture(mob);
            persistence.changed();
            sync(engine.stateView(encounter));
        }
    }

    public void reconcileMemberLocations() {
        requireThread();
        for (UUID encounterId : engine.encounterIds()) {
            if (recovering(encounterId)) continue;
            StateView state = engine.stateView(encounterId);
            for (UUID memberId : state.members().keySet()) {
                Entity member = null;
                for (ServerLevel level : server.getAllLevels()) {
                    member = level.getEntity(memberId);
                    if (member != null) break;
                }
                if (member == null) continue;
                if (member instanceof Mob mob) pullHostile(mob);
                if (!state.region().dimension().equals(member.level().dimension().identifier().toString())) {
                    leave(memberId);
                    continue;
                }
            }
        }
    }

    public void tickConsent() { requireThread(); consent.tick(); }
    public void consentDisconnected(UUID player) { requireThread(); consent.disconnected(player); }
    public void respondToConsent(ServerPlayer player, EncounterProtocol.ConsentReply reply) {
        requireThread(); consent.reply(player, reply);
    }
    public void resyncConsent(ServerPlayer player, UUID request) { requireThread(); consent.resend(player, request); }
    public ConsentWindow.View consentView(UUID request) { requireThread(); return consent.view(request); }

    public record StartResult(StartDisposition status, StateView state, String reason) {
        public StartResult {
            Objects.requireNonNull(status); Objects.requireNonNull(reason);
            if (status == StartDisposition.NONE || (status == StartDisposition.STARTED) != (state != null))
                throw new IllegalArgumentException("invalid start result");
        }
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
        if (consent.view(operationId) == null && (persistence.recoveryFailed() || isMember(player.getUUID())))
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
        return MinecraftRegionSampler.capturePlayers(player.level(), player, discovery, List.of(player),
            config.regionRadius(), 1, config.maxSampledChunks(), config.maxAnchors());
    }

    void commitConsentedEncounter(ServerPlayer anchor, EncounterRegion region, Set<UUID> players, Map<UUID, UUID> requestOwners) {
        StateView state = beginEncounter(anchor, region, players);
        requestOwners.forEach((id, owner) -> startReceipts.put(id, new StartRequestReceipt(owner, state.id())));
        persistence.changed();
        try {
            world.scheduledTicks().captureAll(anchor.level());
            // Install lifecycle-owned group effects before publishing the first usable turn.
            for (UUID member : state.members().keySet())
                if (resolve(member) instanceof LivingEntity living) vanillaEffects.capture(living);
        } catch (RuntimeException failure) {
            // Rules and receipts are already committed. Keep the encounter noninteractive
            // until the next pre-world audit retries platform preparation; never cancel START.
            recoveryPending.add(state.id());
            LOGGER.error("Encounter {} committed but queue capture needs recovery", state.id(), failure);
        }
        sync(state);
    }

    private StateView beginEncounter(ServerPlayer initiator,
                                                 EncounterRegion region, Set<UUID> approvedPlayers) {
        requireThread();
        if (persistence.recoveryFailed()) throw new IllegalStateException("combat save needs recovery before new encounters");
        if (isMember(initiator.getUUID())) throw new IllegalStateException("player already in an encounter");
        ServerLevel level = initiator.level();
        EncounterRegion.Discovery discovery = region.discovery();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!approvedPlayers.contains(player.getUUID()) && player.level() == level
                && region.containsPoint(pointOf(player).x(), pointOf(player).y(), pointOf(player).z()))
                throw new IllegalStateException("another player is inside the proposed encounter region");
        }
        Set<UUID> members = new HashSet<>();
        for (UUID playerId : approvedPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.level() != level)
                throw new IllegalStateException("consenting player became unavailable");
            var point = pointOf(player);
            if (!region.containsPoint(point.x(), point.y(), point.z()))
                throw new IllegalStateException("consenting player left the proposed region");
            // Already participating players consent to the new proposal but remain in their
            // authoritative encounter until the shared safe merge boundary commits.
            if (!isMember(playerId)) members.add(playerId);
        }
        // Non-hostile mobs do not join; pull in only mobs already hostile to a participating player.
        AABB search = new AABB(discovery.minX(), discovery.minY(), discovery.minZ(),
            discovery.maxX(), discovery.maxY(), discovery.maxZ());
        Map<UUID, UUID> initialHostility = new HashMap<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, search, candidate ->
                candidate.isAlive() && candidate.getTarget() instanceof ServerPlayer target
                    && members.contains(target.getUUID()))) {
            if (engine.encounterOf(mob.getUUID()) == null && members.add(mob.getUUID())) {
                initialHostility.put(mob.getUUID(), mob.getTarget().getUUID());
            }
        }
        UUID encounterId = UUID.randomUUID();
        long nextSequence = projections.nextSequence();
        var captured = CombatPersistenceEnvelope.CapturedSettings.from(config);
        // Session settings must exist before beginCandidate fires the turn-start observer, because
        // that observer re-anchors the field through settingsFor.
        capturedSettings.put(encounterId, captured);
        engine.beginCandidate(encounterId, region, members,
            config.time());
        for (Map.Entry<UUID, UUID> relation : initialHostility.entrySet()) {
            engine.setHostile(encounterId, relation.getKey(), relation.getValue(), true);
            engine.setHostile(encounterId, relation.getValue(), relation.getKey(), true);
        }
        projections.bind(encounterId, nextSequence);
        // The first turn observer may already have expanded the field to all consenting players.
        world.bind(encounterId, engine.stateView(encounterId).region());
        // Merge planning runs at beforeLevelTick. Queue capture follows the receipt commit.
        return engine.stateView(encounterId);
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
            throw ActionFailure.staleEncounter();
        var assessment = assessExit(player, encounterId);
        if (!assessment.allowed()) {
            String reason = switch (assessment.code()) {
                case EXIT_NOT_TURN -> "只能在自己的回合逃跑";
                case EXIT_BUSY -> "行动或反应仍在执行，请结束后重试";
                case EXIT_TOO_CLOSE -> String.format(java.util.Locale.ROOT,
                    "最近敌人距离 %.2f 格，逃跑需要至少 %.2f 格", assessment.nearestEnemyDistance(), assessment.requiredDistance());
                default -> "无法确认敌人当前位置，请稍后重试";
            };
            throw new ActionFailure(assessment.code(), ActionFailure.Retry.REFRESH_AND_REPROPOSE, reason);
        }
        if (startReceipts.containsKey(operationId)
            || engine.resultFor(encounterId, operationId) != null
            || engine.pendingOperation(encounterId, operationId) != null)
            throw new IllegalStateException("operation ID payload conflict");
        boolean otherPlayer = engine.stateView(encounterId).members().keySet().stream()
            .anyMatch(id -> !id.equals(player.getUUID()) && server.getPlayerList().getPlayer(id) != null);
        if (otherPlayer) leave(player.getUUID());
        else stop(encounterId);
        exitReceipts.put(operationId,
            new ExitRequestReceipt(player.getUUID(), encounterId, expectedVersion));
        if (engine.encounterIds().contains(encounterId))
            exitAuthorizations.grant(player, encounterId, operationId, expectedVersion);
        host.syncBodyStateTransitions();
    }

    /** Only bounded member facts; entity lookup never loads a chunk or scans unrelated mobs. */
    public EncounterAuthority.ExitAssessment assessExit(ServerPlayer player, UUID encounterId) {
        requireThread();
        var state = engine.stateView(encounterId);
        Map<UUID, EncounterAuthority.ExitPosition> positions = new HashMap<>();
        for (UUID member : state.members().keySet()) {
            if (!member.equals(player.getUUID()) && !engine.isHostile(encounterId, member, player.getUUID())
                && !engine.isHostile(encounterId, player.getUUID(), member)) continue;
            Entity entity = resolve(member);
            if (entity != null && !entity.isRemoved() && entity.level() == player.level()
                && state.region().dimension().equals(entity.level().dimension().identifier().toString()))
                positions.put(member, new EncounterAuthority.ExitPosition(entity.isAlive(), entity.getX(), entity.getZ()));
        }
        var result = engine.assessExit(encounterId, player.getUUID(), settingsFor(encounterId).escapeDistance(), positions);
        if (result.allowed() && host.actionRunning(player.getUUID()))
            return new EncounterAuthority.ExitAssessment(ActionFailure.Code.EXIT_BUSY,
                result.nearestEnemyDistance(), result.requiredDistance());
        return result;
    }

    public boolean exitedRegion(Entity entity, UUID encounterId) {
        return entity instanceof ServerPlayer && engine.encounterOf(entity.getUUID()) == null
            && exitAuthorizations.permits(entity, encounterId);
    }

    public Set<UUID> stop(UUID encounterId) {
        requireThread();
        if (!stopping.add(encounterId)) return Set.of();
        try { return stopOwned(encounterId); }
        finally { stopping.remove(encounterId); }
    }

    private Set<UUID> stopOwned(UUID encounterId) {
        var closedBefore = engine.closedView(encounterId);
        Set<UUID> owners = engine.encounterIds().contains(encounterId)
            ? Set.copyOf(engine.stateView(encounterId).members().keySet())
            : closedBefore == null ? Set.of() : Set.copyOf(closedBefore.members().keySet());
        List<RuntimeException> failures = new ArrayList<>();
        for (UUID owner : owners) releaseStep(failures, () -> {
            UUID current = engine.encounterOf(owner);
            if (current == null || current.equals(encounterId)) host.cancelAction(owner, "encounter stopped");
        });
        releaseStep(failures, () -> movement.finishEncounters(Set.of(encounterId), "encounter stopped"));
        for (UUID owner : owners) releaseStep(failures, () -> {
            UUID current = engine.encounterOf(owner);
            if (current != null && !current.equals(encounterId)) return;
            if (host.active() && resolve(owner) instanceof LivingEntity living) vanillaEffects.release(living);
            else vanillaEffects.forget(owner);
        });
        Set<UUID> released;
        try { released = engine.end(encounterId); }
        catch (RuntimeException failure) {
            pendingStops.add(encounterId);
            persistence.changed();
            failures.forEach(failure::addSuppressed);
            throw failure; // Rule ownership remains; do not expose its world as unowned.
        }
        recoveryPending.remove(encounterId);
        releaseStep(failures, projectiles::revokeEndedProjectileDomains);
        for (UUID owner : owners) releaseStep(failures, () -> {
            if (engine.encounterOf(owner) == null) movement.forgetForce(owner);
        });
        world.unbind(encounterId);
        capturedSettings.remove(encounterId);
        turnStartedAt.remove(encounterId);
        discardMergesContaining(encounterId);
        mergeFailures.remove(encounterId);
        damageResolution.forget(encounterId);
        releaseStep(failures, () -> world.scheduledTicks().releaseEncounter(encounterId));
        EncounterAuthority.View closed = engine.closedView(encounterId);
        if (closed != null) for (UUID member : owners)
            releaseStep(failures, () -> clear(member, encounterId, closed.version()));
        releaseStep(failures, host::syncBodyStateTransitions);
        persistence.changed();
        if (!failures.isEmpty()) {
            pendingStops.add(encounterId);
            RuntimeException failure = new IllegalStateException("encounter stopped with incomplete owner releases");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
        pendingStops.remove(encounterId);
        return released;
    }

    private static void releaseStep(List<RuntimeException> failures, Runnable release) {
        try { release.run(); }
        catch (RuntimeException failure) { failures.add(failure); }
    }

    public void leave(UUID entityId) {
        requireThread();
        if (host.closing()) return;
        exitAuthorizations.revoke(entityId);
        if (worldOutcomeDepth > 0) {
            if (engine.encounterOf(entityId) != null) departuresDuringWorldOutcome.add(entityId);
            return;
        }
        UUID encounterId = engine.encounterOf(entityId);
        if (encounterId == null) return;
        host.cancelAction(entityId, "member left");
        projections.leave(entityId);
        movement.finishPlanMovement(entityId);
        ServerPlayer departingPlayer = server.getPlayerList().getPlayer(entityId);
        movement.finishMember(entityId, "member left after observed movement");
        if(host.active() && resolve(entityId) instanceof LivingEntity living) vanillaEffects.release(living);
        else vanillaEffects.forget(entityId);
        engine.leave(encounterId, entityId);
        projectiles.revokeEndedProjectileDomains();
        movement.forgetForce(entityId);
        EncounterAuthority.View closed = engine.closedView(encounterId);
        if (closed != null) {
            world.unbind(encounterId);
            capturedSettings.remove(encounterId);
            turnStartedAt.remove(encounterId);
                
                    discardMergesContaining(encounterId);
            mergeFailures.remove(encounterId);
            damageResolution.forget(encounterId);
            world.scheduledTicks().releaseEncounter(encounterId);
            if (departingPlayer != null) projections.clear(departingPlayer, encounterId, closed.version());
            for (UUID member : closed.members().keySet()) clear(member, encounterId, closed.version());
        } else {
            StateView state = engine.stateView(encounterId);
            clear(entityId, encounterId, state.version());
            sync(state);
        }
        endIfNoPlayersRemain(encounterId);
        host.syncBodyStateTransitions();
    }
    public void enterOutcome() { worldOutcomeDepth++; }
    public void exitOutcome() { if (worldOutcomeDepth <= 0) throw new IllegalStateException("unbalanced outcome boundary"); worldOutcomeDepth--; }
    public int outcomeDepth() { return worldOutcomeDepth; }
    public boolean recovering(UUID encounter) {
        return recoveryPending.contains(encounter) || pendingStops.contains(encounter) || stopping.contains(encounter);
    }
    public boolean waitingForRecovery(UUID member) {
        return recoveryPending.contains(engine.encounterOf(member));
    }
    public Map<UUID, CombatPersistenceEnvelope.CapturedSettings> capturedSettings() { return Map.copyOf(capturedSettings); }
    public Map<UUID, CombatPersistenceEnvelope.MergeBinding> mergeBindings() { return Map.copyOf(mergeBindings); }
    public Map<UUID, EncounterAuthority.MergePlan> pendingMerges() { return Map.copyOf(pendingMerges); }
    public ExitAuthorizations exitAuthorizations() { return exitAuthorizations; }
    public void restore(CombatPersistenceEnvelope saved) {
            capturedSettings.putAll(saved.capturedSettings());
            for (EncounterStateSnapshot.MergePlanState plan : saved.pendingMerges()) {
                EncounterAuthority.MergePlan restoredPlan = plan.restore();
                pendingMerges.put(restoredPlan.primary(), restoredPlan);
            }
            for (var entry : saved.startReceipts().entrySet())
                startReceipts.put(entry.getKey(), new StartRequestReceipt(
                    entry.getValue().owner(), entry.getValue().encounterId()));
            for (var entry : saved.exitReceipts().entrySet())
                exitReceipts.put(entry.getKey(), new ExitRequestReceipt(
                    entry.getValue().owner(), entry.getValue().encounterId(),
                    entry.getValue().expectedVersion()));
            mergeBindings.putAll(saved.mergeBindings());
            exitAuthorizations.restore(saved.exitAuthorizations());
            capturedSettings.keySet().retainAll(engine.encounterIds());
            for (UUID encounterId : engine.encounterIds()) {
                engine.revokeRestoredPermits(encounterId);
                recoveryPending.add(encounterId);
            }
            if (!recoveryPending.isEmpty())
                persistence.changed();
    }
    public Map<UUID, CombatPersistenceEnvelope.StartReceipt> startReceipts() {
        Map<UUID, CombatPersistenceEnvelope.StartReceipt> starts = new HashMap<>();
        startReceipts.forEach((id, value) -> starts.put(id,
            new CombatPersistenceEnvelope.StartReceipt(value.owner(), value.encounterId())));
        return Map.copyOf(starts);
    }
    public Map<UUID, CombatPersistenceEnvelope.ExitReceipt> exitReceipts() {
        Map<UUID, CombatPersistenceEnvelope.ExitReceipt> exits = new HashMap<>();
        exitReceipts.forEach((id, value) -> exits.put(id,
            new CombatPersistenceEnvelope.ExitReceipt(value.owner(), value.encounterId(),
                value.expectedVersion())));
        return Map.copyOf(exits);
    }
    private void requireThread() { if (!server.isSameThread()) throw new IllegalStateException("encounter lifecycle requires server thread"); }
    private boolean isMember(UUID id) { return engine.encounterOf(id) != null; }
    private Entity resolve(UUID id) {
        for (ServerLevel level : server.getAllLevels()) { Entity entity = level.getEntity(id); if (entity != null) return entity; }
        return null;
    }
    private void sync(StateView state) { projections.publish(state, movement::playerOperation, !recovering(state.id()), player -> assessExit(player, state.id()), vanillaEffects::display); }
    private void clear(UUID member, UUID encounter, long version) { var player = server.getPlayerList().getPlayer(member); if (player != null) projections.clear(player, encounter, version); }
    private static EncounterRegion.Point pointOf(Entity entity) { Vec3 center = entity.getBoundingBox().getCenter(); return new EncounterRegion.Point(center.x, center.y, center.z); }



    public EncounterAuthority.ResultPage results(UUID encounterId, int fromIndex, int limit) {
        requireThread();
        return engine.resultPage(encounterId, fromIndex, limit);
    }

    /** Resolve a terminal retry before checking current membership; this grants no new execution. */
    public OperationRecord.Result completedRetry(ServerPlayer player, UUID requestedEncounter,
                                                  UUID operationId, OperationRecord.Kind kind,
                                                  long expectedVersion, UUID targetId) {
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
            || snapshot.encounterVersion() != expectedVersion)
            throw new IllegalStateException("operation ID payload conflict");
        return result;
    }

    public void resyncMember(ServerPlayer player, UUID requestedEncounter) {
        requireThread();
        UUID encounterId = engine.encounterOf(player.getUUID());
        if (requestedEncounter != null && !requestedEncounter.equals(encounterId)) {
            EncounterAuthority.View closed = engine.closedView(requestedEncounter);
            if (closed != null && closed.members().containsKey(player.getUUID()))
                projections.clear(player, requestedEncounter, closed.version());
        }
        if (encounterId != null) sync(engine.stateView(encounterId));
    }
    /** Translate only committed native targets; this never creates an Encounter in the ordinary world. */
    public void pullHostile(Mob mob) {
        requireThread();
        if (mob == null || mob.isRemoved() || !mob.isAlive()) return;
        LivingEntity target = mob.getTarget();
        if (target == null || target == mob || !target.isAlive() || target.isRemoved()
            || target.level() != mob.level() || resolve(target.getUUID()) != target
            || resolve(mob.getUUID()) != mob) return;
        UUID sourceDomain = engine.encounterOf(mob.getUUID());
        UUID targetDomain = engine.encounterOf(target.getUUID());
        if (recovering(sourceDomain) || recovering(targetDomain)) return;
        if (sourceDomain == null && targetDomain == null) return;
        if (sourceDomain != null && targetDomain != null && !sourceDomain.equals(targetDomain)) {
            if (pendingMerges.values().stream().noneMatch(plan -> plan.encounters().contains(sourceDomain)
                    && plan.encounters().contains(targetDomain))) {
                queueMerge(engine.requestCausalMerge(sourceDomain, targetDomain));
                persistence.changed();
            }
            return;
        }
        UUID domain = sourceDomain == null ? targetDomain : sourceDomain;
        if (!engine.stateView(domain).region().dimension().equals(mob.level().dimension().identifier().toString())) return;
        // A player's participation still requires the consent / attack-intrusion paths.
        if (targetDomain == null && target instanceof ServerPlayer) return;
        boolean changed = false;
        if (sourceDomain == null && engine.join(domain, mob.getUUID())) {
            vanillaEffects.capture(mob); changed = true;
        }
        if (targetDomain == null && engine.join(domain, target.getUUID())) {
            vanillaEffects.capture(target); changed = true;
        }
        if (!engine.isHostile(domain, mob.getUUID(), target.getUUID())
            || !engine.isHostile(domain, target.getUUID(), mob.getUUID())) {
            engine.setHostile(domain, mob.getUUID(), target.getUUID(), true);
            engine.setHostile(domain, target.getUUID(), mob.getUUID(), true);
            changed = true;
        }
        if (changed) {
            persistence.changed();
            sync(engine.stateView(domain));
            host.syncBodyStateTransitions();
        }
    }
    public boolean admitIntruder(UUID attackerId, UUID targetId) {
        requireThread();
        if (attackerId.equals(targetId)) return false;
        UUID encounterId = engine.encounterOf(targetId);
        if (encounterId == null || recovering(encounterId) || engine.encounterOf(attackerId) != null) return false;
        if (!engine.join(encounterId, attackerId)) return false;
        engine.setHostile(encounterId, attackerId, targetId, true);
        engine.setHostile(encounterId, targetId, attackerId, true);
        persistence.changed();
        reanchor(encounterId);
        sync(engine.stateView(encounterId));
        host.syncBodyStateTransitions();
        return true;
    }
    public void reanchor(UUID encounterId) {
        requireThread();
        if (stopping.contains(encounterId) || pendingStops.contains(encounterId)) return;
        StateView state = engine.stateView(encounterId);
        EncounterRegion current = state.region();
        if (current == null) return;
        // Session-captured settings, not live config, so an active session does not change meaning when config reloads.
        CombatPersistenceEnvelope.CapturedSettings settings = settingsFor(encounterId);
        List<ServerPlayer> players = new ArrayList<>();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (UUID id : state.members().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !p.level().dimension().identifier().toString().equals(current.dimension())) continue;
            players.add(p);
            var center = p.getBoundingBox().getCenter();
            minX = Math.min(minX, center.x); minY = Math.min(minY, center.y); minZ = Math.min(minZ, center.z);
            maxX = Math.max(maxX, center.x); maxY = Math.max(maxY, center.y); maxZ = Math.max(maxZ, center.z);
        }
        if (players.isEmpty()) return;
        double horizontal = settings.discoveryHorizontal(), vertical = settings.discoveryVertical();
        EncounterRegion.Discovery discovery = new EncounterRegion.Discovery(
            minX - horizontal, minY - vertical, minZ - horizontal,
            maxX + horizontal, maxY + vertical, maxZ + horizontal);
        // Prepare and validate the complete replacement before mutating any authoritative or derived owner.
        EncounterRegion next;
        try {
            next = MinecraftRegionSampler.capturePlayers(players.get(0).level(), players.get(0),
                discovery, players, settings.regionRadius(), Math.addExact(current.version(), 1),
                settings.maxSampledChunks(), settings.maxAnchors());
        } catch (MinecraftRegionSampler.SamplingDeferred samplingFailure) {
            // Bounded, expected sampling failure (unloaded chunk / too large): keep the previous field.
            DebugDiagnostics.log("FIELD_REANCHOR_DEFERRED", () -> "encounter=" + encounterId
                + " reason=" + samplingFailure.getMessage());
            return;
        }
        if (current.sameContent(next)) return;
        var binding = WorldSimulationCoordinator.prepareBinding(next);
        if (!world.scheduledTicks().canRebindMerge(Set.of(encounterId), next)) {
            DebugDiagnostics.log("FIELD_REANCHOR_DEFERRED", () -> "encounter=" + encounterId
                + " reason=held tick owner is not loaded");
            return;
        }
        // Commit: authoritative region first, then derived owners, then observers.
        engine.updateRegion(encounterId, next);
        try {
            world.bind(encounterId, binding);
            world.scheduledTicks().rebindRegion(encounterId);
        } catch (RuntimeException failure) {
            // The rule commit already happened. Release the session, never claim a rollback.
            pendingStops.add(encounterId);
            persistence.changed();
            try { stop(encounterId); }
            catch (RuntimeException releaseFailure) { failure.addSuppressed(releaseFailure); }
            throw new IllegalStateException("field committed but owner migration failed; encounter release attempted", failure);
        }
        persistence.changed();
        sync(engine.stateView(encounterId));
    }
    private final Map<UUID, Long> turnStartedAt = new HashMap<>();
    public void turnStarted(UUID encounter) { turnStartedAt.put(encounter, host.clock()); reanchor(encounter); }

    public void tickDeadlines() {
        requireThread();
        if (host.closing()) return;
        for (UUID failed : Set.copyOf(pendingStops)) {
            try { stop(failed); }
            catch (RuntimeException failure) {
                LOGGER.warn("Encounter {} still awaits owner cleanup", failed, failure);
            }
        }
        long now = host.clock();
        for (UUID encounterId : List.copyOf(engine.encounterIds())) {
            if (recovering(encounterId)) continue;
            StateView state = engine.stateView(encounterId);
            if (state.phase() != EncounterPhase.ACTIVE && state.phase() != EncounterPhase.CANDIDATE) continue;
            UUID current = state.current();
            if (current == null) continue;
            if (!(resolve(current) instanceof ServerPlayer player) || player.isDeadOrDying()) continue;
            long started = turnStartedAt.getOrDefault(encounterId, now);
            if (now - started < config.turnIdleSkipTicks()) continue;
            skipIdleTurn(encounterId, player);
        }
        world.expireStepDeadlines(config.executionDeadlineTicks());
        for (var evidence : List.copyOf(persistence.checkpoints().values())) {
            if (now - evidence.tick() < config.executionDeadlineTicks()) continue;
            // Only movement/approach may be force-terminated; actions complete normally.
            if (evidence.phase() != AbilityCheckpoint.Phase.APPROACH
                && evidence.phase() != AbilityCheckpoint.Phase.PREPARE) continue;
            UUID encounterId = engine.encounterOf(evidence.owner());
            if (encounterId == null || !engine.encounterIds().contains(encounterId)) continue;
            if (recovering(encounterId)) continue;
            if (engine.pendingOperation(encounterId, evidence.operation()) == null) continue;
            if (host.actionRunning(evidence.owner())) continue;
            DebugDiagnostics.log("EXECUTION_DEADLINE_STOP", () -> "encounter=" + encounterId
                + " op=" + evidence.operation() + " owner=" + evidence.owner());
            stop(encounterId);
            break;
        }
    }
    private void skipIdleTurn(UUID encounterId, ServerPlayer player) {
        try {
            if ((host.actionRunning(player.getUUID())
                || host.actionControlFault(player.getUUID())))
                host.cancelAction(player.getUUID(), "IDLE_TURN_SKIP");
            StateView state = engine.stateView(encounterId);
            if (!player.getUUID().equals(state.current()) || engine.hasPendingOperations(encounterId)) return;
            UUID operation = UUID.nameUUIDFromBytes(("dndturn:idle-skip:" + encounterId + ":" + state.round()
                + ":" + player.getUUID()).getBytes(StandardCharsets.UTF_8));
            host.endTurn(encounterId, player, operation, state.version());
            DebugDiagnostics.log("IDLE_TURN_SKIPPED", () -> "encounter=" + encounterId + " actor=" + player.getUUID());
        } catch (RuntimeException failure) {
            DebugDiagnostics.log("IDLE_TURN_SKIP_FAILED", () -> "encounter=" + encounterId
                + " actor=" + player.getUUID() + " error=" + failure.getMessage());
        }
    }
    public void expireHistory() {
        requireThread();
        long retention = config.historyRetentionTicks();
        Set<UUID> referenced = new HashSet<>();
        for (StartRequestReceipt receipt : startReceipts.values()) referenced.add(receipt.encounterId());
        for (ExitRequestReceipt receipt : exitReceipts.values()) referenced.add(receipt.encounterId());
        for (ExitAuthorizations.Grant grant : exitAuthorizations.snapshot()) referenced.add(grant.encounter());
        for (var origin : projectiles.origins().values())
            if (origin.sourceEncounterId() != null) referenced.add(origin.sourceEncounterId());
        for (var pending : projectiles.pendingSnapshot().values())
            if (pending.sourceEncounterId() != null) referenced.add(pending.sourceEncounterId());
        for (var quarantined : projectiles.quarantines().values()) referenced.add(quarantined.encounterId());
        for (UUID id : engine.encounterIds()) referenced.add(id);
        Set<UUID> removed = engine.expireClosedHistory(host.clock(), retention, referenced);
        if (!removed.isEmpty()) {
            startReceipts.values().removeIf(receipt -> removed.contains(receipt.encounterId()));
            exitReceipts.values().removeIf(receipt -> removed.contains(receipt.encounterId()));
            DebugDiagnostics.log("HISTORY_EXPIRED", () -> "encounters=" + removed.size());
            persistence.changed();
        }
        int actorRemoved = actorStates.expireHistory(host.clock(), retention);
        if (actorRemoved > 0)
            DebugDiagnostics.log("ACTOR_HISTORY_EXPIRED", () -> "records=" + actorRemoved);
        projectiles.expireOrigins(retention);
    }
    private void resolveRestoredWork(UUID encounterId) {
        for (var evidence : List.copyOf(persistence.checkpoints().values())) {
            if (!engine.canonicalEncounterId(evidence.encounter()).equals(encounterId)
                || evidence.phase() == AbilityCheckpoint.Phase.TERMINAL) continue;
            if (engine.pendingOperation(encounterId, evidence.operation()) == null) continue;
            if (evidence.phase() == AbilityCheckpoint.Phase.APPROACH
                || evidence.phase() == AbilityCheckpoint.Phase.PREPARE) {
                terminalizeRestored(encounterId, evidence, OperationRecord.Outcome.INTERRUPTED,
                    "movement/approach terminated by restart; not replayed");
            } else if (observablyApplied(evidence)) {
                terminalizeRestored(encounterId, evidence, OperationRecord.Outcome.COMPLETED,
                    "restart completed from recorded observation; effect not re-applied");
            }
        }
    }
    private void terminalizeRestored(UUID encounterId, AbilityCheckpoint evidence,
                                     OperationRecord.Outcome outcome, String reason) {
        List<UUID> operations = new ArrayList<>();
        if (evidence.action() != null) operations.add(evidence.action());
        if (evidence.movement() != null) operations.add(evidence.movement());
        operations.add(evidence.operation());
        for (UUID operation : operations) {
            if (engine.pendingOperation(encounterId, operation) == null) continue;
            try { engine.publish(encounterId, operation, 0, outcome, reason, 0, 0, true); }
            catch (RuntimeException failure) {
                DebugDiagnostics.log("RESTORED_TERMINAL_FAILED", () -> "op=" + operation
                    + " outcome=" + outcome + " reason=" + failure.getMessage());
            }
        }
    }
    private static boolean observablyApplied(AbilityCheckpoint evidence) {
        var before = evidence.before();
        var observed = evidence.observed();
        if (before == null || observed == null) return false;
        for (var previous : before.blocks())
            for (var current : observed.blocks())
                if (current.cell().equals(previous.cell()) && !current.state().equals(previous.state())) return true;
        for (var previous : before.items())
            for (var current : observed.items())
                if (current.slot() == previous.slot()
                    && (current.count() != previous.count() || current.damage() != previous.damage()
                        || !current.revision().equals(previous.revision()))) return true;
        for (var previous : before.bodies())
            for (var current : observed.bodies())
                if (current.entity().equals(previous.entity())
                    && (current.health() != previous.health()
                        || current.absorption() != previous.absorption())) return true;
        return before.spawned().isEmpty() && !observed.spawned().isEmpty();
    }
    public void close() {
        stopping.clear(); pendingStops.clear(); recoveryPending.clear();
        turnStartedAt.clear(); pendingDeaths.clear(); completedPlayerDeaths.clear(); departuresDuringWorldOutcome.clear();
    }

    private void endIfNoPlayersRemain(UUID encounterId) {
        if (!engine.encounterIds().contains(encounterId)) return;
        for (UUID member : engine.stateView(encounterId).members().keySet()) {
            if (server.getPlayerList().getPlayer(member) != null) return;
        }
        stop(encounterId);
    }
}
