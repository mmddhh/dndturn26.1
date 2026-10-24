package cc.sighs.dndturn.platform.server.action;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.platform.server.damage.DamageResolution;
import static cc.sighs.dndturn.platform.server.damage.DamageResolution.*;
import cc.sighs.dndturn.platform.server.movement.MovementExecutionCoordinator;
import cc.sighs.dndturn.domain.encounter.operation.CausalEvent;
import cc.sighs.dndturn.domain.encounter.operation.Intervention;
import cc.sighs.dndturn.platform.server.damage.BodyTargets;
import cc.sighs.dndturn.domain.actor.TriggeredAbilityInvocation;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.EncounterRegion;
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
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.AbilityExecutor;
import cc.sighs.dndturn.platform.server.action.ActionExecutionCoordinator;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.damage.DamageReceivers;
import cc.sighs.dndturn.platform.server.damage.MeleeAdapters;
import cc.sighs.dndturn.platform.server.damage.TacticalDamageContext;
import cc.sighs.dndturn.platform.server.action.TriggeredAbilities;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Executes attacks and explicit turn actions through the shared rule authority and observed world effects. */
public final class CombatActionExecution implements cc.sighs.dndturn.platform.server.damage.DamageAttempt {
    public interface Outcomes {
        void enterOutcome();
        void exitOutcome();
        int outcomeDepth();
        void flushWorldOutcome();
        void stop(UUID encounter);
    }
    public interface Host {
        long clock();
        boolean knockback(UUID encounter);
        boolean admitIntruder(UUID attacker, UUID target);
        ActionExecutionCoordinator executions();
        ProjectileOrigin origin(UUID actor);
        void swing(LivingEntity actor, UUID encounter, UUID operation, net.minecraft.world.InteractionHand hand);
        OperationRecord.Snapshot operationSnapshot(UUID operationId, UUID parentId, UUID encounterId,
            UUID owner, UUID source, UUID target, long tick, long version, GridCell sourceCell,
            GridCell targetCell, OperationRecord.Kind kind);
        void sync(StateView state);
        Entity resolve(UUID id);
    }
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final EncounterAuthority engine;
    private final ActorStateAuthority actorStates;
    private final MovementExecutionCoordinator movement;
    private final VanillaEffectRoundController vanillaEffects;
    private final DamageResolution damageResolution;
    private final TriggeredAbilities triggers;
    private final Outcomes outcomes;
    private final Host host;
    public CombatActionExecution(MinecraftServer server, EncounterAuthority engine, ActorStateAuthority actorStates,
            MovementExecutionCoordinator movement, VanillaEffectRoundController vanillaEffects, DamageResolution damageResolution,
            TriggeredAbilities triggers, Outcomes outcomes, Host host) {
        this.server = server; this.engine = engine; this.actorStates = actorStates; this.movement = movement;
        this.vanillaEffects = vanillaEffects; this.damageResolution = damageResolution; this.triggers = triggers;
        this.outcomes = outcomes; this.host = host;
    }

