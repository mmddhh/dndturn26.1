package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.ability.ProcessState;
import cc.sighs.dndturn.domain.encounter.operation.CausalEvent;
import cc.sighs.dndturn.domain.encounter.operation.Intervention;
import cc.sighs.dndturn.platform.server.action.ExecutionProcesses;
import cc.sighs.dndturn.platform.server.ai.MovementPorts;
import cc.sighs.dndturn.platform.server.damage.BodyTargets;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.TriggeredAbilityInvocation;
import cc.sighs.dndturn.domain.control.GateDecision;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.encounter.ConsentWindow;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.EncounterRegion;
import cc.sighs.dndturn.domain.encounter.EncounterStateSnapshot;
import cc.sighs.dndturn.domain.encounter.StartDisposition;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.operation.DamageTrace;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.operation.ProjectileOrigin;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.domain.resolution.AttackResolution;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import cc.sighs.dndturn.platform.mixin.server.damage.ArrowDamageAccessor;
import cc.sighs.dndturn.platform.mixin.server.lifecycle.LivingEntityDeathAccessor;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.observation.ItemStackFingerprint;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.projection.PresentationState;
import cc.sighs.dndturn.platform.projection.PresentationUseIdentity;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.ability.AbilityExecutor;
import cc.sighs.dndturn.platform.server.action.AbilityCheckpoint;
import cc.sighs.dndturn.platform.server.action.AbilityWorkBudget;
import cc.sighs.dndturn.platform.server.action.ActionExecutionCoordinator;
import cc.sighs.dndturn.platform.server.action.ActionHost;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.action.MinecraftCoordinates;
import cc.sighs.dndturn.platform.server.action.MinecraftProjectiles;
import cc.sighs.dndturn.platform.server.action.TacticalLaunchContext;
import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import cc.sighs.dndturn.platform.server.ai.MobTurnStrategies;
import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.control.ControlFacts;
import cc.sighs.dndturn.platform.server.control.ExitAuthorizations;
import cc.sighs.dndturn.platform.server.control.OperationAdmissionPolicy;
import cc.sighs.dndturn.platform.server.control.SimulationPolicy;
import cc.sighs.dndturn.platform.server.control.WorldOutcomePolicy;
import cc.sighs.dndturn.platform.server.damage.DamageReceivers;
import cc.sighs.dndturn.platform.server.damage.MeleeAdapters;
import cc.sighs.dndturn.platform.server.damage.TacticalDamageContext;
import cc.sighs.dndturn.platform.server.effect.TriggeredAbilities;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectHost;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController;
import cc.sighs.dndturn.platform.server.inspection.InspectionService;
import cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope;
import cc.sighs.dndturn.platform.server.persistence.CombatSavedData;
import cc.sighs.dndturn.platform.server.persistence.EncounterPersistence;
import cc.sighs.dndturn.platform.server.runtime.MinecraftCombatRuntime;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import cc.sighs.dndturn.platform.server.world.CloudOrigins;
import cc.sighs.dndturn.platform.server.world.EnvironmentScheduler;
import cc.sighs.dndturn.platform.server.world.RegionalScheduledTicks;
import cc.sighs.dndturn.platform.server.world.SimulationPreparation;
import cc.sighs.dndturn.platform.server.world.WorldOutcomeHost;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Coordinates encounter lifecycle and live platform owners for one server instance. */
public final class EncounterRuntime implements RegionalScheduledTicks.RegionAccess {
    private final ActionHost actionHost = new ActionHost() {
        public void recordReleaseReconciliation(UUID operation, boolean retired) { EncounterRuntime.this.recordReleaseReconciliation(operation, retired); }
        public void recordAbilityCheckpoint(AbilityCheckpoint evidence) { EncounterRuntime.this.recordAbilityCheckpoint(evidence); }
        public long planClock() { return EncounterRuntime.this.planClock(); }
        public void drainImmediateTriggers(LiveActorContext actor, UUID encounter, UUID parent) { EncounterRuntime.this.drainImmediateTriggers(actor, encounter, parent); }
        public void finishPlanMovement(ServerPlayer player) { EncounterRuntime.this.finishPlanMovement(player); }
        public void finishPlanMovement(UUID owner) { EncounterRuntime.this.finishPlanMovement(owner); }
        public void finishPlanMovement(UUID owner, UUID operation) { EncounterRuntime.this.finishPlanMovement(owner, operation); }
        public void beginMobPlanMovement(Mob mob, OperationRecord.Snapshot root, UUID operation, GridCell goal) { EncounterRuntime.this.beginMobPlanMovement(mob, root, operation, goal); }
        public void observeMobPlanMovement(Mob mob, UUID operation) { EncounterRuntime.this.observeMobPlanMovement(mob, operation); }
        public void beginPlanMovement(ServerPlayer player, UUID parent, UUID operation) { EncounterRuntime.this.beginPlanMovement(player, parent, operation); }
        public void joinGeneratedMob(ServerPlayer owner, Mob mob, UUID encounter) { EncounterRuntime.this.joinGeneratedMob(owner, mob, encounter); }
        public boolean maySubmitPlan(LiveActorContext actor) { return EncounterRuntime.this.maySubmitPlan(actor); }
        public OperationRecord.Result attackPlan(LiveActorContext context, UUID target, UUID operation, UUID parent,
                                     java.util.function.Consumer<OperationRecord.Result> complete) { return EncounterRuntime.this.attackPlan(context, target, operation, parent, complete); }
        public DamageTrace rangedTrace(LivingEntity player, UUID targetId, UUID operation, boolean opening) { return EncounterRuntime.this.rangedTrace(player, targetId, operation, opening); }
        public boolean ownsItemProjectile(ServerPlayer player,net.minecraft.world.entity.projectile.Projectile projectile) { return EncounterRuntime.this.ownsItemProjectile(player, projectile); }
    };
    public ActionHost actionHost() { requireThread(); return actionHost; }

    private final WorldOutcomeHost worldOutcomes = new WorldOutcomeHost() {
        public UUID canonicalOutcomeEncounter(UUID encounter) { return EncounterRuntime.this.canonicalOutcomeEncounter(encounter); }
        public void completeWorldOutcome(Runnable completion) { EncounterRuntime.this.completeWorldOutcome(completion); }
    };
    public WorldOutcomeHost worldOutcomes() { requireThread(); return worldOutcomes; }

    private final VanillaEffectHost vanillaEffects = new VanillaEffectHost() {
        public VanillaEffectRoundController participantEffects() { return EncounterRuntime.this.participantEffects(); }
        public void participantEffectsChanged() { EncounterRuntime.this.participantEffectsChanged(); }
        public void releaseParticipantEffects(Entity entity) { EncounterRuntime.this.releaseParticipantEffects(entity); }
    };
    public VanillaEffectHost vanillaEffects() { requireThread(); return vanillaEffects; }

    private final ControlFacts controlFacts = new ControlFacts() {
        public GateDecision.Evidence controlEvidence(Entity entity) { return EncounterRuntime.this.controlEvidence(entity); }
        public OperationAdmissionPolicy.Facts captureAdmission(LivingEntity actor, boolean rejectRunning) { return EncounterRuntime.this.captureAdmission(actor, rejectRunning); }
        public SimulationPolicy.EntityFacts captureEntitySimulation(Entity entity) { return EncounterRuntime.this.captureEntitySimulation(entity); }
        public boolean cloudSimulationPaused(Entity cloud) { return EncounterRuntime.this.cloudSimulationPaused(cloud); }
        public boolean cloudTargetAllowed(Entity cloud, LivingEntity target) { return EncounterRuntime.this.cloudTargetAllowed(cloud, target); }
    };
    public ControlFacts controlFacts() { requireThread(); return controlFacts; }

    private static final Logger LOGGER = LogUtils.getLogger();

    private boolean closing;

