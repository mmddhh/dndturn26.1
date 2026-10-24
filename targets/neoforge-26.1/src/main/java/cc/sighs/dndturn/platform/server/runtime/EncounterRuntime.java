package cc.sighs.dndturn.platform.server.runtime;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.platform.observation.ItemStackFingerprint;
import cc.sighs.dndturn.platform.projection.PresentationUseIdentity;
import cc.sighs.dndturn.platform.server.encounter.ProjectionPublisher;
import cc.sighs.dndturn.platform.server.encounter.ServerEntityProjections;
import cc.sighs.dndturn.platform.server.encounter.ServerCombatConfig;
import cc.sighs.dndturn.platform.server.encounter.EncounterLifecycleCoordinator;
import cc.sighs.dndturn.platform.server.runtime.AuthorityProjection;

import cc.sighs.dndturn.platform.server.action.CombatActionExecution;
import cc.sighs.dndturn.platform.server.world.WorldSimulationCoordinator;
import cc.sighs.dndturn.platform.server.projectile.ProjectileExecutionCoordinator;
import cc.sighs.dndturn.platform.server.damage.DamageResolution;
import cc.sighs.dndturn.platform.server.movement.MovementExecutionCoordinator;
import cc.sighs.dndturn.platform.server.action.ExecutionProcesses;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.control.GateDecision;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.EncounterRegion;
import cc.sighs.dndturn.domain.encounter.EncounterStateSnapshot;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.operation.DamageTrace;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.operation.ProjectileOrigin;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.projection.PresentationState;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.checkpoint.AbilityCheckpoint;
import cc.sighs.dndturn.platform.server.budget.AbilityWorkBudget;
import cc.sighs.dndturn.platform.server.action.ActionExecutionCoordinator;
import cc.sighs.dndturn.platform.server.action.ActionAdmission;
import cc.sighs.dndturn.platform.server.action.ActionCombat;
import cc.sighs.dndturn.platform.server.action.ActionEffects;
import cc.sighs.dndturn.platform.server.action.ActionEvidence;
import cc.sighs.dndturn.platform.server.action.TacticalImpact;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import cc.sighs.dndturn.platform.server.ai.MobTurnStrategies;
import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.control.ControlFacts;
import cc.sighs.dndturn.platform.server.control.OperationAdmissionPolicy;
import cc.sighs.dndturn.platform.server.control.SimulationPolicy;
import cc.sighs.dndturn.platform.server.action.TriggeredAbilities;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectHost;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController;
import cc.sighs.dndturn.platform.server.inspection.InspectionService;
import cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope;
import cc.sighs.dndturn.platform.server.persistence.CombatSavedData;
import cc.sighs.dndturn.platform.server.persistence.EncounterPersistence;
import cc.sighs.dndturn.platform.server.runtime.MinecraftCombatRuntime;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import cc.sighs.dndturn.platform.server.world.CloudOrigins;
import cc.sighs.dndturn.platform.server.control.SimulationPreparation;
import cc.sighs.dndturn.platform.server.world.WorldOutcomeHost;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Coordinates encounter lifecycle and live platform owners for one server instance. */
public final class EncounterRuntime {
    private final TriggeredAbilities.Host triggerHost = new TriggeredAbilities.Host() {
        public ActorStateAuthority actorStates() { return actorStates; }
        public ExecutionProcesses processes() { return processes; }
        public WorldOutcomeHost worldOutcomes() { return worldOutcomes; }
        public UUID generation() { return generation; }
        public long clock() { return cumulativeServerTicks; }
    };
    private final ActionAdmission actionAdmission = new ActionAdmission() {
        public void authorizeImpact(ServerPlayer actor, BlockPos pos) { TacticalImpact.authorize(actor, pos, engine, world::encounterAtBlock); }
        public boolean maySubmitPlan(LiveActorContext actor) { return EncounterRuntime.this.maySubmitPlan(actor); }
        public boolean mayOrganizeInventory(ServerPlayer actor) { return EncounterRuntime.this.mayOrganizeInventory(actor); }
        public void requireAbilityWork(UUID actor, UUID encounter, AbilityWorkBudget.Work work) { EncounterRuntime.this.requireAbilityWork(actor, encounter, work); }
        public void resyncMember(ServerPlayer actor, UUID encounter) { EncounterRuntime.this.resyncMember(actor, encounter); }
    };
    private final ActionCombat actionCombat = new ActionCombat() {
        public OperationRecord.Result attackPlan(LiveActorContext context, UUID target, UUID operation, UUID parent,
        java.util.function.Consumer<OperationRecord.Result> complete) { return EncounterRuntime.this.attackPlan(context, target, operation, parent, complete); }
        public DamageTrace rangedTrace(LivingEntity actor, UUID target, UUID operation, boolean opening) { return EncounterRuntime.this.rangedTrace(actor, target, operation, opening); }
    };
    private final ActionEffects actionEffects = new ActionEffects() {
        public void drainImmediateTriggers(LiveActorContext actor, UUID encounter, UUID parent) { EncounterRuntime.this.drainImmediateTriggers(actor, encounter, parent); }
        public void joinGeneratedMob(ServerPlayer owner, Mob mob, UUID encounter) { EncounterRuntime.this.joinGeneratedMob(owner, mob, encounter); }
        public void swing(LivingEntity actor, UUID encounter, UUID operation, net.minecraft.world.InteractionHand hand) { entityProjections.swing(actor, encounter, operation, hand); }
    };
    private final ActionEvidence actionEvidence = new ActionEvidence() {
        public long planClock() { return EncounterRuntime.this.planClock(); }
        public long executionDeadlineTicks() { return config.executionDeadlineTicks(); }
        public void recordAbilityCheckpoint(AbilityCheckpoint evidence) { EncounterRuntime.this.recordAbilityCheckpoint(evidence); }
        public void recordReleaseFailure(UUID operation, String reason) {
            persistence.appendAudit(new CombatPersistenceEnvelope.RecoveryAudit(operation, generation, cumulativeServerTicks, ActionFailure.Code.CONTROL_FAULT, reason));
        }
        public void recordReleaseReconciliation(UUID operation, boolean retired) { EncounterRuntime.this.recordReleaseReconciliation(operation, retired); }
    };