    private DamageTrace.DamageEvidence damageEvidence(Entity actor, StateView state,
            DamageTrace.Stage stage, TacticalDamageContext.HurtObservation observed,
            List<DamageTrace.EquipmentChange> changes) {
        return DamageResolution.damageEvidence(actor, state, stage, observed, changes,
            host.origin(actor.getUUID()), host.knockback(state.id()));
    }
    public void drainImmediateTriggers(LiveActorContext actor, UUID encounter, UUID parent) {
        requireThread();
        var operation = engine.pendingOperation(encounter, parent);
        if (operation != null) triggers.drain(actor, operation,
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
        var actor = actorStates.context(target);
        actorStates.damageAttempt(actor, event);
        var decision = triggers.drainAttempt(actor, operation, event);
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


    public static boolean inMeleeReach(LivingEntity actor, LivingEntity target) {
        BlockPos from = actor.blockPosition();
        BlockPos to = target.blockPosition();
        return from.distManhattan(to) <= 3
            && new GridCell(from.getX(), from.getY(), from.getZ()).chebyshev(
                new GridCell(to.getX(), to.getY(), to.getZ())) <= CombatRules.MELEE_RANGE
            && actor.hasLineOfSight(target);
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
        StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion || (state.phase() != EncounterPhase.ACTIVE && state.phase() != EncounterPhase.CANDIDATE)
            || !actor.getUUID().equals(state.current()) || MovementExecutionCoordinator.unsupportedPlayerMovement(actor)
            || movement.hasPlayerMoveLease(actor.getUUID())
            || !state.region().dimension().equals(actor.level().dimension().identifier().toString())
            || !state.region().containsPoint(pointOf(actor).x(), pointOf(actor).y(), pointOf(actor).z()))
            throw new IllegalStateException("DASH is not authorized in this phase or position");
        Math.addExact(expectedVersion, 2);
        BlockPos pos = actor.blockPosition();
        OperationRecord.Snapshot snapshot = host.operationSnapshot(operationId, null,
            encounterId, actor.getUUID(), actor.getUUID(), null, host.clock(),
            expectedVersion, new GridCell(pos.getX(), pos.getY(), pos.getZ()), null,
            OperationRecord.Kind.DASH);
        if (!engine.beginOperation(snapshot)) throw new IllegalStateException("DASH action is unavailable");
        OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
            OperationRecord.Outcome.COMPLETED,
            "DASH granted " + engine.movementTicksPerTurn(encounterId) + " movement ticks", 0, 0, true);
        host.sync(engine.stateView(encounterId));
        return result;
    }