    private final MinecraftServer server;
    private final UUID generation;
    private final ServerCombatConfig config;
    private final EncounterAuthority engine;
    private final ServerConsentCoordinator consent;
    private final ServerEntityProjections entityProjections;
    private final ProjectionPublisher projections;
    private final EncounterPersistence persistence;
    private final ActorStateAuthority actorStates;
    private final InspectionService inspections = new InspectionService(this);
    public InspectionService inspections() { requireThread(); return inspections; }
    public ActorStateAuthority actorStates() { requireThread(); return actorStates; }
    private long cumulativeServerTicks;
    private final Map<UUID, CombatPersistenceEnvelope.ProjectileAbility> projectileAbilities = new HashMap<>();
    public AbilityCheckpoint abilityCheckpoint(UUID operation) { requireThread(); return persistence.checkpoint(operation); }
    private final Map<UUID, CombatPersistenceEnvelope.MergeBinding> mergeBindings = new HashMap<>();
    void recordReleaseReconciliation(UUID operation, boolean retired) {
        requireThread();
        persistence.appendAudit(new CombatPersistenceEnvelope.RecoveryAudit(operation, generation, cumulativeServerTicks,
            ActionFailure.Code.NONE, retired ? "old instance retired; server control revoked" : "owned control release reconciled"));
    }
    void recordAbilityCheckpoint(AbilityCheckpoint evidence) {
        requireThread();
        persistence.recordCheckpoint(evidence);
    }
    private final List<CombatPersistenceEnvelope.LeaseEvidence> restoredLeaseEvidence = new ArrayList<>();
    private final Set<UUID> recoveryPending = new HashSet<>();
    private final Random attackRandom = new Random();
    private final Map<UUID, Random> gameTestAttackRandom = new HashMap<>();
    /** Exact region snapshots are a query projection; EncounterAuthority owns membership and phase. */
    private final Map<UUID, EncounterRegion> regions = new HashMap<>();
    private final Map<UUID, CombatPersistenceEnvelope.CapturedSettings> capturedSettings = new HashMap<>();
    private final Map<UUID, Set<Long>> regionChunks = new HashMap<>();
    private final Set<UUID> departuresDuringWorldOutcome = new HashSet<>();
    private final Set<UUID> pendingDeaths = new HashSet<>();
    private final Map<UUID, UUID> completedPlayerDeaths = new HashMap<>();
    private ActionExecutionCoordinator tacticalActions;
    private final VanillaEffectRoundController participantEffects;
    private final ExecutionProcesses processes;
    public ExecutionProcesses processes() { requireThread(); return processes; }
    VanillaEffectRoundController participantEffects() { return participantEffects; }
    void participantEffectsChanged() { persistence.changed(); }
    void releaseParticipantEffects(Entity entity) {
        if (ServerRuntime.existingEncounter(server) == this && !closing && worldOutcomeDepth == 0 && entity instanceof LivingEntity living && engine.encounterOf(entity.getUUID()) != null)
            participantEffects.release(living);
    }
    public ActionExecutionCoordinator tacticalActions() {
        requireThread();
        if (tacticalActions == null) tacticalActions = new ActionExecutionCoordinator(this, server, engine);
        return tacticalActions;
    }
    long planClock() { return cumulativeServerTicks; }
    UUID canonicalOutcomeEncounter(UUID encounter) { requireThread(); return encounter == null ? null : engine.canonicalEncounterId(encounter); }
    void completeWorldOutcome(Runnable completion) {
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
    void drainImmediateTriggers(LiveActorContext actor, UUID encounter, UUID parent) {
        requireThread();
        var operation = engine.pendingOperation(encounter, parent);
        if (operation != null) new TriggeredAbilities(this, engine).drain(actor, operation,
                TriggeredAbilityInvocation.Timing.IMMEDIATE);
    }
    /** Scoped pre-write boundary; the damage driver retains ownership of the original hurt call. */
    public boolean cancelDamageAttempt(LivingEntity target, net.minecraft.world.damagesource.DamageSource source,
                                       float amount, UUID operationId) {
        requireThread();
        UUID encounter = engine.encounterOf(target.getUUID());
        if (encounter == null || operationId == null) return false;
        var operation = engine.pendingOperation(encounter, operationId);
        if (operation == null || !target.getUUID().equals(operation.target())) return false;
        UUID eventId = UUID.nameUUIDFromBytes(("damage-attempt:" + operationId + ":" + target.getUUID())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var event = new CausalEvent(eventId, operationId,
                engine.operationRoot(encounter, operationId), encounter, engine.stateView(encounter).version(),
                operation.owner(), target.getUUID(), CausalEvent.Phase.ATTEMPT,
                new CausalEvent.Damage(amount,
                        source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE),
                        source.getDirectEntity() == null ? null : source.getDirectEntity().getUUID()));
        var actor = new LiveActorContext(target);
        actorStates().damageAttempt(actor, event);
        var decision = new TriggeredAbilities(this, engine).drainAttempt(actor, operation, event);
        actor.verifyCurrent();
        if (!operation.equals(engine.pendingOperation(encounter, operationId)))
            throw new IllegalStateException("damage operation invalidated by reaction");
        return decision.disposition() == Intervention.Disposition.CANCEL;
    }
    public Entity damageBody(LivingEntity actor, UUID operationId) {
        requireThread();
        var encounter = engine.encounterOf(actor.getUUID());
        if (encounter == null || operationId == null) return actor;
        var operation = engine.pendingOperation(encounter, operationId);
        if (operation == null || !actor.getUUID().equals(operation.target())) return actor;
        var intent = engine.operationIntent(encounter, operationId);
        if (intent == null || intent.target().facet() == null) return actor;
        if (!actor.getUUID().equals(intent.target().entity())) throw new IllegalStateException("facet actor differs from damage target");
        var hit = BodyTargets.resolve(actor, intent.target().facet());
        if (DamageReceivers.server().find(hit) == null) throw new IllegalStateException("facet receiver unsupported");
        return hit.body();
    }
    boolean admitPlan(OperationRecord.Snapshot root) {
        requireThread();
        if (root.kind() != OperationRecord.Kind.PLAN || !generation.equals(root.observationEpoch()))
            throw new IllegalArgumentException("current generation plan required");
        return engine.beginOperation(root);
    }
    void finishPlanMovement(ServerPlayer player) { finishPlayerMove(player); }
    void finishPlanMovement(UUID owner) { var lease = playerMoves.get(owner); if (lease != null) closePlayerMove(lease); }
    void finishPlanMovement(UUID owner, UUID operation) {
        var lease = playerMoves.get(owner);
        if (operation != null && lease != null && operation.equals(lease.operationId)) closePlayerMove(lease);
        var mobLease = mobMoves.get(owner);
        if (operation != null && mobLease != null && operation.equals(mobLease.operationId)) closeMobMove(mobLease, "plan movement finished");
    }
    /** Navigation is a child of an admitted PLAN, with the existing observed movement ledger. */
    void beginMobPlanMovement(Mob mob, OperationRecord.Snapshot root, UUID operation, GridCell goal) {
        var context = new LiveActorContext(mob);
        context.verifyCurrent();
        if (!root.owner().equals(context.id()) || root.kind() != OperationRecord.Kind.PLAN
            || !root.equals(engine.pendingOperation(root.encounterId(), root.operationId())))
            throw new IllegalStateException("admitted owner plan required");
        if (mob.isNoAi() || mob.isPassenger() || mob.hasControllingPassenger()
            || MovementPorts.find(mob) == null)
            throw new IllegalStateException("Mob ground movement port unavailable");
        var snapshot = new OperationRecord.Snapshot(operation, root.operationId(), root.encounterId(), mob.getUUID(), mob.getUUID(),
            null, cumulativeServerTicks, engine.stateView(root.encounterId()).version(), root.sourceCell(), goal,
            OperationRecord.Kind.MOVE, generation);
        if (!engine.beginPlanStep(snapshot)) throw new IllegalStateException("Mob plan movement rejected");
        var lease = new MobMoveLease(root.encounterId(), operation, mob);
        mobMoves.put(mob.getUUID(), lease);
        try {
            var port = MovementPorts.require(mob);
            lease.driver = Objects.requireNonNull(port.port().start(mob, goal), "movement driver");
            lease.pathStarted = true;
        } catch (RuntimeException failure) {
            closeMobMove(lease, "movement port could not start");
            throw failure;
        }
    }
    /** Observe one vanilla body tick; the parent plan, never this driver, decides what happens next. */
    void observeMobPlanMovement(Mob mob, UUID operation) {
        var lease = mobMoves.get(mob.getUUID());
        if (lease == null || !lease.operationId.equals(operation)) return;
        if (!activeMobLease(lease)) { revokeMobLease(lease); return; }
        if (lease.ticked) {
            lease.ticked = false;
            var observation = settleMobDisplacement(mob, lease, engine.stateView(lease.encounterId), lease.driver != null && lease.driver.owns(mob));
            if (observation == MobObservation.NO_DISPLACEMENT) lease.stalled++;
        }
        if (lease.budgetBlocked || lease.stalled >= 5 || lease.driver == null || !lease.driver.owns(mob) || lease.driver.done(mob))
            closeMobMove(lease, "navigation completed or interrupted");
    }
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
    private final Map<UUID, MobMoveLease> mobMoves = new HashMap<>();
    private record StartRequestReceipt(UUID owner, UUID encounterId) {}
    private final Map<UUID, StartRequestReceipt> startReceipts = new HashMap<>();
    private record ExitRequestReceipt(UUID owner, UUID encounterId, long expectedVersion) {}
    private final Map<UUID, ExitRequestReceipt> exitReceipts = new HashMap<>();
    private final ExitAuthorizations exitAuthorizations = new ExitAuthorizations();
    /** The END intent observes a later version than its MOVE_BEGIN operation snapshot. */
    private record MoveEndReceipt(UUID owner, UUID encounterId, long expectedVersion) {}
    private final Map<UUID, MoveEndReceipt> moveEndReceipts = new HashMap<>();
    private final Map<UUID, EncounterAuthority.MergePlan> pendingMerges = new HashMap<>();
    private final Map<UUID, String> mergeFailures = new HashMap<>();
    /** Value evidence survives departure of the owner; simulation domain is a scheduler projection. */
    private final Map<UUID, ProjectileOrigin> projectileOrigins = new HashMap<>();
    private final Map<UUID, UUID> projectileSimulationDomains = new HashMap<>();
    private final Set<UUID> retainedProjectileHistory = new HashSet<>();
    /** A verified collision waits here until its source and target domains merge. */
    private record PendingProjectileAttack(UUID targetId, UUID sourceEncounterId) {}
    private final Map<UUID, PendingProjectileAttack> pendingProjectileAttacks = new HashMap<>();
    private final Map<UUID, CombatPersistenceEnvelope.QuarantinedProjectile> quarantinedProjectiles = new HashMap<>();
    private final EnvironmentScheduler environment;
    private final RegionalScheduledTicks scheduledTicks = new RegionalScheduledTicks(this);
    private int worldOutcomeDepth;

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
        final UUID ownerInstance;
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
        MovementPorts.Driver driver;

        MobMoveLease(UUID encounterId, UUID operationId, Mob mob) {
            this.encounterId = encounterId;
            this.operationId = operationId;
            this.mobId = mob.getUUID();
            this.ownerInstance = ((PresentationIdentity)mob).dndturn$presentationInstance();
            this.previous = mob.position();
            this.previousGrounded = mob.onGround();
            this.previousWater = mob.isInWater();
        }
    }

    private enum MobObservation { NO_DISPLACEMENT, DISPLACED, TERMINAL_COMPLETED, TERMINAL_UNKNOWN }

    public EncounterRuntime(MinecraftServer server, ActorStateAuthority actorStates, UUID generation) {
        this(server, server.getDataStorage().computeIfAbsent(CombatSavedData.TYPE), actorStates, generation);
    }

    private EncounterRuntime(MinecraftServer server, CombatSavedData savedData, ActorStateAuthority actorStates, UUID generation) {
        this.server = server;
        this.generation = java.util.Objects.requireNonNull(generation);
        this.config = ServerCombatConfig.load(server);
        this.consent = new ServerConsentCoordinator(server, generation, this::isMember,
            this::sampleConsentRegion, this::commitConsentedEncounter);
        this.entityProjections = new ServerEntityProjections(server, generation, this::presentationFacts);
        this.actorStates = java.util.Objects.requireNonNull(actorStates);
        var opened = EncounterPersistence.open(savedData, config.roundTicks());
        this.persistence = opened.persistence();
        this.engine = opened.authority();
        this.processes = new ExecutionProcesses(this::requireThread,
                persistence::changed, persistence.processes());
        CombatPersistenceEnvelope saved = opened.checkpoint();
        this.environment = new EnvironmentScheduler(engine);
        this.engine.bindAbilityDefinitions(AbilityAdapterRegistry.definitions());
        this.engine.bindActorTurnObserver(boundary -> actorStates.turnStarted(boundary.encounter(), boundary.round(), boundary.actor()));
        this.engine.bindObservationEpoch(generation);
        this.participantEffects = new VanillaEffectRoundController(this,engine);
        this.projections = new ProjectionPublisher(server, engine, generation, this::resolve);
        if (!persistence.recoveryFailed() && saved != null) {
            cumulativeServerTicks = saved.cumulativeServerTicks();
            projections.restore(saved.nextSessionSequence(), saved.sessionSequences());
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
            for (var entry : saved.moveEndReceipts().entrySet())
                moveEndReceipts.put(entry.getKey(), new MoveEndReceipt(
                    entry.getValue().owner(), entry.getValue().encounterId(),
                    entry.getValue().expectedVersion()));
            restoredLeaseEvidence.addAll(saved.leases());
            mergeBindings.putAll(saved.mergeBindings());
            projectileAbilities.putAll(saved.projectileAbilities());
            participantEffects.restore(saved.participantEffects(),saved.turnSettlements());
            exitAuthorizations.restore(saved.exitAuthorizations());
            capturedSettings.keySet().retainAll(engine.encounterIds());
            for (UUID encounterId : engine.encounterIds()) {
                engine.revokeRestoredPermits(encounterId);
                recoveryPending.add(encounterId);
            }
            if (!recoveryPending.isEmpty())
                persistence.changed();
        }
        if (!persistence.recoveryFailed()) for (UUID encounterId : engine.encounterIds()) {
            EncounterRegion region = engine.stateView(encounterId).region();
            if (region != null) {
                regions.put(encounterId, region);
                regionChunks.put(encounterId, exactRegionChunks(region));
                projections.bind(encounterId, projections.nextSequence());
            }
        }
    }