    private final WorldOutcomeHost worldOutcomes = new WorldOutcomeHost() {
        public UUID canonicalOutcomeEncounter(UUID encounter) { return EncounterRuntime.this.canonicalOutcomeEncounter(encounter); }
        public void completeWorldOutcome(Runnable completion) { EncounterRuntime.this.completeWorldOutcome(completion); }
    };
    public WorldOutcomeHost worldOutcomes() { requireThread(); return worldOutcomes; }

    private final VanillaEffectHost vanillaEffectHost = new VanillaEffectHost() {
        public cc.sighs.dndturn.domain.actor.ActorStates.State state(UUID actor) { return actorStates.state(actor); }
        public cc.sighs.dndturn.domain.actor.ActorStates.State command(LiveActorContext actor, cc.sighs.dndturn.domain.actor.ActorStates.Command command) { return actorStates.command(actor, command); }
        public List<cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord> invocations(UUID actor) { return actorStates.invocations(actor); }
        public void invocationStatus(UUID operation, cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord.Status status, String reason) { actorStates.invocationStatus(operation, status, reason); }
        public UUID encounterOf(UUID actor) { return engine.encounterOf(actor); }
        public UUID generation() { return generation; }
        public long clock() { return cumulativeServerTicks; }
        public void changed() { persistence.changed(); }
    };
    public VanillaEffectRoundController vanillaEffects() { return vanillaEffects; }
    public boolean holdVanillaEffect(LivingEntity actor, net.minecraft.world.effect.MobEffectInstance effect) {
        requireThread(); return isMember(actor.getUUID()) && vanillaEffects.holdEffect(actor, effect);
    }
    public boolean holdVanillaFire(Entity actor) {
        requireThread(); return isMember(actor.getUUID()) && vanillaEffects.holdFire(actor);
    }

    private final ControlFacts controlFacts = new ControlFacts() {
        public GateDecision.Evidence controlEvidence(Entity entity) { return EncounterRuntime.this.controlEvidence(entity); }
        public OperationAdmissionPolicy.Facts captureAdmission(LivingEntity actor, boolean rejectRunning) { return EncounterRuntime.this.captureAdmission(actor, rejectRunning); }
        public SimulationPolicy.EntityFacts captureEntitySimulation(Entity entity) { return EncounterRuntime.this.captureEntitySimulation(entity); }
        public boolean cloudSimulationPaused(Entity cloud) { return EncounterRuntime.this.cloudSimulationPaused(cloud); }
        public boolean cloudTargetAllowed(Entity cloud, LivingEntity target) { return EncounterRuntime.this.cloudTargetAllowed(cloud, target); }
    };
    public ControlFacts controlFacts() { requireThread(); return controlFacts; }

    private static final Logger LOGGER = LogUtils.getLogger();

    private final DamageResolution damageResolution = new DamageResolution();