    /** Prototype defensive host.executions() use the same server-owned action budget as ATTACK and DASH. */
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
        StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion || state.phase() != EncounterPhase.ACTIVE
            || !actor.getUUID().equals(state.current()) || movement.hasPlayerMoveLease(actor.getUUID())
            || !state.region().dimension().equals(actor.level().dimension().identifier().toString())
            || !state.region().containsPoint(pointOf(actor).x(), pointOf(actor).y(), pointOf(actor).z()))
            throw new IllegalStateException(kind + " is not authorized in this phase or position");
        Math.addExact(expectedVersion, 2);
        BlockPos pos = actor.blockPosition();
        OperationRecord.Snapshot snapshot = host.operationSnapshot(operationId, null,
            encounterId, actor.getUUID(), actor.getUUID(), null, host.clock(),
            expectedVersion, new GridCell(pos.getX(), pos.getY(), pos.getZ()), null, kind);
        if (!engine.beginOperation(snapshot)) throw new IllegalStateException(kind + " action is unavailable");
        OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
            OperationRecord.Outcome.COMPLETED,
            kind == OperationRecord.Kind.DODGE ? "dodge until next own turn"
                : "disengage until end of this turn", 0, 0, true);
        host.sync(engine.stateView(encounterId));
        return result;
    }

    /** Explicit administrator capability: end the current member's turn, including a Mob turn. */
    public OperationRecord.Result endCurrentTurn(UUID encounterId,
                                                                UUID operationId, long expectedVersion) {
        requireThread();
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) return matchingEndTurn(previous, previous.snapshot().owner(), expectedVersion);
        StateView state = engine.stateView(encounterId);
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
        StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion) throw ActionFailure.staleEncounter();
        if (!actor.level().tickRateManager().runsNormally()) throw new IllegalStateException("global freeze holds turn settlement");
        if (host.executions() != null && host.executions().running(owner))
            throw new IllegalStateException("cancel the active plan before ending the turn");
        if (host.executions() != null && host.executions().controlFault(owner))
            throw new IllegalStateException("control release requires reconciliation before ending the turn");
        var pos = actor.blockPosition();
        OperationRecord.Snapshot snapshot = host.operationSnapshot(operationId, null,
            encounterId, owner, owner, null, host.clock(),
            state.version(), new GridCell(pos.getX(), pos.getY(), pos.getZ()), null,
            OperationRecord.Kind.END_TURN);
        if (!engine.beginOperation(snapshot)) throw new IllegalStateException("end turn not authorized");
        outcomes.enterOutcome();
        try {
        if(actor instanceof LivingEntity living) vanillaEffects.settle(living,snapshot);
        if (state.phase() == EncounterPhase.CANDIDATE) {
            // A candidate turn boundary reviews every directed Mob target, including player turns.
            for (UUID sourceId : state.members().keySet()) {
                Entity found = host.resolve(sourceId);
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
            triggers.drain(actorStates.context(living), snapshot,
                    TriggeredAbilityInvocation.Timing.OWNER_TURN_END);
        OperationRecord.Result result = engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
            "turn ended", 0, 0, true);
        return result;
        } catch(RuntimeException failure) {
            if(engine.pendingOperation(encounterId,operationId)!=null)
                engine.publish(encounterId,operationId,0,OperationRecord.Outcome.UNKNOWN,
                    "turn settlement uncertain; not replayed: "+failure.getClass().getSimpleName(),0,0,true);
            // Unknown native effects cannot be made safe by trying the boundary again.
            outcomes.stop(encounterId);
            throw failure;
        } finally {
            outcomes.exitOutcome();
            if(outcomes.outcomeDepth()==0) {
                outcomes.flushWorldOutcome();
            }
            if(engine.encounterIds().contains(encounterId)) host.sync(engine.stateView(encounterId));
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
        return attack(actor, targetId, operationId, expectedVersion, null, false);
    }
    public OperationRecord.Result attackPlan(LiveActorContext context, UUID target, UUID operation, UUID parent,
                                     java.util.function.Consumer<OperationRecord.Result> complete) {
        context.verifyCurrent();
        LivingEntity actor = context.body();
        outcomes.enterOutcome();
        try {
            var result = attack(actor, target, operation, engine.stateView(engine.encounterOf(actor.getUUID())).version(), parent, false);
            // The confirmed synchronous attack is the final step of this melee plan.
            complete.accept(result);
            return result;
        } finally {
            outcomes.exitOutcome();
            if (outcomes.outcomeDepth() == 0) {
                outcomes.flushWorldOutcome();
            }
        }
    }
    private OperationRecord.Result attack(LivingEntity actor, UUID targetId,
                                          UUID operationId, long expectedVersion, UUID planParent, boolean intrusion) {
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
        StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion) throw ActionFailure.staleEncounter();
        if (state.phase() != EncounterPhase.CANDIDATE && state.phase() != EncounterPhase.ACTIVE)
            throw new IllegalStateException("not a member attack phase");
        if (!state.members().containsKey(targetId) || !intrusion && !actor.getUUID().equals(state.current()))
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
        // Mob-to-Mob requires Encounter hostility; player attacks still cannot become PvP.
        if (actor == target || actor instanceof ServerPlayer && target instanceof ServerPlayer
            || !(actor instanceof ServerPlayer) && !(target instanceof ServerPlayer)
                && !engine.isHostile(encounterId, actor.getUUID(), targetId)
                && !engine.isHostile(encounterId, targetId, actor.getUUID()))
            throw new IllegalStateException("melee relationship unsupported");
        Vec3 actorCenter = actor.getBoundingBox().getCenter();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        if (!state.region().dimension().equals(level.dimension().identifier().toString())
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
            host.knockback(encounterId), false, actor instanceof ServerPlayer)
            : AbilityExecutors.resolve(plan.intent()).meleeEffects(actorStates.context(actor), host.knockback(encounterId));
        double weaponAttribute = effects.baseDamage();
        var defenseFacts = actorStates.snapshots().defense(target);
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
        OperationRecord.Snapshot root = host.operationSnapshot(operationId, planParent,
            encounterId, actor.getUUID(), actor.getUUID(), targetId, host.clock(), expectedVersion,
            new GridCell(origin.getX(), origin.getY(), origin.getZ()),
            new GridCell(destination.getX(), destination.getY(), destination.getZ()), OperationRecord.Kind.ATTACK);
        if (!(intrusion ? engine.beginIntrusionAttack(root) : planParent == null ? engine.beginOperation(root) : engine.beginPlanStep(root))) throw new IllegalStateException("attack already executing or unauthorized");
        UUID permitId = null;
        UUID damageId = null;
        boolean effectStarted = false;
        try {
        // Candidate activation can deliver an actor turn boundary. Rebind facts after that rule mutation.
        defenseFacts = actorStates.snapshots().defense(target);
        reduction = RuleResolver.reduction(defenseFacts);
        ac = RuleResolver.armorClass(defenseFacts);
        AttackResolution.validateDamage(weaponDamage,
                new AttackResolution.Defense(ac, reduction));
        if (plan != null && actorStates.snapshots().capture(actorStates.context(actor), AbilityAdapterRegistry.definitions().require(plan.intent()))
                .abilities().stream().noneMatch(binding -> binding.id().equals(plan.intent().behaviorId())
                    && binding.source().equals(plan.intent().source()))) throw ActionFailure.source("grant changed at attack admission");
        // Admission above is the once-only operation boundary, including misses and zero damage.
        DebugDiagnostics.log("ATTACK_ACCEPTED", () -> "op=" + operationId + " parent=" + planParent
            + " encounter=" + encounterId + " actor=" + actor.getUUID() + " target=" + targetId);
        host.swing(actor, encounterId, operationId, net.minecraft.world.InteractionHand.MAIN_HAND);
        CombatRules.RollMode rollMode = CombatRules.mode(openingAdvantage,
            state.members().get(targetId).dodging());
        var damagePlan = damageResolution.resolveAttack(encounterId, ac, reduction, weaponDamage, rollMode, null);
        CombatRules.AttackRoll roll = damagePlan.roll();
        if (!roll.hit()) {
            DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
                weaponDamage, reduction, 0, false, 0, 0,
                damageEvidence(actor, state, DamageTrace.Stage.MISS, null, List.of()));
            OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
                OperationRecord.Outcome.COMPLETED, "miss: die=" + roll.die() + " AC=" + ac, 0, 0, true, trace);
            host.sync(engine.stateView(encounterId));
            return result;
        }
        int tacticalDamage = damagePlan.damage();
        if (tacticalDamage == 0 && !effects.observeZeroDamage()) {
            DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
                weaponDamage, reduction, 0, false, 0, 0,
                damageEvidence(actor, state, DamageTrace.Stage.ZERO_DAMAGE, null, List.of()));
            OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
                OperationRecord.Outcome.COMPLETED, "hit with zero tactical damage", 0, 0, true, trace);
            host.sync(engine.stateView(encounterId));
            return result;
        }
        permitId = UUID.randomUUID();
        engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permitId, encounterId, operationId,
            actor.getUUID(), Set.of(targetId), Set.of(EncounterPhase.ACTIVE), 1,
            engine.stateView(encounterId).round()));
        damageId = UUID.randomUUID();
        OperationRecord.Snapshot child = host.operationSnapshot(damageId, operationId,
            encounterId, actor.getUUID(), actor.getUUID(), targetId, host.clock(),
            engine.stateView(encounterId).version(), root.sourceCell(), root.targetCell(),
            OperationRecord.Kind.DAMAGE);
        if (!engine.beginOperation(child, permitId)) throw new IllegalStateException("damage permit rejected");
        DamageSource source = level.damageSources().source(TacticalDamageContext.DAMAGE_TYPE, actor);
        float healthBefore = target.getHealth();
        float absorptionBefore = target.getAbsorptionAmount();
        Map<EquipmentKey, EquipmentValue> equipmentBefore = equipmentSnapshot(actor, target);
        effectStarted = true;
        outcomes.enterOutcome();
        try {
            var observed = TacticalDamageContext.hurtObserved(level, target, source, tacticalDamage,
                effects.knockback(), damageId, this);
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
            LOGGER.error("Tactical damage {} requires reconciliation", damageId, error);
            if (engine.resultFor(encounterId, operationId) == null)
                return engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.UNKNOWN,
                    "world damage outcome unknown", 0, 0, true);
            return engine.resultFor(encounterId, operationId);
        } finally {
            outcomes.exitOutcome();
            outcomes.flushWorldOutcome();
            outcomes.flushWorldOutcome();
            if (engine.encounterIds().contains(encounterId)) host.sync(engine.stateView(encounterId));
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
                host.sync(engine.stateView(encounterId));
                return failed;
            }
            throw error;
        }
    }

    public DamageTrace rangedTrace(LivingEntity player, UUID targetId, UUID operation, boolean opening) {
        var state = engine.stateView(engine.encounterOf(player.getUUID()));
        LivingEntity target = (LivingEntity) player.level().getEntity(targetId);
        var defenseFacts = actorStates.snapshots().defense(target);
        int ac = RuleResolver.armorClass(defenseFacts);
        var mode = CombatRules.mode(opening, state.members().get(targetId).dodging());
        int reduction = RuleResolver.reduction(defenseFacts);
        // The launch intent captures the roll; actual projectile base damage is captured at insertion.
        double base = 0;
        var plan = damageResolution.resolveAttack(state.id(), ac, reduction, base, mode, null);
        var roll = plan.roll();
        return attackTrace(operation, targetId, mode, roll, ac, base, reduction,
            plan.damage(), false, 0, 0, null);
    }
    private void requireThread() { if (!server.isSameThread()) throw new IllegalStateException("combat action requires server thread"); }
    private static EncounterRegion.Point pointOf(Entity entity) { Vec3 c = entity.getBoundingBox().getCenter(); return new EncounterRegion.Point(c.x, c.y, c.z); }

    public OperationRecord.Result intrusionAttack(LiveActorContext context, UUID target, UUID operation,
                                           java.util.function.Consumer<OperationRecord.Result> complete) {
        context.verifyCurrent();
        LivingEntity actor = context.body();
        outcomes.enterOutcome();
        try {
            var result = attack(actor, target, operation,
                engine.stateView(engine.encounterOf(actor.getUUID())).version(), null, true);
            complete.accept(result);
            return result;
        } finally {
            outcomes.exitOutcome();
            if (outcomes.outcomeDepth() == 0) outcomes.flushWorldOutcome();
        }
    }
    /** The caller has canceled native damage; resolve before leaving that damage boundary. */
    public boolean interceptMeleeIntrusion(LivingEntity target, DamageSource source) {
        requireThread();
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
            || attacker == target || attacker.isDeadOrDying() || target.isDeadOrDying()
            || attacker.level() != target.level() || host.resolve(attacker.getUUID()) != attacker
            || host.resolve(target.getUUID()) != target
            || attacker instanceof ServerPlayer && target instanceof ServerPlayer) return false;
        UUID encounterId = engine.encounterOf(target.getUUID());
        if (encounterId == null || engine.encounterOf(attacker.getUUID()) != null) return false;
        StateView state = engine.stateView(encounterId);
        if ((state.phase() != EncounterPhase.CANDIDATE && state.phase() != EncounterPhase.ACTIVE)
            || engine.hasPendingOperations(encounterId)) return false;
        if (!inMeleeReach(attacker, target) || MeleeAdapters.server().attacker(attacker) == null
            || DamageReceivers.server().find(target) == null) return false;
        if (!host.admitIntruder(attacker.getUUID(), target.getUUID())) return false;
        UUID operation = UUID.randomUUID();
        intrusionAttack(actorStates.context(attacker), target.getUUID(), operation, result -> {});
        return true;
    }
}