    /** Exercises production recovery with isolated data; never registers a running service. */
    public static EncounterRuntime restoreForGameTest(MinecraftServer server, CombatSavedData savedData) {
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer) || !server.isSameThread())
            throw new IllegalStateException("isolated recovery requires the GameTest server thread");
        return new EncounterRuntime(server, savedData, new ActorStateAuthority(server), UUID.randomUUID());
    }

    public void close() {
        requireThread();
        if (closing) return;
        this.closing = true;
        try {
            // Save running state and pending evidence, not artificially ended encounters.
            this.persistNow();
        } finally {
            try {
                RuntimeException releaseFailure = null;
                for (MobMoveLease lease : List.copyOf(this.mobMoves.values())) {
                    try { this.revokeMobLease(lease); }
                    catch (RuntimeException failure) {
                        if (releaseFailure == null) releaseFailure = failure;
                        else releaseFailure.addSuppressed(failure);
                    }
                }
                if (releaseFailure != null) throw releaseFailure;
            } finally {
                try { this.scheduledTicks.releaseAll(); }
                finally {
                    this.playerMoves.clear();
                    this.regions.clear();
                    this.regionChunks.clear();
                    this.projections.close();
                    this.entityProjections.clear();
                    this.environment.close();
                }
            }
        }
    }

    public void persistIfChanged() {
        requireThread();
        actorStates.persist();
        if (persistence.recoveryFailed()) return;
        if (ServerRuntime.existingEncounter(server)==this) for (UUID id:engine.encounterIds()) {
            if(recoveryPending.contains(id)) continue;
            for(UUID member:engine.stateView(id).members().keySet())
                if(resolve(member) instanceof LivingEntity living) participantEffects.capture(living);
        }
        persistence.persistIfChanged(engine, cumulativeServerTicks, this::captureCheckpoint);
    }

    private CombatPersistenceEnvelope captureCheckpoint(EncounterStateSnapshot rules) {
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
        return new CombatPersistenceEnvelope(rules, cumulativeServerTicks, projections.lastSequence(),
            projections.sessions(), capturedSettings, projectileOrigins,
            projectileSimulationDomains, pendingArrows,
            pendingMerges.values().stream()
                .map(EncounterStateSnapshot.MergePlanState::capture).toList(),
            starts, exits, moveEnds, leases, quarantinedProjectiles, exitAuthorizations.snapshot(), persistence.checkpoints(), mergeBindings, projectileAbilities, persistence.audits(),
            participantEffects.owners(),participantEffects.settlements(), processes.snapshot());
    }

    private void persistNow() {
        persistence.changed();
        persistIfChanged();
    }

    public void advancePersistenceClock() {
        requireThread();
        cumulativeServerTicks = Math.addExact(cumulativeServerTicks, 1);
        actorStates.endServerTick();
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

    public UUID activeEnvironmentStep(ServerLevel level) { return environment.step(level); }
    /** The primary retains its periodic clock on merge; clocks are never summed. */
    public long environmentTimeAt(ServerLevel level, BlockPos pos) {
        UUID domain = encounterAtBlock(level, pos);
        if (domain == null) return level.getGameTime();
        long time = engine.environmentTime(domain);
        return domain.equals(environment.domain(level)) && environment.step(level) != null
            ? Math.addExact(time, 1) : time;
    }
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
                && (((LivingEntityDeathAccessor) living).dndturn$isDead()
                    || living instanceof ServerPlayer && !living.isAlive()
                        && ((PresentationIdentity) living).dndturn$presentationInstance().equals(completedInstance))) {
                actorStates.death(entityId);
                leave(entityId);
            }
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
        if (ServerRuntime.existingEncounter(server) != this) return;
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
        environment.begin(level, candidates, encounterId -> {
            var state = engine.stateView(encounterId);
            boolean loadedMember = state.members().keySet().stream().anyMatch(id -> level.getEntity(id) != null);
            return loadedMember && regionChunksLoaded(level, regionChunks.getOrDefault(encounterId, Set.of()));
        });
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
                AbilityAdapterRegistry.resolve(evidence.invocation());
                if (!(level.getEntity(evidence.owner()) instanceof LivingEntity body)) {
                    code = ActionFailure.Code.SOURCE_INVALID; reason = "actor unavailable; no chunks loaded; action not replayed";
                } else if (!evidence.ownerInstance().equals(((PresentationIdentity)body).dndturn$presentationInstance())) {
                    code = ActionFailure.Code.SOURCE_INVALID; reason = "actor instance replaced; old lease not restored";
                } else {
                    var owned = AbilityAdapterRegistry.facts(evidence.invocation()).source(new LiveActorContext(body), evidence.invocation().hand());
                    if (!java.util.Objects.equals(owned, evidence.invocation().source())) {
                        code = ActionFailure.Code.SOURCE_INVALID; reason = "source changed; confirmed effects retained; action not replayed";
                    }
                }
            } catch (RuntimeException unavailable) {
                code = ActionFailure.Code.ABILITY_UNAVAILABLE; reason = "ability version or source adapter unavailable; action not replayed";
            }
            persistence.appendAudit(new CombatPersistenceEnvelope.RecoveryAudit(evidence.operation(), generation,
                cumulativeServerTicks, code, reason));
        }
        for (var entry : projectileAbilities.entrySet()) {
            var origin = projectileOrigins.get(entry.getKey());
            if (origin == null || origin.sourceEncounterId() == null
                || !engine.canonicalEncounterId(origin.sourceEncounterId()).equals(encounter)) continue;
            try { AbilityAdapterRegistry.resolve(entry.getValue().invocation()); }
            catch (RuntimeException unavailable) {
                quarantineProjectile(entry.getKey(), encounter, entry.getValue().invocation().target().entity(),
                    "launch ability version unavailable; delayed effect cannot be replayed");
            }
        }
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
            EncounterAuthority.StateView state = engine.stateView(encounterId);
            if (!dimension.equals(state.region().dimension())) continue;
            boolean regionLoaded = regionChunksLoaded(level, regionChunks.getOrDefault(encounterId, Set.of()));
            boolean allMembersPresent = state.members().keySet().stream()
                .allMatch(id -> level.getEntity(id) instanceof LivingEntity member && member.isAlive());
            String behaviorRecovery = engine.exportSnapshot().encounters().stream().filter(e -> e.id().equals(encounterId))
                .flatMap(e -> e.pending().values().stream()).filter(e -> e.intent() != null)
                .map(e -> AbilityAdapterRegistry.recoveryReason(e.intent())).distinct().collect(java.util.stream.Collectors.joining("; "));
            auditAbilityRecovery(level, encounterId);
            int unknown = engine.failRestoredWork(encounterId, cumulativeServerTicks,
                behaviorRecovery.isEmpty() ? "restart evidence could not prove previous world effect" : behaviorRecovery);
            for(UUID owner:state.members().keySet()) if(level.getEntity(owner) instanceof LivingEntity living
                && !participantEffects.reconcile(living)) {
                engine.recordRestoredUnknown(encounterId,UUID.randomUUID(),owner,owner,owner,cumulativeServerTicks,
                    "participant effect/native save order mismatch; prior settlement is not replayed");
                unknown++;
            }
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
                projections.nextRevision(encounterId);
                if (ServerRuntime.existingEncounter(server) == this) scheduledTicks.captureAll(level);
                recoveryPending.remove(encounterId);
                sync(engine.stateView(encounterId));
            }
            recoveryPending.remove(encounterId);
        }
    }

    private void scheduleMerge(UUID seed) {
        queueMerge(engine.planMerge(seed));
    }

    private void queueMerge(EncounterAuthority.MergePlan plan) {
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
            if (binding.phase() == CombatPersistenceEnvelope.MergePhase.BOUND || !engine.encounterIds().contains(binding.primary())) continue;
            // PREPARED is not proof that the rules committed. Only aliases/result evidence can establish that.
            boolean committed = binding.sources().stream().allMatch(id -> engine.canonicalEncounterId(id).equals(binding.primary()));
            if (!committed) continue;
            var region = engine.stateView(binding.primary()).region();
            if (!region.dimension().equals(level.dimension().identifier().toString())) continue;
            if (!regionChunksLoaded(level, exactRegionChunks(region)) || !scheduledTicks.canRebindMerge(binding.sources(), region))
                throw new IllegalStateException("committed merge awaits loaded owner bindings; simulation cannot advance");
            bindMergedOwners(level, binding, region);
        }
    }
    private void bindMergedOwners(ServerLevel level, CombatPersistenceEnvelope.MergeBinding binding, EncounterRegion region) {
        projections.bind(binding.primary(), binding.sequence());
        for (UUID id : binding.sources()) {
            if (id.equals(binding.primary())) continue;
            EncounterAuthority.View closed = engine.closedView(id);
            regions.remove(id); capturedSettings.remove(id); regionChunks.remove(id);
            gameTestAttackRandom.remove(id);
            if (closed != null) for (UUID member : closed.members().keySet()) clear(member, id, closed.version());
        }
        regions.put(binding.primary(), region);
        regionChunks.put(binding.primary(), exactRegionChunks(region));
        scheduledTicks.rebindMerge(binding.sources(), binding.primary(), region);
        exitAuthorizations.reconcile(engine, this::resolve);
        pendingMerges.remove(binding.primary());
        mergeFailures.remove(binding.primary());
        mergeBindings.put(binding.primary(), binding.at(CombatPersistenceEnvelope.MergePhase.BOUND));
        persistNow();
    }

    /** Runs before any scheduled queue or world callback sees the newly merged projection. */
    private void attemptPendingMerges(ServerLevel level) {
        repairMergeBindings(level);
        for (EncounterAuthority.MergePlan plan : List.copyOf(pendingMerges.values())) {
            if (!plan.equals(pendingMerges.get(plan.primary()))) continue;
            if (!engine.encounterIds().containsAll(plan.encounters())) {
                pendingMerges.remove(plan.primary());
                mergeFailures.remove(plan.primary());
                continue;
            }
            EncounterAuthority.StateView primary = engine.stateView(plan.primary());
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
                EncounterAuthority.StateView ready = engine.stateView(working.primary());
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
                long mergedProjectionSequence = projections.nextSequence();
                UUID mergeOperation = UUID.randomUUID();
                var binding = new CombatPersistenceEnvelope.MergeBinding(mergeOperation, working.primary(), working.encounters(),
                    CombatPersistenceEnvelope.MergePhase.PREPARED, mergedProjectionSequence);
                mergeBindings.put(working.primary(), binding);
                persistNow();
                engine.commitMerge(working, sampled,
                    mergeOperation, cumulativeServerTicks);
                ruleCommitted = true;
                mergeBindings.put(working.primary(), binding.at(CombatPersistenceEnvelope.MergePhase.RULES_COMMITTED));
                persistNow();
                ruleCommitted = true;
                bindMergedOwners(level, binding, sampled);
                projectionCommitted = true;
                pendingMerges.remove(working.primary());
                mergeFailures.remove(working.primary());
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
            scheduledTicks.beforeBlockQueue(level, environment.domain(level));
    }

    public void beforeFluidQueue(ServerLevel level) {
        requireThread();
        if (!candidateRegionChunks(level).isEmpty())
            scheduledTicks.beforeFluidQueue(level, environment.domain(level));
    }

    /** Reached only after the normal ServerLevel tick returned through all world stages. */
    public void afterLevelTick(ServerLevel level) {
        requireThread();
        UUID encounterId = environment.complete(level);
        if (encounterId != null) {
            sync(engine.stateView(encounterId));
            syncBodyStateTransitions();
        }
    }

    /** Called by the outer vanilla world-tick exception boundary, before it reports the crash. */
    public void abortLevelTick(ServerLevel level, Throwable failure) {
        requireThread();
        UUID encounterId = environment.abort(level, cumulativeServerTicks, failure);
        if (encounterId != null) stop(encounterId);
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
            // Level.shouldTickBlocksAt only checks the distance-manager range. The native
            // BoundTickingBlockEntity also requires BLOCK_TICKING and entity storage loaded.
            // Do not spend an environment step while that inner vanilla gate will skip it.
            var loaded = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (loaded == null || !level.shouldTickBlocksAt(chunk) || !level.areEntitiesLoaded(chunk)
                    || !loaded.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.BLOCK_TICKING)) return false;
        }
        return true;
    }

    /** A movement lease permits only vanilla movement packets, not body simulation or other input. */
    public boolean hasPlayerMoveLease(UUID playerId) { return playerMoves.containsKey(playerId); }
    /** Diagnostic capture is not a lease validation or a transferable credential. */
    GateDecision.Evidence controlEvidence(Entity entity) {
        UUID encounter = engine.encounterOf(entity.getUUID());
        var mobLease = mobMoves.get(entity.getUUID());
        return new GateDecision.Evidence(generation, encounter,
            encounter == null ? null : engine.stateView(encounter).version(),
            entity instanceof PresentationIdentity identity ? identity.dndturn$presentationInstance() : null,
            mobLease == null ? null : mobLease.operationId, environment.step(entity.level()), cumulativeServerTicks);
    }
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
        Entity current = resolve(lease.mobId);
        if (!(current instanceof Mob) || !lease.ownerInstance.equals(((PresentationIdentity)current).dndturn$presentationInstance())) return false;
        EncounterAuthority.StateView state = engine.stateView(lease.encounterId);
        return (state.phase() == EncounterPhase.ACTIVE || state.phase() == EncounterPhase.CANDIDATE)
            && lease.mobId.equals(state.current())
            && state.members().containsKey(lease.mobId)
            && engine.pendingOperation(lease.encounterId, lease.operationId) != null;
    }

    private void revokeMobLease(MobMoveLease lease) {
        if (mobMoves.remove(lease.mobId, lease)) {
            stopMobNavigation(lease);
        }
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
        EncounterAuthority.StateView state = engine.stateView(encounterId);
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
        EncounterAuthority.StateView state = engine.stateView(lease.encounterId);
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
    private final AbilityWorkBudget abilityWork = new AbilityWorkBudget();
    private long decisionCursor;
    public boolean takeAbilityWork(UUID actor, UUID encounter, AbilityWorkBudget.Work work) { requireThread(); return abilityWork.take(cumulativeServerTicks, actor, encounter, work); }
    public void requireAbilityWork(UUID actor, UUID encounter, AbilityWorkBudget.Work work) { requireThread(); abilityWork.require(cumulativeServerTicks, actor, encounter, work); }
    public Map<AbilityWorkBudget.Work, AbilityWorkBudget.Counts> abilityWorkCounts() { requireThread(); return abilityWork.counters(); }
    private final MobTurnStrategies.Decisions mobDecisions = new MobTurnStrategies.Decisions();
    /** Successful insertion evidence is supplied by the bounded item execution scope. */
    void joinGeneratedMob(ServerPlayer owner, Mob mob, UUID encounter) {
        requireThread();
        if (!encounter.equals(engine.encounterOf(owner.getUUID())) || mob.level() != owner.level()
            || mob.isRemoved() || !mob.isAlive()) throw new IllegalStateException("spawn membership context changed");
        var center = mob.getBoundingBox().getCenter();
        if (!engine.stateView(encounter).region().containsPoint(center.x, center.y, center.z))
            throw new IllegalStateException("spawn outside encounter");
        UUID existing = engine.encounterOf(mob.getUUID());
        if (existing != null && !existing.equals(encounter)) throw new IllegalStateException("spawn already belongs to another encounter");
        if (engine.join(encounter, mob.getUUID())) {
            participantEffects.capture(mob);
            persistence.changed();
            sync(engine.stateView(encounter));
        }
    }
    public void advanceMobTurns() {
        requireThread();
        if (server.tickRateManager().isFrozen()) return;
        mobDecisions.retain(engine.encounterIds());
        var decisionOrder = new ArrayList<>(engine.encounterIds());
        decisionOrder.sort(UUID::compareTo);
        if (!decisionOrder.isEmpty()) java.util.Collections.rotate(decisionOrder, -(int)Math.floorMod(decisionCursor++, decisionOrder.size()));
        for (UUID encounterId : decisionOrder) {
            if (recoveryPending.contains(encounterId) || tacticalActions().runningIn(encounterId)) continue;
            var state = engine.stateView(encounterId);
            if (state.phase() == EncounterPhase.ENVIRONMENT || state.current() == null) continue;
            if (!(resolve(state.current()) instanceof Mob mob) || tacticalActions().controlFault(mob.getUUID())) continue;
            var leased = mobMoves.get(mob.getUUID());
            if (leased != null) { observeMobPlanMovement(mob, leased.operationId); continue; }
            if (engine.hasPendingOperations(encounterId)) continue;
            if (!abilityWork.take(planClock(), mob.getUUID(), encounterId, AbilityWorkBudget.Work.AI)) continue;
            ActionIntent decision;
            try {
                for (UUID ignored : state.members().keySet())
                    abilityWork.require(planClock(), mob.getUUID(), encounterId, AbilityWorkBudget.Work.CANDIDATE);
                decision = mobDecisions.next(new LiveActorContext(mob), state, engine);
            }
            catch (RuntimeException failure) {
                DebugDiagnostics.log("MOB_DECISION_FAILURE", () -> "actor=" + mob.getUUID()
                    + " encounter=" + encounterId + " failure=" + ActionFailure.classify(failure) + " reason=" + failure.getMessage());
                if (failure instanceof ActionFailure deferred && deferred.code() == ActionFailure.Code.EVALUATION_DEFERRED) continue;
                decision = null;
            }
            if (decision == null) {
                endCurrentTurn(encounterId, UUID.randomUUID(), state.version());
            } else {
                UUID operation = mobDecisions.operation(state);
                try {
                    tacticalActions().submit(new LiveActorContext(mob), generation, encounterId, operation, state.version(), decision, false);
                    mobDecisions.submitted(state, operation);
                } catch (ActionFailure deferred) {
                    if (deferred.code() != ActionFailure.Code.EVALUATION_DEFERRED) throw deferred;
                    // No root was accepted. Preserve this turn's decision allowance for a later tick.
                }
            }
        }
    }
    boolean maySubmitPlan(LiveActorContext actor) {
        return OperationAdmissionPolicy.evaluate(captureAdmission(actor.body(), !(actor.body() instanceof ServerPlayer))).allowed();
    }

    /** Owner-side read-only capture. Running-player handling is retained at the PLAN entry. */
    OperationAdmissionPolicy.Facts captureAdmission(LivingEntity actor, boolean rejectRunning) {
        requireThread();
        UUID encounter = engine.encounterOf(actor.getUUID());
        var state = encounter == null ? null : engine.stateView(encounter);
        var center = actor.getBoundingBox().getCenter();
        boolean player = actor instanceof ServerPlayer;
        return new OperationAdmissionPolicy.Facts(generation, encounter, state == null ? -1 : state.version(),
            closing, persistence.recoveryFailed(), encounter != null && recoveryPending.contains(encounter), worldOutcomeDepth != 0,
            server.tickRateManager().isFrozen(), rejectRunning && tacticalActions != null && tacticalActions.running(actor.getUUID()),
            tacticalActions != null && tacticalActions.controlFault(actor.getUUID()),
            state != null && actor.getUUID().equals(state.current()),
            state != null && (player ? state.phase() == EncounterPhase.ACTIVE || state.phase() == EncounterPhase.CANDIDATE
                : state.phase() != EncounterPhase.ENVIRONMENT),
            state != null && (player ? isEntityInsidePausedRegion(actor) : state.region().containsPoint(center.x, center.y, center.z)),
            player || actor instanceof Mob);
    }

    private MobObservation settleMobDisplacement(Mob mob, MobMoveLease lease,
                                                  EncounterAuthority.StateView state, boolean navigationOwned) {
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
        if (entity instanceof Mob mob && lease.ownerInstance.equals(((PresentationIdentity)mob).dndturn$presentationInstance())
                && lease.driver != null) lease.driver.release(mob);
        lease.driver = null;
    }

    private void closeMobMove(MobMoveLease lease, String reason) {
        if (mobMoves.get(lease.mobId) != lease) return;
        Entity entity = resolve(lease.mobId);
        if (lease.ticked && entity instanceof Mob mob && lease.ownerInstance.equals(((PresentationIdentity)mob).dndturn$presentationInstance())
            && engine.encounterIds().contains(lease.encounterId)) {
            lease.ticked = false;
            settleMobDisplacement(mob, lease, engine.stateView(lease.encounterId),
                lease.pathStarted && lease.driver != null && lease.driver.owns(mob));
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
            EncounterAuthority.StateView state = engine.stateView(encounterId);
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
                throw ActionFailure.staleEncounter();
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
        EncounterAuthority.StateView state = engine.stateView(lease.encounterId);
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
    public void respondToConsent(ServerPlayer player, EncounterProtocol.ConsentReply reply) {
        requireThread(); consent.reply(player, reply);
    }
    public void resyncConsent(ServerPlayer player, UUID request) { requireThread(); consent.resend(player, request); }
    public ConsentWindow.View consentView(UUID request) { requireThread(); return consent.view(request); }

    public record StartResult(StartDisposition status, EncounterAuthority.StateView state, String reason) {
        public StartResult {
            Objects.requireNonNull(status); Objects.requireNonNull(reason);
            if (status == StartDisposition.NONE || (status == StartDisposition.STARTED) != (state != null))
                throw new IllegalArgumentException("invalid start result");
        }
    }

    public boolean mayOrganizeInventory(ServerPlayer player) {
        return OperationAdmissionPolicy.evaluate(captureAdmission(player, false)).allowed();
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
        return MinecraftRegionSampler.capture(player.level(), player, discovery, config.regionRadius(), 1,
            config.maxSampledChunks(), config.maxAnchors());
    }

    void commitConsentedEncounter(ServerPlayer anchor, EncounterRegion region, Set<UUID> players, Map<UUID, UUID> requestOwners) {
        EncounterAuthority.StateView state = beginEncounter(anchor, region, players);
        requestOwners.forEach((id, owner) -> startReceipts.put(id, new StartRequestReceipt(owner, state.id())));
        persistence.changed();
        try {
            scheduledTicks.captureAll(anchor.level());
            // Install lifecycle-owned group effects before publishing the first usable turn.
            for (UUID member : state.members().keySet())
                if (resolve(member) instanceof LivingEntity living) participantEffects.capture(living);
        } catch (RuntimeException failure) {
            // Rules and receipts are already committed. Keep the encounter noninteractive
            // until the next pre-world audit retries platform preparation; never cancel START.
            recoveryPending.add(state.id());
            LOGGER.error("Encounter {} committed but queue capture needs recovery", state.id(), failure);
        }
        sync(state);
    }

    private EncounterAuthority.StateView beginEncounter(ServerPlayer initiator,
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
        AABB search = new AABB(discovery.minX(), discovery.minY(), discovery.minZ(),
            discovery.maxX(), discovery.maxY(), discovery.maxZ());
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
        for (Mob mob : level.getEntitiesOfClass(Mob.class, search,
                mob -> mob.isAlive() && discovery.contains(pointOf(mob)))) {
            if (engine.encounterOf(mob.getUUID()) == null) members.add(mob.getUUID());
        }
        UUID encounterId = UUID.randomUUID();
        long nextSequence = projections.nextSequence();
        var captured = CombatPersistenceEnvelope.CapturedSettings.from(config);
        Set<Long> chunks = exactRegionChunks(region);
        engine.beginCandidate(encounterId, region, members,
            config.time());
        capturedSettings.put(encounterId, captured);
        projections.bind(encounterId, nextSequence);
        regions.put(encounterId, region);
        regionChunks.put(encounterId, chunks);
        // Merge planning runs at beforeLevelTick. Queue capture follows the receipt commit.
        return engine.stateView(encounterId);
    }

    public EncounterAuthority.StateView state(UUID encounterId) {
        requireThread();
        return engine.stateView(encounterId);
    }

    public long sessionProjectionSequence(UUID encounterId) {
        requireThread();
        Long sequence = projections.sequence(encounterId);
        if (sequence == null) throw new IllegalArgumentException("unknown session projection");
        return sequence;
    }

    public EncounterAuthority.ResultPage results(UUID encounterId, int fromIndex, int limit) {
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
            throw ActionFailure.staleEncounter();
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
        if (engine.encounterIds().contains(encounterId))
            exitAuthorizations.grant(player, encounterId, operationId, expectedVersion);
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
            && exitAuthorizations.permits(entity, encounterId);
    }

    public void resyncMember(ServerPlayer player, UUID requestedEncounter) {
        requireThread();
        UUID encounterId = engine.encounterOf(player.getUUID());
        if (requestedEncounter != null && !requestedEncounter.equals(encounterId)) {
            EncounterAuthority.View closed = engine.closedView(requestedEncounter);
            if (closed != null && closed.members().containsKey(player.getUUID()))
                clear(player, requestedEncounter, closed.version());
        }
        if (encounterId != null) sync(engine.stateView(encounterId));
    }

    public void flushResultPages() {
        requireThread();
        projections.flush();
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
        EncounterAuthority.StateView state = engine.stateView(encounterId);
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
        EncounterAuthority.StateView state = engine.stateView(encounterId);
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
        EncounterAuthority.StateView state = engine.stateView(encounterId);
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
        EncounterAuthority.StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion) throw ActionFailure.staleEncounter();
        if (!actor.level().tickRateManager().runsNormally()) throw new IllegalStateException("global freeze holds turn settlement");
        if (tacticalActions != null && tacticalActions.running(owner))
            throw new IllegalStateException("cancel the active plan before ending the turn");
        if (tacticalActions != null && tacticalActions.controlFault(owner))
            throw new IllegalStateException("control release requires reconciliation before ending the turn");
        var pos = actor.blockPosition();
        OperationRecord.Snapshot snapshot = operationSnapshot(operationId, null,
            encounterId, owner, owner, null, cumulativeServerTicks,
            state.version(), new GridCell(pos.getX(), pos.getY(), pos.getZ()), null,
            OperationRecord.Kind.END_TURN);
        if (!engine.beginOperation(snapshot)) throw new IllegalStateException("end turn not authorized");
        worldOutcomeDepth++;
        try {
        if(actor instanceof LivingEntity living) participantEffects.settle(living,snapshot);
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
        actorStates.advance(owner, EffectDefinition.Clock.TURN_END, operationId);
        if (actor instanceof LivingEntity living && living.isAlive())
            new TriggeredAbilities(this, engine).drain(new LiveActorContext(living), snapshot,
                    TriggeredAbilityInvocation.Timing.OWNER_TURN_END);
        OperationRecord.Result result = engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
            "turn ended", 0, 0, true);
        return result;
        } catch(RuntimeException failure) {
            if(engine.pendingOperation(encounterId,operationId)!=null)
                engine.publish(encounterId,operationId,0,OperationRecord.Outcome.UNKNOWN,
                    "turn settlement uncertain; not replayed: "+failure.getClass().getSimpleName(),0,0,true);
            // Unknown native effects cannot be made safe by trying the boundary again.
            stop(encounterId);
            throw failure;
        } finally {
            worldOutcomeDepth--;
            if(worldOutcomeDepth==0) {
                confirmPendingDeaths();
                for(UUID departed:Set.copyOf(departuresDuringWorldOutcome)) {
                    departuresDuringWorldOutcome.remove(departed); leave(departed);
                }
            }
            if(engine.encounterIds().contains(encounterId)) sync(engine.stateView(encounterId));
        }
    }

    private static OperationRecord.Result matchingEndTurn(OperationRecord.Result previous, UUID owner,
                                                           long expectedVersion) {
        if (previous.snapshot().kind() != OperationRecord.Kind.END_TURN
            || !previous.snapshot().owner().equals(owner)
            || previous.snapshot().encounterVersion() != expectedVersion)
            throw new IllegalStateException("operation ID payload conflict");
        return previous;
    }

    public OperationRecord.Result attack(LivingEntity actor, UUID targetId,
                                                                  UUID operationId, long expectedVersion) {
        return attack(actor, targetId, operationId, expectedVersion, null);
    }
    OperationRecord.Result attackPlan(LiveActorContext context, UUID target, UUID operation, UUID parent,
                                     java.util.function.Consumer<OperationRecord.Result> complete) {
        context.verifyCurrent();
        LivingEntity actor = context.body();
        worldOutcomeDepth++;
        try {
            var result = attack(actor, target, operation, engine.stateView(engine.encounterOf(actor.getUUID())).version(), parent);
            // The confirmed synchronous attack is the final step of this melee plan.
            complete.accept(result);
            return result;
        } finally {
            worldOutcomeDepth--;
            if (worldOutcomeDepth == 0) {
                confirmPendingDeaths();
                for (UUID departed : worldOutcomeDepth == 0 ? Set.copyOf(departuresDuringWorldOutcome) : Set.<UUID>of()) {
                    departuresDuringWorldOutcome.remove(departed);
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
        EncounterAuthority.StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion) throw ActionFailure.staleEncounter();
        if (state.phase() != EncounterPhase.CANDIDATE && state.phase() != EncounterPhase.ACTIVE)
            throw new IllegalStateException("not a member attack phase");
        if (!actor.getUUID().equals(state.current()) || !state.members().containsKey(targetId))
            throw new IllegalStateException("attacker or target not authorized");
        if (!(actor.level() instanceof ServerLevel level) || actor.isDeadOrDying())
            throw new IllegalStateException("attacker is not a loaded living member");
        Entity found = level.getEntity(targetId);
        if (!(found instanceof LivingEntity target) || target.isDeadOrDying())
            throw new IllegalStateException("target is not a loaded living member");
        if (MeleeAdapters.server().attacker(actor) == null)
            throw new IllegalStateException("attacker melee adapter unavailable");
        var receiver = DamageReceivers.server().find(target);
        if (receiver == null) throw new IllegalStateException("target combat adapter unavailable");
        // Keep the approved player-versus-Mob relation scope separate from type support.
        if ((actor instanceof ServerPlayer) == (target instanceof ServerPlayer))
            throw new IllegalStateException("melee relationship unsupported");
        Vec3 actorCenter = actor.getBoundingBox().getCenter();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        if (!state.region().dimension().equals(level.dimension().identifier().toString())
            || !state.region().containsPoint(actorCenter.x, actorCenter.y, actorCenter.z)
            || !state.region().containsPoint(targetCenter.x, targetCenter.y, targetCenter.z)
            || !inMeleeReach(actor, target)) throw new IllegalStateException("target is outside supported melee reach");
        boolean openingAdvantage = actor instanceof ServerPlayer
            && !engine.hasAttemptedAttack(encounterId, actor.getUUID())
            && receiver.unawareOf(target, actor);
        var plan = planParent == null ? null : engine.pendingOperation(encounterId, planParent);
        double legacyDamage = plan != null ? 0 : actor instanceof ServerPlayer
            ? actor.getMainHandItem().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND)
            : MeleeAdapters.server().attacker(actor).intrinsicDamage().applyAsDouble(actor);
        var effects = plan == null ? new AbilityExecutor.MeleeEffects(Math.max(0, legacyDamage),
            settingsFor(encounterId).tacticalKnockbackEnabled(), false, actor instanceof ServerPlayer)
            : AbilityAdapterRegistry.resolve(plan.intent()).meleeEffects(new LiveActorContext(actor), settingsFor(encounterId).tacticalKnockbackEnabled());
        double weaponAttribute = effects.baseDamage();
        var defenseFacts = MinecraftSnapshotCapture.defense(target);
        double toughness = defenseFacts.read(RuleFacts.DEFENSE, RuleFacts.TOUGHNESS);
        if (!Double.isFinite(weaponAttribute) || !Double.isFinite(toughness)
            || weaponAttribute > Integer.MAX_VALUE || toughness > Integer.MAX_VALUE)
            throw new IllegalStateException("unsupported weapon or toughness attribute");
        double weaponDamage = Math.max(0, weaponAttribute);
        int reduction = RuleResolver.reduction(defenseFacts);
        AttackResolution.validateDamage(weaponDamage,
                AttackResolution.defense(defenseFacts));
        int ac = RuleResolver.armorClass(defenseFacts);
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
        // Candidate activation can deliver an actor turn boundary. Rebind facts after that rule mutation.
        defenseFacts = MinecraftSnapshotCapture.defense(target);
        reduction = RuleResolver.reduction(defenseFacts);
        ac = RuleResolver.armorClass(defenseFacts);
        AttackResolution.validateDamage(weaponDamage,
                new AttackResolution.Defense(ac, reduction));
        if (plan != null && MinecraftSnapshotCapture.capture(new LiveActorContext(actor), AbilityAdapterRegistry.definitions().require(plan.intent()))
                .abilities().stream().noneMatch(binding -> binding.id().equals(plan.intent().behaviorId())
                    && binding.source().equals(plan.intent().source()))) throw ActionFailure.source("grant changed at attack admission");
        // Admission above is the once-only operation boundary, including misses and zero damage.
        DebugDiagnostics.log("ATTACK_ACCEPTED", () -> "op=" + operationId + " parent=" + planParent
            + " encounter=" + encounterId + " actor=" + actor.getUUID() + " target=" + targetId);
        entityProjections.swing(actor, encounterId, operationId, net.minecraft.world.InteractionHand.MAIN_HAND);
        CombatRules.RollMode rollMode = CombatRules.mode(openingAdvantage,
            state.members().get(targetId).dodging());
        var damagePlan = resolveAttack(encounterId, ac, reduction, weaponDamage, rollMode, null);
        CombatRules.AttackRoll roll = damagePlan.roll();
        if (!roll.hit()) {
            DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
                weaponDamage, reduction, 0, false, 0, 0,
                damageEvidence(actor, state, DamageTrace.Stage.MISS, null, List.of()));
            OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
                OperationRecord.Outcome.COMPLETED, "miss: die=" + roll.die() + " AC=" + ac, 0, 0, true, trace);
            sync(engine.stateView(encounterId));
            return result;
        }
        int tacticalDamage = damagePlan.damage();
        if (tacticalDamage == 0 && !effects.observeZeroDamage()) {
            DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
                weaponDamage, reduction, 0, false, 0, 0,
                damageEvidence(actor, state, DamageTrace.Stage.ZERO_DAMAGE, null, List.of()));
            OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
                OperationRecord.Outcome.COMPLETED, "hit with zero tactical damage", 0, 0, true, trace);
            sync(engine.stateView(encounterId));
            return result;
        }
        permitId = UUID.randomUUID();
        engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permitId, encounterId, operationId,
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
        worldOutcomeDepth++;
        try {
            var observed = TacticalDamageContext.hurtObserved(level, target, source, tacticalDamage,
                effects.knockback(), damageId);
            boolean accepted = observed.accepted();
            if (accepted && roll.critical()) level.getChunkSource().sendToTrackingPlayersAndSelf(target,
                new net.minecraft.network.protocol.game.ClientboundAnimatePacket(target,
                    net.minecraft.network.protocol.game.ClientboundAnimatePacket.CRITICAL_HIT));
            if (accepted && effects.heldItemHooks() && actor instanceof ServerPlayer player) {
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
            actorStates.observedHit(damageId, actor, target, accepted, healthLoss + absorptionLoss);
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
            worldOutcomeDepth--;
            confirmPendingDeaths();
            for (UUID departed : worldOutcomeDepth == 0 ? Set.copyOf(departuresDuringWorldOutcome) : Set.<UUID>of()) {
                departuresDuringWorldOutcome.remove(departed);
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

    DamageTrace rangedTrace(LivingEntity player, UUID targetId, UUID operation, boolean opening) {
        var state = engine.stateView(engine.encounterOf(player.getUUID()));
        LivingEntity target = (LivingEntity) player.level().getEntity(targetId);
        var defenseFacts = MinecraftSnapshotCapture.defense(target);
        int ac = RuleResolver.armorClass(defenseFacts);
        var mode = CombatRules.mode(opening, state.members().get(targetId).dodging());
        int reduction = RuleResolver.reduction(defenseFacts);
        // The launch intent captures the roll; actual projectile base damage is captured at insertion.
        double base = 0;
        var plan = resolveAttack(state.id(), ac, reduction, base, mode, null);
        var roll = plan.roll();
        return attackTrace(operation, targetId, mode, roll, ac, base, reduction,
            plan.damage(), false, 0, 0, null);
    }

    private AttackResolution.Plan resolveAttack(UUID encounter, int ac, int reduction,
            double damage, CombatRules.RollMode mode, DamageTrace captured) {
        var random = gameTestAttackRandom.getOrDefault(encounter, attackRandom);
        int first = captured == null ? 1 + random.nextInt(20) : captured.firstDie();
        int second = captured == null ? mode == CombatRules.RollMode.NORMAL ? first : 1 + random.nextInt(20) : captured.secondDie();
        return AttackResolution.resolve(new AttackResolution.Input(
                CombatRules.RULES_REVISION, new AttackResolution.Defense(ac, reduction), damage, mode,
                new AttackResolution.Dice(first, second)));
    }

    private static DamageTrace attackTrace(UUID operationId, UUID targetId,
                                           CombatRules.RollMode mode, CombatRules.AttackRoll roll,
                                           int armorClass, double weaponDamage, int reduction,
                                           int tacticalDamage, boolean accepted,
                                           float absorptionLoss, float healthLoss,
                                           DamageTrace.DamageEvidence evidence) {
        DebugDiagnostics.log("ATTACK_ROLL_OBSERVATION", () -> "op=" + operationId + " target=" + targetId
            + " mode=" + mode + " first=" + roll.first() + " second=" + roll.second()
            + " selected=" + roll.die() + " total=" + roll.total() + " ac=" + armorClass
            + " hit=" + roll.hit() + " critical=" + roll.critical() + " base=" + weaponDamage
            + " reduction=" + reduction + " tacticalDamage=" + tacticalDamage
            + " accepted=" + accepted + " absorptionLoss=" + absorptionLoss + " healthLoss=" + healthLoss
            + " stage=" + (evidence == null ? "PREPARED" : evidence.stage()));
        return new DamageTrace(operationId, targetId, mode, roll.first(), roll.second(),
            roll.die(), roll.total(), armorClass, roll.hit(), roll.critical(), weaponDamage,
            reduction, tacticalDamage, accepted, absorptionLoss, healthLoss, evidence);
    }

    private DamageTrace.DamageEvidence damageEvidence(Entity actor, EncounterAuthority.StateView state,
                                                     DamageTrace.Stage stage,
                                                     TacticalDamageContext.HurtObservation observed,
                                                     List<DamageTrace.EquipmentChange> equipmentChanges) {
        ProjectileOrigin origin = projectileOrigins.get(actor.getUUID());
        return new DamageTrace.DamageEvidence(actor.getUUID(), CombatRules.RULES_REVISION,
            state.region().version(), observed == null ? settingsFor(state.id()).tacticalKnockbackEnabled() : observed.knockbackEnabled(), stage,
            observed == null ? 0 : observed.shieldBlockedContribution(),
            observed != null && observed.shieldWearCalled(),
            observed != null && observed.knockbackEventObserved(),
            observed != null && observed.knockbackMoved(), equipmentChanges,
            origin == null ? actor.getUUID() : origin.ownerId(),
            origin == null ? state.id() : origin.sourceEncounterId(),
            origin == null ? null : origin.rootOperationId(), observed == null ? null : observed.displacement());
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
        if(engine.encounterIds().contains(encounterId)) for(UUID owner:engine.stateView(encounterId).members().keySet()) {
            if(ServerRuntime.existingEncounter(server) == this && resolve(owner) instanceof LivingEntity living) participantEffects.release(living);
            else participantEffects.forget(owner);
        }
        Set<UUID> released = engine.end(encounterId);
        revokeEndedProjectileDomains();
        released.forEach(forcedMovements::remove);
        regions.remove(encounterId);
        capturedSettings.remove(encounterId);
        regionChunks.remove(encounterId);
        
        discardMergesContaining(encounterId);
        mergeFailures.remove(encounterId);
        gameTestAttackRandom.remove(encounterId);
        scheduledTicks.releaseEncounter(encounterId);
        EncounterAuthority.View closed = engine.closedView(encounterId);
        if (closed != null) for (UUID member : released) clear(member, encounterId, closed.version());
        syncBodyStateTransitions();
        return released;
    }

    public void leave(UUID entityId) {
        requireThread();
        exitAuthorizations.revoke(entityId);
        if (worldOutcomeDepth > 0) {
            if (engine.encounterOf(entityId) != null) departuresDuringWorldOutcome.add(entityId);
            return;
        }
        UUID encounterId = engine.encounterOf(entityId);
        if (encounterId == null) return;
        if (tacticalActions != null) tacticalActions.cancel(entityId, "member left");
        projections.leave(entityId);
        PlayerMoveLease playerLease = playerMoves.get(entityId);
        if (playerLease != null) closePlayerMove(playerLease);
        ServerPlayer departingPlayer = server.getPlayerList().getPlayer(entityId);
        MobMoveLease mobLease = mobMoves.get(entityId);
        if (mobLease != null) closeMobMove(mobLease, "member left after observed movement");
        if(ServerRuntime.existingEncounter(server) == this && resolve(entityId) instanceof LivingEntity living) participantEffects.release(living);
        else participantEffects.forget(entityId);
        engine.leave(encounterId, entityId);
        revokeEndedProjectileDomains();
        forcedMovements.remove(entityId);
        EncounterAuthority.View closed = engine.closedView(encounterId);
        if (closed != null) {
            regions.remove(encounterId);
            capturedSettings.remove(encounterId);
            regionChunks.remove(encounterId);
            
                    discardMergesContaining(encounterId);
            mergeFailures.remove(encounterId);
            gameTestAttackRandom.remove(encounterId);
            scheduledTicks.releaseEncounter(encounterId);
            if (departingPlayer != null) clear(departingPlayer, encounterId, closed.version());
            for (UUID member : closed.members().keySet()) clear(member, encounterId, closed.version());
        } else {
            EncounterAuthority.StateView state = engine.stateView(encounterId);
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
            var launch = TacticalLaunchContext.current();
            if (owner == null || !launch.owner().equals(owner.getUUID())) throw new IllegalStateException("launch owner mismatch");
            TacticalLaunchContext.observe(arrow);
            if (launch.invocation() != null) projectileAbilities.put(arrow.getUUID(),
                new CombatPersistenceEnvelope.ProjectileAbility(launch.root(), launch.invocation()));
            projectileSimulationDomains.put(arrow.getUUID(), TacticalLaunchContext.current().encounter());
            retainedProjectileHistory.add(arrow.getUUID());
        }
        persistence.changed();
    }

    public void captureSnowballOrigin(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball, boolean loaded) {
        var launch = TacticalLaunchContext.current();
        if (loaded || launch == null || ball.getOwner() == null || !launch.owner().equals(ball.getOwner().getUUID())) return;
        projectileOrigins.put(ball.getUUID(), new ProjectileOrigin(ball.getUUID(), launch.owner(), launch.operation(), launch.encounter(),
            ball.level().dimension().identifier().toString(), cumulativeServerTicks, 0, "minecraft:snowball", "minecraft:snowball", true, true, false, launch.trace()));
        if (launch.invocation() != null) projectileAbilities.put(ball.getUUID(),
            new CombatPersistenceEnvelope.ProjectileAbility(launch.root(), launch.invocation()));
        projectileSimulationDomains.put(ball.getUUID(), launch.encounter());
        retainedProjectileHistory.add(ball.getUUID());
        persistence.changed();
    }
    public void captureItemProjectile(net.minecraft.world.entity.projectile.Projectile projectile, boolean loaded) {
        var launch=TacticalLaunchContext.current();
        if (loaded || launch==null || !MinecraftProjectiles.matches(projectile,launch.invocation())
            || projectile.getOwner()==null || !launch.owner().equals(projectile.getOwner().getUUID())) return;
        String item=projectile instanceof net.minecraft.world.entity.projectile.FishingHook ? "minecraft:fishing_rod"
            : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                ((net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile)projectile).getItem().getItem()).toString();
        projectileOrigins.put(projectile.getUUID(),new ProjectileOrigin(projectile.getUUID(),launch.owner(),launch.operation(),launch.encounter(),
            projectile.level().dimension().identifier().toString(),cumulativeServerTicks,0,item,item,true,true,false,null));
        projectileAbilities.put(projectile.getUUID(),new CombatPersistenceEnvelope.ProjectileAbility(launch.root(),launch.invocation()));
        projectileSimulationDomains.put(projectile.getUUID(),launch.encounter()); retainedProjectileHistory.add(projectile.getUUID());
        persistence.changed();
    }
    boolean ownsItemProjectile(ServerPlayer player,net.minecraft.world.entity.projectile.Projectile projectile) {
        var origin=projectileOrigins.get(projectile.getUUID());
        return origin!=null && origin.launchVerified() && player.getUUID().equals(origin.ownerId())
            && projectileAbilities.containsKey(projectile.getUUID()) && !quarantinedProjectiles.containsKey(projectile.getUUID());
    }
    public net.minecraft.world.entity.projectile.ProjectileDeflection itemProjectileImpact(
        net.minecraft.world.entity.projectile.Projectile projectile,HitResult hit,
        java.util.function.Supplier<net.minecraft.world.entity.projectile.ProjectileDeflection> original) {
        if (!MinecraftProjectiles.supported(projectile) || !projectileOrigins.containsKey(projectile.getUUID())) return original.get();
        var none=net.minecraft.world.entity.projectile.ProjectileDeflection.NONE;
        if (quarantinedProjectiles.containsKey(projectile.getUUID())) return none;
        UUID domain=liveProjectileDomain(projectile.getUUID());
        if (domain==null) return original.get();
        if (!domain.equals(environment.domain(projectile.level()))) return none;
        var origin=projectileOrigins.get(projectile.getUUID());
        UUID target=hit instanceof EntityHitResult eh?eh.getEntity().getUUID():null;
        UUID operation=arrowOperationId(projectile.getUUID(),target);
        if (engine.resultFor(domain,operation)!=null) return none;
        var owner=server.getPlayerList().getPlayer(origin.ownerId());
        boolean admitted=false;
        worldOutcomeDepth++;
        try {
            if (owner==null || owner.level()!=projectile.level()) throw new IllegalStateException("item effect owner unavailable");
            double radius=projectile instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion?4.5:2;
            var contact=hit.getLocation();
            for (var pos:BlockPos.betweenClosed(BlockPos.containing(contact.x-radius,contact.y-2,contact.z-radius),
                BlockPos.containing(contact.x+radius,contact.y+3,contact.z+radius))) {
                if (!projectile.level().hasChunkAt(pos) || !domain.equals(encounterAtBlock((ServerLevel)projectile.level(),pos))
                    || !projectile.level().mayInteract(owner,pos) || server.isUnderSpawnProtection((ServerLevel)projectile.level(),pos,owner))
                    throw new IllegalStateException("item impact footprint outside permitted loaded domain");
            }
            if (hit instanceof net.minecraft.world.phys.BlockHitResult bh
                && !MinecraftProjectiles.inert(projectile.level().getBlockState(bh.getBlockPos())))
                throw new IllegalStateException("projectile block callback requires an adapter");
            if (target!=null && !(projectile.level().getEntity(target) instanceof LivingEntity)
                && !(projectile instanceof net.minecraft.world.entity.projectile.FishingHook
                    && projectile.level().getEntity(target) instanceof net.minecraft.world.entity.item.ItemEntity))
                throw new IllegalStateException("projectile deflection target unsupported");
            var affected=projectile.level().getEntitiesOfClass(LivingEntity.class,
                projectile.getBoundingBox().move(contact.subtract(projectile.position())).inflate(radius,2,radius));
            if (affected.size()>32) throw new IllegalStateException("potion target budget exceeded");
            for (var body:affected) if (projectile instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion
                && (!domain.equals(engine.encounterOf(body.getUUID())) || body instanceof ServerPlayer && body!=owner))
                throw new IllegalStateException("potion area includes an unauthorized target");
            var snapshot=new OperationRecord.Snapshot(operation,origin.rootOperationId(),domain,origin.ownerId(),projectile.getUUID(),target,
                planClock(),engine.stateView(domain).version(),MinecraftCoordinates.cell(projectile.blockPosition()),MinecraftCoordinates.cell(BlockPos.containing(contact)),
                OperationRecord.Kind.ENVIRONMENT,generation());
            if (!engine.beginCausalItemImpact(origin,snapshot)) throw new IllegalStateException("causal item effect admission denied");
            admitted=true;
            var result=original.get();
            engine.publish(domain,operation,0,OperationRecord.Outcome.COMPLETED,"native item impact completed",0,0,true);
            return result;
        } catch(RuntimeException failure) {
            if (admitted && engine.pendingOperation(domain,operation)!=null)
                engine.publish(domain,operation,0,OperationRecord.Outcome.UNKNOWN,"item impact outcome uncertain",0,0,true);
            else if (!admitted) engine.rejectCausalProjectileEffect(domain,operation,origin.ownerId(),projectile.getUUID(),target,planClock(),failure.getMessage());
            quarantineProjectile(projectile.getUUID(),domain,target,"item impact requires reconciliation: "+failure.getMessage());
            return none;
        } finally {
            worldOutcomeDepth--;
            if (worldOutcomeDepth==0) {
                confirmPendingDeaths();
                for (UUID departed:Set.copyOf(departuresDuringWorldOutcome)) {
                    departuresDuringWorldOutcome.remove(departed); leave(departed);
                }
            }
        }
    }
    public boolean handleSnowballImpact(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball, HitResult hit) {
        var origin = projectileOrigins.get(ball.getUUID());
        UUID domain = liveProjectileDomain(ball.getUUID());
        if (origin == null || domain == null) return isEntityInsidePausedRegion(ball);
        if (!domain.equals(environment.domain(ball.level()))) return true;
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
            || !(contacted instanceof LivingEntity)
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
        if (recorded != null || waiting != null) persistence.changed();
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
            if (projectileOrigins.remove(projectileId) != null) persistence.changed();
        }
    }

    /** Scheduling is revocable; immutable launch evidence is retained independently. */
    private UUID liveProjectileDomain(UUID projectileId) {
        UUID recorded = projectileSimulationDomains.get(projectileId);
        if (recorded == null) return null;
        UUID canonical = engine.canonicalEncounterId(recorded);
        if (!engine.encounterIds().contains(canonical)) {
            projectileSimulationDomains.remove(projectileId);
            persistence.changed();
            return null;
        }
        if (!canonical.equals(recorded)) {
            projectileSimulationDomains.put(projectileId, canonical);
            persistence.changed();
        }
        return canonical;
    }

    private void revokeEndedProjectileDomains() {
        for (UUID projectileId : Set.copyOf(projectileSimulationDomains.keySet()))
            liveProjectileDomain(projectileId);
    }

    /** Called by the fixed-version entity gate before an arrow's collision or despawn logic. */
    private SimulationPreparation prepareArrowSimulation(AbstractArrow arrow) {
        if (quarantinedProjectiles.containsKey(arrow.getUUID())) return SimulationPreparation.quarantined();
        if (!(arrow.level() instanceof ServerLevel level)) return SimulationPreparation.advance();
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
                persistence.changed();
            }
        }
        if (domain == null) return SimulationPreparation.advance();
        UUID canonical = engine.canonicalEncounterId(domain);
        if (!engine.encounterIds().contains(canonical)) return SimulationPreparation.advance();
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
                persistence.changed();
                rejectArrowTransit(arrow, canonical, "pending-target-unavailable",
                    "pending arrow collision no longer has a live member at the observed impact");
                return SimulationPreparation.ended();
            }
            if (targetDomain != null
                && (targetDomain.equals(sourceCanonical) || !engine.encounterIds().contains(sourceCanonical))
                && targetDomain.equals(environment.domain(level))) {
                Entity target = pendingTarget;
                if (target != null) {
                    projectileSimulationDomains.put(projectileId, targetDomain);
                    pendingProjectileAttacks.remove(projectileId);
                    persistence.changed();
                    handleArrowImpact(arrow, new EntityHitResult(target));
                    if (arrow.isRemoved()) return SimulationPreparation.ended();
                    if (quarantinedProjectiles.containsKey(projectileId)) return SimulationPreparation.quarantined();
                    return new SimulationPreparation(SimulationPreparation.Status.CONTACT_HANDLED, "PROJECTILE_CONTACT_HANDLED");
                }
            }
            return new SimulationPreparation(SimulationPreparation.Status.HELD, "PROJECTILE_CONTACT_PENDING");
        }
        // Pure scheduling decision after the owner has completed domain/contact reconciliation.
        // The old return was exactly !canonical.equals(environment.domain(level)).
        return SimulationPreparation.scheduling(SimulationPolicy.entity(new SimulationPolicy.EntityFacts(false, true,
            !canonical.equals(environment.domain(level)), false)));
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
        if (!encounterId.equals(environment.domain(level))) return false;
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
        if (!encounterId.equals(environment.domain(level))) return true;
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
            persistence.changed();
            return true;
        }
        if (shooterDomain != null && !engine.canonicalEncounterId(shooterDomain).equals(encounterId)) {
            queueMerge(engine.requestCausalMerge(shooterDomain, encounterId));
            pendingProjectileAttacks.put(arrow.getUUID(),
                new PendingProjectileAttack(target.getUUID(), shooterDomain));
            persistence.changed();
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
        EncounterAuthority.StateView state = engine.stateView(encounterId);
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
        var diagnosticOrigin = origin;
        DebugDiagnostics.log("PROJECTILE_ATTACK_ACCEPTED", () -> "op=" + root.operationId()
            + " root=" + diagnosticOrigin.rootOperationId() + " actor=" + root.owner() + " projectile=" + root.source()
            + " encounter=" + root.encounterId() + " target=" + root.target());
        try {
            if (ownerId != null && state.members().containsKey(ownerId))
                engine.setHostile(encounterId, ownerId, target.getUUID(), true);
            var defenseFacts = MinecraftSnapshotCapture.defense(target);
            int armorClass = RuleResolver.armorClass(defenseFacts);
            double toughness = defenseFacts.read(RuleFacts.DEFENSE, RuleFacts.TOUGHNESS);
            if (!Double.isFinite(toughness) || toughness > Integer.MAX_VALUE)
                throw new IllegalStateException("unsupported target toughness");
            int reduction = RuleResolver.reduction(defenseFacts);
            CombatRules.RollMode mode = CombatRules.mode(openingAdvantage,
                state.members().get(target.getUUID()).dodging());
            DamageTrace launchTrace = origin.launchTrace();
            if (launchTrace != null) { mode = launchTrace.rollMode(); armorClass = launchTrace.targetArmorClass(); reduction = launchTrace.toughnessReduction(); }
            var damagePlan = resolveAttack(encounterId, armorClass, reduction, origin.arrowBaseDamage(), mode, launchTrace);
            CombatRules.AttackRoll roll = damagePlan.roll();
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
            int tacticalDamage = damagePlan.damage();
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
            engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permitId, encounterId,
                operationId, arrow.getUUID(), Set.of(target.getUUID()),
                Set.of(EncounterPhase.ENVIRONMENT), 1, engine.stateView(encounterId).round()));
            damageId = UUID.randomUUID();
            OperationRecord.Snapshot child = operationSnapshot(damageId, operationId,
                encounterId, root.owner(), arrow.getUUID(), target.getUUID(), cumulativeServerTicks,
                engine.stateView(encounterId).version(), null, null, OperationRecord.Kind.DAMAGE);
            if (!engine.beginOperation(child, permitId))
                throw new IllegalStateException("arrow damage permit rejected");
            worldOutcomeDepth++;
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
                actorStates.observedHit(damageId, equipmentOwner, target, observation.accepted(), healthLoss + absorptionLoss);
                engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
                    "arrow hit: die=" + roll.die(), 0, healthLoss, true, trace);
                arrow.discard();
                sync(engine.stateView(encounterId));
                return true;
            } finally {
                worldOutcomeDepth--;
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
            for (UUID departed : worldOutcomeDepth == 0 ? Set.copyOf(departuresDuringWorldOutcome) : Set.<UUID>of()) {
                departuresDuringWorldOutcome.remove(departed);
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
        return !SimulationPolicy.entity(captureEntitySimulation(entity)).allowed();
    }

    /** Read-only native classification and current owner evidence. */
    SimulationPolicy.EntityFacts captureEntitySimulation(Entity entity) {
        boolean quarantine = quarantinedProjectiles.containsKey(entity.getUUID());
        if (CloudOrigins.tagged(entity))
            return new SimulationPolicy.EntityFacts(!CloudOrigins.confirmed(entity, this), true, cloudSimulationPaused(entity), false);
        if (entity instanceof net.minecraft.world.entity.AreaEffectCloud)
            return new SimulationPolicy.EntityFacts(false, true, isEnvironmentEntityPaused(entity), false);
        if (entity instanceof net.minecraft.world.entity.item.PrimedTnt)
            return new SimulationPolicy.EntityFacts(quarantine, true, isEnvironmentEntityPaused(entity), false);
        if (entity instanceof AbstractArrow || (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball || MinecraftProjectiles.supported(entity)) && projectileOrigins.containsKey(entity.getUUID())) {
            UUID recorded = projectileSimulationDomains.get(entity.getUUID());
            UUID domain = recorded == null ? null : engine.canonicalEncounterId(recorded);
            if (domain == null || !engine.encounterIds().contains(domain))
                return new SimulationPolicy.EntityFacts(quarantine, true, isEntityInsidePausedRegion(entity), false);
            return new SimulationPolicy.EntityFacts(quarantine, true,
                !domain.equals(environment.domain(entity.level())), pendingProjectileAttacks.containsKey(entity.getUUID()));
        }
        return new SimulationPolicy.EntityFacts(quarantine, false, false, false);
    }

    private record SimulationDecision(long tick, EncounterAuthority.Revision revision, UUID environment,
                                      UUID environmentStep, UUID instance, SimulationPreparation result) {}
    boolean cloudSimulationPaused(Entity cloud) {
        return !WorldOutcomePolicy.cloudSimulation(captureCloud(cloud)).allowed();
    }
    private WorldOutcomePolicy.CloudFacts captureCloud(Entity cloud) {
        boolean confirmed = CloudOrigins.confirmed(cloud, this);
        UUID domain = confirmed ? engine.canonicalEncounterId(CloudOrigins.domain(cloud)) : null;
        return new WorldOutcomePolicy.CloudFacts(confirmed, domain != null && engine.encounterIds().contains(domain),
            domain != null && domain.equals(environment.domain(cloud.level())), isEntityInsidePausedRegion(cloud),
            cloud.level().tickRateManager().runsNormally(), closing || persistence.recoveryFailed() || domain != null && recoveryPending.contains(domain));
    }
    boolean cloudTargetAllowed(Entity cloud, LivingEntity target) {
        var source = captureCloud(cloud);
        UUID domain = source.confirmed() ? engine.canonicalEncounterId(CloudOrigins.domain(cloud)) : null;
        UUID step = environmentStepFor(cloud);
        return WorldOutcomePolicy.cloudTarget(new WorldOutcomePolicy.CloudTargetFacts(
            WorldOutcomePolicy.cloudSimulation(source).allowed(), source.liveDomain(),
            domain != null && domain.equals(engine.encounterOf(target.getUUID())),
            step != null && step.equals(environmentStepFor(target)), isEntityInsidePausedRegion(target))).allowed();
    }
    private final Map<UUID, SimulationDecision> simulationDecisions = new HashMap<>();

    /** Called only from the vanilla entity/ride execution gate; repeated visits cannot replay effects. */
    public SimulationPreparation prepareEntitySimulation(Entity entity) {
        requireThread();
        if (entity.isRemoved()) return SimulationPreparation.ended();
        if (quarantinedProjectiles.containsKey(entity.getUUID())) return SimulationPreparation.quarantined();
        SimulationDecision prior = simulationDecisions.get(entity.getUUID());
        UUID environment = this.environment.domain(entity.level());
        UUID step = this.environment.step(entity.level());
        UUID instance = entity instanceof PresentationIdentity identity ? identity.dndturn$presentationInstance() : null;
        // A contact consumed this entity's step. A different operation's rule revision cannot replay its body tick.
        if (prior != null && prior.tick() == cumulativeServerTicks && Objects.equals(prior.instance(), instance)
            && prior.result().status() == SimulationPreparation.Status.CONTACT_HANDLED) return prior.result();
        if (prior != null && prior.tick() == cumulativeServerTicks
            && prior.revision().equals(engine.revision()) && Objects.equals(prior.environment(), environment)
            && Objects.equals(prior.environmentStep(), step) && Objects.equals(prior.instance(), instance))
            return prior.result();
        SimulationPreparation result = prepareEntitySimulationStep(entity);
        simulationDecisions.put(entity.getUUID(), new SimulationDecision(cumulativeServerTicks,
            engine.revision(), environment, step, instance, result));
        return result;
    }

    private SimulationPreparation prepareEntitySimulationStep(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.AreaEffectCloud)
            return SimulationPreparation.scheduling(SimulationPolicy.entity(captureEntitySimulation(entity)));
        if (quarantinedProjectiles.containsKey(entity.getUUID()))
            return SimulationPreparation.quarantined();
        if (entity instanceof net.minecraft.world.entity.item.PrimedTnt)
            return SimulationPreparation.scheduling(SimulationPolicy.entity(captureEntitySimulation(entity)));
        if (entity instanceof AbstractArrow arrow) return prepareArrowSimulation(arrow);
        if (MinecraftProjectiles.supported(entity) && projectileOrigins.containsKey(entity.getUUID())) {
            UUID domain=liveProjectileDomain(entity.getUUID());
            if (domain==null) return SimulationPreparation.advance();
            // No mutation in policy; domain reconciliation above remains owned here.
            if (!SimulationPolicy.entity(new SimulationPolicy.EntityFacts(false, true,
                !domain.equals(environment.domain(entity.level())), false)).allowed())
                return new SimulationPreparation(SimulationPreparation.Status.HELD, "ENVIRONMENT_STEP_REQUIRED");
            var box=entity.getBoundingBox().expandTowards(entity.getDeltaMovement()).inflate(.1);
            if (box.getSize()>8) { quarantineProjectile(entity.getUUID(),domain,null,"item projectile sweep exceeds budget"); return SimulationPreparation.quarantined(); }
            for (var pos:BlockPos.betweenClosed(box)) {
                if (!entity.level().hasChunkAt(pos) || !domain.equals(encounterAtBlock((ServerLevel)entity.level(),pos))
                    || !MinecraftProjectiles.inert(entity.level().getBlockState(pos))
                        && !(entity instanceof net.minecraft.world.entity.projectile.FishingHook
                            && entity.level().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                            && entity.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER))) {
                    quarantineProjectile(entity.getUUID(),domain,null,"item projectile encountered unsupported traversal"); return SimulationPreparation.quarantined();
                }
            }
            if (entity instanceof net.minecraft.world.entity.projectile.FishingHook) {
                for (var pos:BlockPos.betweenClosed(entity.blockPosition().offset(-3,-1,-3),entity.blockPosition().offset(3,3,3)))
                    if (!entity.level().hasChunkAt(pos) || !domain.equals(encounterAtBlock((ServerLevel)entity.level(),pos))) {
                        quarantineProjectile(entity.getUUID(),domain,null,"fishing observation outside loaded domain"); return SimulationPreparation.quarantined();
                    }
            }
            return SimulationPreparation.advance();
        }
        if (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball && projectileOrigins.containsKey(entity.getUUID())) {
            UUID domain = liveProjectileDomain(entity.getUUID());
            if (domain == null) return SimulationPreparation.advance();
            return SimulationPreparation.scheduling(SimulationPolicy.entity(new SimulationPolicy.EntityFacts(false, true,
                !domain.equals(environment.domain(entity.level())), false)));
        }
        reconcileMobMovementStep(entity);
        return SimulationPreparation.scheduling(SimulationPolicy.entity(captureEntitySimulation(entity)));
    }

    /** Lease-owner mutation, called only from prepareEntitySimulationStep, never from a query. */
    private void reconcileMobMovementStep(Entity entity) {
        MobMoveLease lease = mobMoves.get(entity.getUUID());
        if (lease != null && !activeMobLease(lease)) {
            revokeMobLease(lease);
            lease = null;
        }
        if (!isEntityInsidePausedRegion(entity)) {
            if (lease != null) closeMobMove(lease, "member moved outside fixed region");
            return;
        }
        if (lease == null) return;
        if (lease.pathStarted && entity instanceof Mob mob) {
            int remaining = engine.stateView(lease.encounterId).members().get(entity.getUUID()).movementTicks();
            // Inspect the next vanilla navigation/control proposal and its loaded collision
            // corridor. Flat dry steps cost one; water and possible jumps need their own budget.
            int possibleCost = nextMobStepCost(mob, lease);
            if (possibleCost < 0 || remaining < possibleCost) {
                lease.budgetBlocked = true;
                closeMobMove(lease, "navigation budget or corridor invalidated");
                return;
            }
            lease.authorizedCost = possibleCost;
            if (lease.underreserveNextExpensiveStepForGameTest && possibleCost >= 2) {
                lease.authorizedCost = 1;
            }
        }
    }

    /** A provider's conservative budget estimate never replaces observed rule charges. */
    private static int nextMobStepCost(Mob mob, MobMoveLease lease) {
        if (lease.driver == null || !lease.driver.owns(mob)) return -1;
        int budget = lease.driver.nextStepBudget(mob);
        return budget >= 1 && budget <= 3 ? budget : -1;
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

    /** Current spatial ownership, not membership or the original igniter's turn. */
    public UUID environmentStepFor(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !level.tickRateManager().runsNormally()) return null;
        UUID active = environment.domain(level);
        if (active == null || !engine.encounterIds().contains(active)) return null;
        Vec3 center = entity.getBoundingBox().getCenter();
        EncounterRegion region = regions.get(active);
        return region != null && region.dimension().equals(level.dimension().identifier().toString())
            && region.containsPoint(center.x, center.y, center.z) && !isEnvironmentEntityPaused(entity)
            ? environment.step(level) : null;
    }

    private boolean isEnvironmentEntityPaused(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        Vec3 center = entity.getBoundingBox().getCenter();
        String dimension = level.dimension().identifier().toString();
        for (var entry : regions.entrySet()) {
            if (entry.getValue().dimension().equals(dimension)
                && entry.getValue().containsPoint(center.x, center.y, center.z)
                && !entry.getKey().equals(environment.domain(level))) return true;
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
                if (!entry.getKey().equals(environment.domain(level))) return true;
        }
        return false;
    }

    /** The local client receives changes only when it enters or leaves a formal paused region. */
    public void syncBodyStateTransitions() {
        requireThread();
        exitAuthorizations.reconcile(engine, this::resolve);
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            sendBodyState(player, MinecraftCombatRuntime.isBodyPaused(player),
                ActorControlPolicy.leasedMovement(AuthorityProjection.actor(player)).allowed());
        }
        projections.retainOnline(online);
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
            if (identity != null) use = new PresentationState.Use(identity, living.getUsedItemHand(), ItemStackFingerprint.revision(living, living.getUseItem()),
                Math.max(0, living.getTicksUsingItem()), Math.max(0, living.getUseItemRemainingTicks()));
        }
        boolean paused = entity instanceof ServerPlayer player ? MinecraftCombatRuntime.isBodyPaused(player) : clientEntityPaused(entity);
        return new PresentationState.Facts(member, controller,
            controller == null ? "RELEASED" : engine.stateView(controller).phase().name(), paused, use,
            controller == null ? 0 : engine.environmentTime(controller));
    }

    private boolean clientEntityPaused(Entity entity) {
        if (quarantinedProjectiles.containsKey(entity.getUUID())) return true;
        if (entity instanceof AbstractArrow || (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball || MinecraftProjectiles.supported(entity)) && projectileOrigins.containsKey(entity.getUUID())) {
            UUID recorded = projectileSimulationDomains.get(entity.getUUID());
            UUID domain = recorded == null ? null : engine.canonicalEncounterId(recorded);
            if (domain != null && engine.encounterIds().contains(domain)) {
                var state = engine.stateView(domain);
                return !SimulationPolicy.presentationDuringEnvironment(false, pendingProjectileAttacks.containsKey(entity.getUUID()),
                    recoveryPending.contains(domain), state.phase() == EncounterPhase.ENVIRONMENT, server.tickRateManager().isFrozen()).allowed();
            }
        }
        return isEntitySimulationPaused(entity);
    }

    public void sendBodyState(ServerPlayer player, boolean paused, boolean movementAllowed) {
        requireThread();
        if (closing) return;
        projections.body(player, paused, movementAllowed);
    }

    private void sync(EncounterAuthority.StateView state) {
        projections.publish(state, actor -> {
            var movement = playerMoves.get(actor);
            return movement == null ? null : movement.operationId;
        }, !recoveryPending.contains(state.id()));
    }

    private void clear(UUID member, UUID encounterId, long version) {
        ServerPlayer player = server.getPlayerList().getPlayer(member);
        if (player != null) clear(player, encounterId, version);
    }

    private void clear(ServerPlayer player, UUID encounterId, long version) {
        projections.clear(player, encounterId, version);
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
        if (closing || persistence.recoveryFailed() || recoveryPending.contains(encounterId))
            throw new IllegalStateException("encounter platform state is not ready");
        if (tacticalActions != null && tacticalActions.controlFault(owner))
            throw new IllegalStateException("control release requires reconciliation");
        if (tick != cumulativeServerTicks) throw new IllegalArgumentException("operation clock must be server observation time");
        return new OperationRecord.Snapshot(operationId, parentId, encounterId, owner, source, target,
            tick, version, sourceCell, targetCell, kind, generation);
    }

    private void quarantineProjectile(UUID projectile, UUID encounter, UUID target, String reason) {
        quarantinedProjectiles.putIfAbsent(projectile, new CombatPersistenceEnvelope.QuarantinedProjectile(
            arrowOperationId(projectile, target), encounter, target, reason));
        persistence.changed();
    }

    public boolean isProjectileQuarantined(UUID projectile) {
        requireThread();
        return quarantinedProjectiles.containsKey(projectile);
    }
}