    private final EncounterLifecycleCoordinator lifecycle;
    public EncounterLifecycleCoordinator lifecycle() { return lifecycle; }
    private boolean prepareWorldBoundary(ServerLevel level) { return lifecycle.prepareWorldBoundary(level); }
    private void flushWorldOutcome() { lifecycle.flushWorldOutcome(); }
    private void completeWorldOutcome(Runnable completion) { lifecycle.completeWorldOutcome(completion); }
    private CombatPersistenceEnvelope.CapturedSettings settingsFor(UUID encounterId) { return lifecycle.settingsFor(encounterId); }
    private void queueMerge(EncounterAuthority.MergePlan plan) { lifecycle.queueMerge(plan); }
    private void joinGeneratedMob(ServerPlayer owner, Mob mob, UUID encounter) { lifecycle.joinGeneratedMob(owner, mob, encounter); }
    private boolean exitedRegion(Entity entity, UUID encounterId) { return lifecycle.exitedRegion(entity, encounterId); }
    private Set<UUID> stop(UUID encounterId) { return lifecycle.stop(encounterId); }
    private final CombatActionExecution combat;
    public CombatActionExecution combat() { return combat; }
    private void drainImmediateTriggers(LiveActorContext actor, UUID encounter, UUID parent) { combat.drainImmediateTriggers(actor, encounter, parent); }
    private OperationRecord.Result endCurrentTurn(UUID encounterId,
                                                                UUID operationId, long expectedVersion) { return combat.endCurrentTurn(encounterId, operationId, expectedVersion); }
    private OperationRecord.Result attackPlan(LiveActorContext context, UUID target, UUID operation, UUID parent,
                                     java.util.function.Consumer<OperationRecord.Result> complete) { return combat.attackPlan(context, target, operation, parent, complete); }
    private DamageTrace rangedTrace(LivingEntity player, UUID targetId, UUID operation, boolean opening) { return combat.rangedTrace(player, targetId, operation, opening); }
    private final WorldSimulationCoordinator world;
    public WorldSimulationCoordinator world() { return world; }
    private final CloudOrigins.Evidence cloudEvidence = new CloudOrigins.Evidence() {
        public cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord invocation(UUID operation) { return actorStates.invocation(operation); }
        public UUID canonical(UUID encounter) { return engine.canonicalEncounterId(encounter); }
    };
    public CloudOrigins.Evidence cloudEvidence() { return cloudEvidence; }
    private UUID encounterAtBlock(ServerLevel level, BlockPos pos) { return world.encounterAtBlock(level, pos); }
    private SimulationPolicy.EntityFacts captureEntitySimulation(Entity entity) { return world.captureEntitySimulation(entity); }
    private boolean cloudSimulationPaused(Entity cloud) { return world.cloudSimulationPaused(cloud); }
    private boolean cloudTargetAllowed(Entity cloud, LivingEntity target) { return world.cloudTargetAllowed(cloud, target); }
    private boolean isEntityInsidePausedRegion(Entity entity) { return world.isEntityInsidePausedRegion(entity); }
    private final ProjectileExecutionCoordinator projectiles;
    public ProjectileExecutionCoordinator projectiles() { return projectiles; }
    private UUID liveProjectileDomain(UUID projectileId) { return projectiles.liveProjectileDomain(projectileId); }
    private SimulationPreparation prepareArrowSimulation(AbstractArrow arrow) { return projectiles.prepareArrowSimulation(arrow); }
    private void quarantineProjectile(UUID projectile, UUID encounter, UUID target, String reason) { projectiles.quarantineProjectile(projectile, encounter, target, reason); }
    private boolean isProjectileQuarantined(UUID projectile) { return projectiles.isProjectileQuarantined(projectile); }
    private final MovementExecutionCoordinator movement;
    public void finishMovementTicks() { movement.finishMovementTicks(); if (actions != null) actions.tick(); }
    public MovementExecutionCoordinator movement() { requireThread(); return movement; }
    private boolean closing;

    private final ServerCombatConfig config;
    private final MinecraftServer server;
    private final UUID generation;
    private final EncounterAuthority engine;
    private final ServerEntityProjections entityProjections;
    private final ProjectionPublisher projections;
    private final EncounterPersistence persistence;
    private final ActorStateAuthority actorStates;
    private final InspectionService inspections;
    public InspectionService inspections() { requireThread(); return inspections; }
    public ActorStateAuthority actorStates() { requireThread(); return actorStates; }
    private long cumulativeServerTicks;
    public AbilityCheckpoint abilityCheckpoint(UUID operation) { requireThread(); return persistence.checkpoint(operation); }
    void recordReleaseReconciliation(UUID operation, boolean retired) {
        requireThread();
        persistence.appendAudit(new CombatPersistenceEnvelope.RecoveryAudit(operation, generation, cumulativeServerTicks,
            ActionFailure.Code.NONE, retired ? "old instance retired; server control revoked" : "owned control release reconciled"));
    }
    void recordAbilityCheckpoint(AbilityCheckpoint evidence) {
        requireThread();
        persistence.recordCheckpoint(evidence);
    }
    private ActionExecutionCoordinator actions;
    private final VanillaEffectRoundController vanillaEffects;
    private final ExecutionProcesses processes;
    public ExecutionProcesses processes() { requireThread(); return processes; }
    public void releaseVanillaEffects(Entity entity) {
        if (ServerRuntime.existingEncounter(server) == this && !closing && lifecycle.outcomeDepth() == 0 && entity instanceof LivingEntity living && engine.encounterOf(entity.getUUID()) != null)
            vanillaEffects.release(living);
    }
    public ActionExecutionCoordinator actions() {
        requireThread();
        if (actions == null) actions = new ActionExecutionCoordinator(server, engine, generation, actorStates, processes, movement,
            worldOutcomes, actionAdmission, actionCombat, actionEffects, actionEvidence);
        return actions;
    }
    long planClock() { return cumulativeServerTicks; }
    UUID canonicalOutcomeEncounter(UUID encounter) { requireThread(); return encounter == null ? null : engine.canonicalEncounterId(encounter); }

    public EncounterRuntime(MinecraftServer server, ActorStateAuthority actorStates, UUID generation) {
        this(server, server.getDataStorage().computeIfAbsent(CombatSavedData.TYPE), actorStates, generation);
    }

    private EncounterRuntime(MinecraftServer server, CombatSavedData savedData, ActorStateAuthority actorStates, UUID generation) {
        this.server = server;
        this.generation = java.util.Objects.requireNonNull(generation);
        this.config = ServerCombatConfig.load(server);
        this.actorStates = java.util.Objects.requireNonNull(actorStates);
        var opened = EncounterPersistence.open(savedData, config.roundTicks());
        this.persistence = opened.persistence();
        this.engine = opened.authority();
        this.inspections = new InspectionService(engine, actorStates, generation, this::planClock);
        this.actorStates.bindEncounterMembership(engine::encounterOf);
        this.movement = new MovementExecutionCoordinator(server, engine, generation, new MovementExecutionCoordinator.Host() {
            public boolean supportsMovement(Mob actor) { return cc.sighs.dndturn.platform.server.ai.MovementPorts.find(actor) != null; }
            public cc.sighs.dndturn.platform.server.ai.MovementPorts.Driver startMovement(Mob actor, GridCell goal) {
                return cc.sighs.dndturn.platform.server.ai.MovementPorts.require(actor).port().start(actor, goal);
            }
            public long clock() { return cumulativeServerTicks; }
            public boolean allowsMove(ServerPlayer actor, Vec3 position) { return actions == null || actions.allowsMove(actor, position); }
            public void sync(StateView state) { EncounterRuntime.this.sync(state); }
            public void syncBodyStateTransitions() { EncounterRuntime.this.syncBodyStateTransitions(); }
            public void reanchor(UUID encounter) { lifecycle.reanchor(encounter); }
            public boolean isEntityInsidePausedRegion(Entity entity) { return EncounterRuntime.this.isEntityInsidePausedRegion(entity); }
            public void movement(Entity actor, PresentationState.Movement value) { entityProjections.movement(actor, value); }
            public OperationRecord.Snapshot operationSnapshot(UUID operation, UUID parent, UUID encounter, UUID owner,
                    UUID source, UUID target, long tick, long version, GridCell from, GridCell to, OperationRecord.Kind kind) {
                return EncounterRuntime.this.operationSnapshot(operation, parent, encounter, owner, source, target, tick, version, from, to, kind);
            }
        });

        this.processes = new ExecutionProcesses(this::requireThread,
                persistence::changed, persistence.processes());
        CombatPersistenceEnvelope saved = opened.checkpoint();
        this.world = new WorldSimulationCoordinator(server, engine, new WorldSimulationCoordinator.Host() {
            public long clock() { return cumulativeServerTicks; }
            public boolean prepareBoundary(ServerLevel level) { return prepareWorldBoundary(level); }
            public boolean blocked(UUID encounter) { return closing || persistence.recoveryFailed() || encounter != null && lifecycle.recovering(encounter); }
            public boolean exitedRegion(Entity entity, UUID encounter) { return EncounterRuntime.this.exitedRegion(entity, encounter); }
            public void reconcileMovement(Entity entity) { movement.reconcileMobMovementStep(entity); }
            public void sync(StateView state) { EncounterRuntime.this.sync(state); }
            public void syncBodyStateTransitions() { EncounterRuntime.this.syncBodyStateTransitions(); }
            public void stop(UUID encounter) { EncounterRuntime.this.stop(encounter); }
        }, new WorldSimulationCoordinator.Projectiles() {
            public boolean isProjectileQuarantined(UUID id) { return projectiles.isProjectileQuarantined(id); }
            public ProjectileOrigin origin(UUID id) { return projectiles.origin(id); }
            public UUID recordedDomain(UUID id) { return projectiles.recordedDomain(id); }
            public boolean hasPendingContact(UUID id) { return projectiles.hasPendingContact(id); }
            public SimulationPreparation prepareArrowSimulation(AbstractArrow arrow) { return projectiles.prepareArrowSimulation(arrow); }
            public UUID liveProjectileDomain(UUID id) { return projectiles.liveProjectileDomain(id); }
            public void quarantineProjectile(UUID projectile, UUID encounter, UUID target, String reason) { projectiles.quarantineProjectile(projectile, encounter, target, reason); }
        }, cloudEvidence);
        this.projectiles = new ProjectileExecutionCoordinator(server, engine, generation, actorStates, world.scheduler(), persistence, damageResolution, new ProjectileExecutionCoordinator.Host() {
            public cc.sighs.dndturn.platform.server.damage.DamageAttempt damageAttempt() { return combat; }
            public long clock() { return cumulativeServerTicks; }
            public Map<UUID, EncounterRegion> regions() { return world.regions(); }
            public boolean knockback(UUID encounter) { return settingsFor(encounter).tacticalKnockbackEnabled(); }
            public void enterOutcome() { lifecycle.enterOutcome(); }
            public void exitOutcome() { lifecycle.exitOutcome(); }
            public void flushOutcome() { flushWorldOutcome(); }
            public OperationRecord.Snapshot operationSnapshot(UUID operationId, UUID parentId, UUID encounterId,
            UUID owner, UUID source, UUID target, long tick, long version, GridCell sourceCell,
            GridCell targetCell, OperationRecord.Kind kind) { return EncounterRuntime.this.operationSnapshot(operationId, parentId, encounterId, owner, source, target, tick, version, sourceCell, targetCell, kind); }
            public void sync(StateView state) { EncounterRuntime.this.sync(state); }
            public UUID encounterAtBlock(ServerLevel level, BlockPos pos) { return EncounterRuntime.this.encounterAtBlock(level, pos); }
            public boolean isEntityInsidePausedRegion(Entity entity) { return EncounterRuntime.this.isEntityInsidePausedRegion(entity); }
            public void queueMerge(EncounterAuthority.MergePlan plan) { EncounterRuntime.this.queueMerge(plan); }
            public Entity resolve(UUID id) { return EncounterRuntime.this.resolve(id); }
        });
        actorStates.snapshots().bindFacts(new LiveActorContext.Facts() {
            public boolean hostile(UUID encounter, UUID actor, UUID target) {
                return engine.isHostile(encounter, actor, target) || engine.isHostile(encounter, target, actor);
            }
            public cc.sighs.dndturn.domain.actor.ActorStates.State state(UUID actor) { return actorStates.state(actor); }
            public UUID encounterAtBlock(ServerLevel level, BlockPos pos) { return world.encounterAtBlock(level, pos); }
            public boolean ownsProjectile(ServerPlayer owner, net.minecraft.world.entity.projectile.Projectile projectile) { return projectiles.ownsItemProjectile(owner, projectile); }
            public String fault(UUID actor) { return actorStates.fault(actor); }
            public void requireAbilityWork(UUID actor, UUID encounter, AbilityWorkBudget.Work work) { EncounterRuntime.this.requireAbilityWork(actor, encounter, work); }
        });
        this.engine.bindAbilityDefinitions(AbilityAdapterRegistry.definitions());
        this.engine.bindActorTurnObserver(this::turnStarted);
        this.engine.bindObservationEpoch(generation);
        this.vanillaEffects = new VanillaEffectRoundController(vanillaEffectHost,engine);
        this.entityProjections = new ServerEntityProjections(server, generation, this::presentationFacts);
        this.projections = new ProjectionPublisher(server, engine, generation, this::resolve);
        this.lifecycle = new EncounterLifecycleCoordinator(server, engine, generation, config, new EncounterLifecycleCoordinator.Host() {
            public long clock() { return cumulativeServerTicks; }
            public boolean active() { return ServerRuntime.existingEncounter(server) == EncounterRuntime.this; }
            public boolean closing() { return closing; }
            public void cancelAction(UUID actor, String reason) { if (actions != null) actions.cancel(actor, reason); }
            public boolean actionRunning(UUID actor) { return actions != null && actions.running(actor); }
            public boolean actionControlFault(UUID actor) { return actions != null && actions.controlFault(actor); }
            public void drainImmediateTriggers(LiveActorContext actor, UUID encounter, UUID parent) { EncounterRuntime.this.drainImmediateTriggers(actor, encounter, parent); }
            public void syncBodyStateTransitions() { EncounterRuntime.this.syncBodyStateTransitions(); }
            public void persistNow() { EncounterRuntime.this.persistNow(); }
            public void endTurn(UUID encounter, ServerPlayer player, UUID operation, long version) { combat.endTurn(encounter, player, operation, version); }
            public OperationRecord.Snapshot operationSnapshot(UUID operationId, UUID parentId, UUID encounterId,
            UUID owner, UUID source, UUID target, long tick, long version, GridCell sourceCell,
            GridCell targetCell, OperationRecord.Kind kind) { return EncounterRuntime.this.operationSnapshot(operationId, parentId, encounterId, owner, source, target, tick, version, sourceCell, targetCell, kind); }
        }, actorStates, world, projectiles, movement, persistence, projections, vanillaEffects, processes, damageResolution);
        this.combat = new CombatActionExecution(server, engine, actorStates, movement, vanillaEffects, damageResolution,
            new TriggeredAbilities(triggerHost, engine), new CombatActionExecution.Outcomes() {
                public void enterOutcome() { lifecycle.enterOutcome(); }
                public void exitOutcome() { lifecycle.exitOutcome(); }
                public int outcomeDepth() { return lifecycle.outcomeDepth(); }
                public void flushWorldOutcome() { lifecycle.flushWorldOutcome(); }
                public void stop(UUID encounter) { lifecycle.stop(encounter); }
            }, new CombatActionExecution.Host() {
                public long clock() { return cumulativeServerTicks; }
                public boolean knockback(UUID encounter) { return lifecycle.settingsFor(encounter).tacticalKnockbackEnabled(); }
                public boolean admitIntruder(UUID attacker, UUID target) { return lifecycle.admitIntruder(attacker, target); }
                public ActionExecutionCoordinator executions() { return actions; }
                public ProjectileOrigin origin(UUID actor) { return projectiles.origin(actor); }
                public void swing(LivingEntity actor, UUID encounter, UUID operation, net.minecraft.world.InteractionHand hand) { entityProjections.swing(actor, encounter, operation, hand); }
            public OperationRecord.Snapshot operationSnapshot(UUID operationId, UUID parentId, UUID encounterId,
            UUID owner, UUID source, UUID target, long tick, long version, GridCell sourceCell,
            GridCell targetCell, OperationRecord.Kind kind) { return EncounterRuntime.this.operationSnapshot(operationId, parentId, encounterId, owner, source, target, tick, version, sourceCell, targetCell, kind); }
            public void sync(StateView state) { EncounterRuntime.this.sync(state); }
            public Entity resolve(UUID id) { return EncounterRuntime.this.resolve(id); }
            });
        if (!persistence.recoveryFailed() && saved != null) {
            cumulativeServerTicks = saved.cumulativeServerTicks();
            projections.restore(saved.nextSessionSequence(), saved.sessionSequences());
            lifecycle.restore(saved);
            projectiles.restore(saved);
            movement.restoreReceipts(saved.moveEndReceipts(), saved.leases());
            vanillaEffects.restore(saved.participantEffects(),saved.turnSettlements());

        }
        if (!persistence.recoveryFailed()) for (UUID encounterId : engine.encounterIds()) {
            EncounterRegion region = engine.stateView(encounterId).region();
            if (region != null) {
                world.bind(encounterId, region);
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
        closing = true;
        RuntimeException failure = null;
        // Persist active phases first; release observations are saved before held queues are handed back.
        for (Runnable step : List.<Runnable>of(
                () -> { if (actions != null) actions.prepareShutdown(); },
                this::persistNow,
                () -> { if (actions != null) actions.shutdown(); },
                this::persistNow,
                movement::close, world::close, lifecycle::close,
                projections::close, entityProjections::clear, () -> mobDecisions.retain(Set.of()))) {
            try { step.run(); }
            catch (RuntimeException error) { if (failure == null) failure = error; else if (failure != error) failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }

    public void persistIfChanged() {
        requireThread();
        actorStates.persist();
        if (persistence.recoveryFailed()) return;
        if (ServerRuntime.existingEncounter(server)==this) for (UUID id:engine.encounterIds()) {
            if(lifecycle.recovering(id)) continue;
            for(UUID member:engine.stateView(id).members().keySet())
                if(resolve(member) instanceof LivingEntity living) vanillaEffects.capture(living);
        }
        persistence.persistIfChanged(engine, cumulativeServerTicks, this::captureCheckpoint);
    }

    private CombatPersistenceEnvelope captureCheckpoint(EncounterStateSnapshot rules) {
        var pendingArrows = projectiles.pendingSnapshot();
        var starts = lifecycle.startReceipts();
        var exits = lifecycle.exitReceipts();
        var moveEnds = movement.receipts();
        var leases = movement.leaseEvidence();
        return new CombatPersistenceEnvelope(rules, cumulativeServerTicks, projections.lastSequence(),
            projections.sessions(), lifecycle.capturedSettings(), projectiles.origins(),
            projectiles.domains(), pendingArrows,
            lifecycle.pendingMerges().values().stream()
                .map(EncounterStateSnapshot.MergePlanState::capture).toList(),
            starts, exits, moveEnds, leases, projectiles.quarantines(), lifecycle.exitAuthorizations().snapshot(), persistence.checkpoints(), lifecycle.mergeBindings(), projectiles.abilities(), persistence.audits(),
            vanillaEffects.owners(),vanillaEffects.settlements(), processes.snapshot());
    }

    private void persistNow() {
        persistence.changed();
        persistIfChanged();
    }

    public void advancePersistenceClock() {
        requireThread();
        cumulativeServerTicks = Math.addExact(cumulativeServerTicks, 1);
        actorStates.endServerTick(cumulativeServerTicks);
        world.advanceClock();
        if (cumulativeServerTicks % 20 == 0) lifecycle.expireHistory();
    }
    public boolean matchesGeneration(UUID candidate) { return generation.equals(candidate); }
    public UUID generation() { return generation; }
    public boolean isMember(UUID entityId) { return engine.encounterOf(entityId) != null; }
    public void useAttackRandomForGameTest(UUID encounterId, Random random) {
        requireThread();
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer)
            || !engine.encounterIds().contains(encounterId))
            throw new IllegalStateException("attack random injection requires an active GameTest encounter");
        damageResolution.seed(encounterId, Objects.requireNonNull(random));
    }
    public UUID encounterOf(UUID entityId) { return engine.encounterOf(entityId); }

    /** A movement lease permits only vanilla movement packets, not body simulation or other input. */
    /** Diagnostic capture is not a lease validation or a transferable credential. */
    GateDecision.Evidence controlEvidence(Entity entity) {
        UUID encounter = engine.encounterOf(entity.getUUID());
        var mobOperation = movement.mobOperation(entity.getUUID());
        return new GateDecision.Evidence(generation, encounter,
            encounter == null ? null : engine.stateView(encounter).version(),
            entity instanceof PresentationIdentity identity ? identity.dndturn$presentationInstance() : null,
            mobOperation, world.scheduler().step(entity.level()), cumulativeServerTicks);
    }
    private final AbilityWorkBudget abilityWork = new AbilityWorkBudget();
    public boolean takeAbilityWork(UUID actor, UUID encounter, AbilityWorkBudget.Work work) { requireThread(); return abilityWork.take(cumulativeServerTicks, actor, encounter, work); }
    public void requireAbilityWork(UUID actor, UUID encounter, AbilityWorkBudget.Work work) { requireThread(); abilityWork.require(cumulativeServerTicks, actor, encounter, work); }
    public Map<AbilityWorkBudget.Work, AbilityWorkBudget.Counts> abilityWorkCounts() { requireThread(); return abilityWork.counters(); }
    private final MobTurnStrategies.Facts mobDecisionFacts = new MobTurnStrategies.Facts() {
        public MinecraftSnapshotCapture snapshots() { return actorStates.snapshots(); }
        public long actorRevision(UUID actor) { return actorStates.state(actor).revision(); }
        public long planClock() { return cumulativeServerTicks; }
        public boolean takeAbilityWork(UUID actor, UUID encounter, AbilityWorkBudget.Work work) {
            return EncounterRuntime.this.takeAbilityWork(actor, encounter, work);
        }
    };
    public MobTurnStrategies.Facts mobDecisionFacts() { requireThread(); return mobDecisionFacts; }
    private final MobTurnStrategies.TurnCommands mobCommands = new MobTurnStrategies.TurnCommands() {
        public boolean recovering(UUID encounter) { return lifecycle.recovering(encounter); }
        public boolean runningIn(UUID encounter) { return actions().runningIn(encounter); }
        public boolean controlFault(UUID actor) { return actions().controlFault(actor); }
        public Entity resolve(UUID actor) { return EncounterRuntime.this.resolve(actor); }
        public UUID mobOperation(UUID actor) { return movement.mobOperation(actor); }
        public void observeMobMovement(Mob actor, UUID operation) { movement.observeMobPlanMovement(actor, operation); }
        public void endCurrentTurn(UUID encounter, UUID operation, long version) { combat.endCurrentTurn(encounter, operation, version); }
        public void submit(LiveActorContext actor, UUID encounter, UUID operation, long version, ActionIntent intent) { actions().submit(actor, generation, encounter, operation, version, intent, false); }
    };
    public void advanceMobTurns() { mobDecisions.advance(server, engine, mobCommands); }
    private final MobTurnStrategies.Decisions mobDecisions = new MobTurnStrategies.Decisions(mobDecisionFacts);
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
            closing, persistence.recoveryFailed(), encounter != null && lifecycle.recovering(encounter), lifecycle.outcomeDepth() != 0,
            server.tickRateManager().isFrozen(), rejectRunning && actions != null && actions.running(actor.getUUID()),
            actions != null && actions.controlFault(actor.getUUID()),
            state != null && actor.getUUID().equals(state.current()),
            state != null && (player ? state.phase() == EncounterPhase.ACTIVE || state.phase() == EncounterPhase.CANDIDATE
                : state.phase() != EncounterPhase.ENVIRONMENT),
            state != null && state.members().containsKey(actor.getUUID()),
            player || actor instanceof Mob);
    }

    private Entity resolve(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    public boolean mayOrganizeInventory(ServerPlayer player) {
        return OperationAdmissionPolicy.evaluate(captureAdmission(player, false)).allowed();
    }

    public StateView state(UUID encounterId) {
        requireThread();
        return engine.stateView(encounterId);
    }

    public long sessionProjectionSequence(UUID encounterId) {
        requireThread();
        Long sequence = projections.sequence(encounterId);
        if (sequence == null) throw new IllegalArgumentException("unknown session projection");
        return sequence;
    }

    public void flushResultPages() {
        requireThread();
        projections.flush();
    }

    /** The local client receives changes only when it enters or leaves a formal paused region. */
    public void syncBodyStateTransitions() {
        requireThread();
        lifecycle.exitAuthorizations().reconcile(engine, this::resolve);
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

    public void sendBodyState(ServerPlayer player, boolean paused, boolean movementAllowed) {
        requireThread();
        if (closing) return;
        projections.body(player, paused, movementAllowed);
    }

    private void sync(StateView state) {
        projections.publish(state, movement::playerOperation, !lifecycle.recovering(state.id()), player -> lifecycle.assessExit(player, state.id()));
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
        if (closing || persistence.recoveryFailed() || lifecycle.recovering(encounterId))
            throw new IllegalStateException("encounter platform state is not ready");
        if (actions != null && actions.controlFault(owner))
            throw new IllegalStateException("control release requires reconciliation");
        if (tick != cumulativeServerTicks) throw new IllegalArgumentException("operation clock must be server observation time");
        return new OperationRecord.Snapshot(operationId, parentId, encounterId, owner, source, target,
            tick, version, sourceCell, targetCell, kind, generation);
    }
    private void resyncMember(ServerPlayer player, UUID encounter) { lifecycle.resyncMember(player, encounter); }
    public PresentationState.Facts presentationFacts(Entity entity) {
        requireThread();
        UUID member = engine.encounterOf(entity.getUUID());
        UUID controller = null;
        Vec3 center = entity.getBoundingBox().getCenter();
        for (var entry : world.regions().entrySet()) {
            if (entry.getValue().dimension().equals(entity.level().dimension().identifier().toString())
                && entry.getValue().containsPoint(center.x, center.y, center.z) && !exitedRegion(entity, entry.getKey())) {
                controller = entry.getKey(); break;
            }
        }
        var use = PresentationState.Use.STOPPED;
        if (controller != null && entity instanceof LivingEntity living && living.isUsingItem()) {
            UUID identity = actions == null ? null : actions.presentationUseId(entity.getUUID());
            if (identity == null) identity = ((PresentationUseIdentity)living).dndturn$useIdentity();
            if (identity != null) use = new PresentationState.Use(identity, living.getUsedItemHand(), ItemStackFingerprint.revision(living, living.getUseItem()),
                Math.max(0, living.getTicksUsingItem()), Math.max(0, living.getUseItemRemainingTicks()));
        }
        boolean paused = entity instanceof ServerPlayer player ? MinecraftCombatRuntime.isBodyPaused(player) : world.clientEntityPaused(entity);
        return new PresentationState.Facts(member, controller,
            controller == null ? "RELEASED" : engine.stateView(controller).phase().name(), paused, use,
            controller == null ? 0 : engine.environmentTime(controller));
    }
    private void turnStarted(EncounterAuthority.TurnBoundary boundary) {
        actorStates.turnStarted(boundary.encounter(), boundary.round(), boundary.actor());
        lifecycle.turnStarted(boundary.encounter());
    }

}
