package cc.sighs.dndturn.platform.server.ai;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;

import cc.sighs.dndturn.domain.ai.AiMemory;

import cc.sighs.dndturn.application.planning.BudgetedScan;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.ai.AiAffordance;
import cc.sighs.dndturn.domain.ai.AiDecisionContext;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.ai.AiRuntimeState;
import cc.sighs.dndturn.domain.ai.PerceptionSnapshot;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.budget.AbilityWorkBudget;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.fact.MinecraftFactProviders;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/** Strategies propose values only. Execution, resources and navigation belong to the shared PLAN. */
public final class MobTurnStrategies {
    public interface Facts {
        MinecraftSnapshotCapture snapshots();
        long actorRevision(UUID actor);
        long planClock();
        boolean takeAbilityWork(UUID actor, UUID encounter, AbilityWorkBudget.Work work);
    }
    public interface TurnCommands {
        boolean recovering(UUID encounter);
        boolean runningIn(UUID encounter);
        boolean controlFault(UUID actor);
        Entity resolve(UUID actor);
        UUID mobOperation(UUID actor);
        void observeMobMovement(Mob actor, UUID operation);
        void endCurrentTurn(UUID encounter, UUID operation, long version);
        void submit(LiveActorContext actor, UUID encounter, UUID operation, long version, ActionIntent intent);
    }

    private record TargetSample(PerceptionSnapshot.ObservedActor perception, boolean hostile,
                                List<AiDecisionContext.Option> options) {}
    static AiDecisionContext capture(LiveActorContext actor, StateView state,
                                        OperationRecord.Result previous, EncounterAuthority engine, Facts facts) {
        actor = facts.snapshots().context(actor.body());
        return capture(actor, state, previous, engine, new Capture(actor, state, facts),
            new AiRuntimeState(actor.id(), actor.instance(), state.round(), 0, null), facts);
    }
    private record Dependencies(UUID instance, long version, FactSlice facts,
                                net.minecraft.world.phys.Vec3 position, UUID target,
                                List<List<GrantEvidence>> sources, long actorRevision) {}
    private static Dependencies dependencies(LiveActorContext actor, StateView state, Facts facts) {
        var target = ((Mob)actor.body()).getTarget();
        return new Dependencies(actor.instance(), state.version(), MinecraftFactProviders.capture(actor, ActorFacts.DECISION), actor.body().position(),
            target == null ? null : target.getUUID(), cc.sighs.dndturn.platform.server.action.AbilityExecutors.all().stream()
                .map(behavior -> AbilityAdapterRegistry.facts(behavior.id()).checkedSources(actor)).toList(),
            facts.actorRevision(actor.id()));
    }
    private static final class Capture {
        final UUID actor;
        final Dependencies dependencies;
        final ActorSnapshot snapshot;
        final BudgetedScan<UUID, TargetSample> scan;
        Capture(LiveActorContext actor, StateView state, Facts facts) {
            this.actor = actor.id(); this.dependencies = dependencies(actor, state, facts);
            this.snapshot = facts.snapshots().capture(actor, state);
            scan = new BudgetedScan<>(state.members().keySet().stream().sorted().toList(), 4096);
        }
    }
    private static AiDecisionContext capture(LiveActorContext actor, StateView state,
                                        OperationRecord.Result previous, EncounterAuthority engine, Capture capture, AiRuntimeState runtime, Facts facts) {
        var mob = (Mob) actor.body();
        var service = facts;
        boolean complete = capture.scan.advance(() -> service.takeAbilityWork(actor.id(),
            state.id(), AbilityWorkBudget.Work.CANDIDATE), id -> {
            if (id.equals(actor.id())) return null;
            var entity = actor.level().getEntity(id);
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()) return null;
            boolean visible = mob.hasLineOfSight(living), current = mob.getTarget() == living;
            // A native commitment does not reveal the target's new position through walls.
            if (!visible) {
                var known = runtime.memory().known(id, state.round());
                if (known == null) return null;
                var last = known.observation(); var point = last.evidence().lastKnownPosition();
                var evidence = new PerceptionSnapshot.Evidence(point, state.round() - known.observedRound(),
                        last.evidence().sensor(), last.evidence().confidence(), last.evidence().relations());
                double distance = mob.position().distanceToSqr(new net.minecraft.world.phys.Vec3(point.x(), point.y(), point.z()));
                return new TargetSample(new PerceptionSnapshot.ObservedActor(id, false, current, last.player(),
                        false, distance, evidence), false, List.of());
            }
            if (!current) {
                double follow;
                try { follow = capture.dependencies.facts().read(ActorFacts.DECISION, ActorFacts.FOLLOW_EFFECTIVE); }
                catch (FactSlice.MissingFact unavailable) { return null; }
                if (mob.distanceToSqr(living) > follow * follow) return null;
            }
            var aim = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, state.region().dimension(), id, null, -1, 0, 0, 0);
            var options = cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor, capture.snapshot, state, aim).stream()
                .map(o -> new AiDecisionContext.Option(id, o.binding(),
                    o.availability() == cc.sighs.dndturn.platform.server.ability.AbilityQueries.Availability.EXECUTABLE
                        || o.availability() == cc.sighs.dndturn.platform.server.ability.AbilityQueries.Availability.APPROACH_REQUIRED,
                    AiDefinitions.SEMANTICS.get(o.binding().id(), o.binding().version())))
                // An equipped/specialized ranged action keeps priority over the universal melee fallback.
                .sorted(Comparator.comparingInt(o -> o.semantics().contains(AiAffordance.RANGED) ? 0 : 1)).toList();
            var relations = new HashSet<PerceptionSnapshot.Relation>();
            if (mob.isAlliedTo(living)) relations.add(PerceptionSnapshot.Relation.ALLY);
            if ((engine.isHostile(state.id(), actor.id(), id) || engine.isHostile(state.id(), id, actor.id()))) relations.add(PerceptionSnapshot.Relation.HOSTILE);
            var position = living.position();
            return new TargetSample(new PerceptionSnapshot.ObservedActor(id, visible, current,
                living instanceof ServerPlayer, mob.canAttack(living), mob.distanceToSqr(living),
                new PerceptionSnapshot.Evidence(new ActionIntent.Point(position.x, position.y, position.z), 0,
                        "dndturn:line_of_sight", 1, relations)),
                (engine.isHostile(state.id(), actor.id(), id) || engine.isHostile(state.id(), id, actor.id())), options);
        });
        if (!complete) throw new ActionFailure(ActionFailure.Code.EVALUATION_DEFERRED,
            ActionFailure.Retry.REFRESH_AND_REPROPOSE, "AI discovery retained its cursor for the next server tick");
        var samples = capture.scan.results();
        return new AiDecisionContext(capture.snapshot, AiDefinitions.definition(capture.snapshot.definition(), mob),
            state.phase(), state.members().get(actor.id()).action(), state.region().dimension(),
            new PerceptionSnapshot(samples.stream().map(TargetSample::perception).toList()),
            samples.stream().filter(TargetSample::hostile).map(t -> t.perception().id()).collect(java.util.stream.Collectors.toSet()),
            samples.stream().flatMap(t -> t.options().stream()).toList(),
            MovementPorts.find(mob) == null ? null : MovementPorts.require(mob).port().opportunity(actor, state, previous), runtime);
    }
    private MobTurnStrategies() {}
    public static void freeze() { AiDefinitions.freeze(); MovementPorts.freeze(); }
    /** Bounded decision memory, not a resource balance. Reset on owner, instance or turn changes. */
    public static final class Decisions {
        private final Facts facts;
        private long decisionCursor;
        public Decisions(Facts facts) { this.facts = Objects.requireNonNull(facts); }

        private record Proposal(UUID actor, UUID instance, long version, UUID operation, ActionIntent intent) {}
        private final Map<UUID, AiRuntimeState> turns = new HashMap<>();
        private final Map<UUID, Proposal> proposals = new HashMap<>();
        private final Map<UUID, AiRuntimeState.Waiting> waits = new HashMap<>();
        private final Map<UUID, Capture> captures = new HashMap<>();
        private record MemoryOwner(UUID encounter, UUID actor, UUID instance) {}
        private final Map<MemoryOwner, AiMemory> memories = new HashMap<>();
        public void retain(Set<UUID> ids) { turns.keySet().retainAll(ids); proposals.keySet().retainAll(ids); waits.keySet().retainAll(ids); captures.keySet().retainAll(ids);
            memories.keySet().removeIf(key -> !ids.contains(key.encounter())); }
        public ActionIntent next(LiveActorContext actor, StateView state, EncounterAuthority engine) {
            var old = turns.get(state.id());
            if (old == null || !old.actor().equals(actor.id()) || !old.instance().equals(actor.instance()) || old.round() != state.round()) {
                memories.keySet().removeIf(key -> key.actor().equals(actor.id()) && (!key.instance().equals(actor.instance())
                        || !key.encounter().equals(state.id())));
                old = new AiRuntimeState(actor.id(), actor.instance(), state.round(), 0, null,
                        memories.getOrDefault(new MemoryOwner(state.id(), actor.id(), actor.instance()), AiMemory.empty()));
                turns.put(state.id(), old);
                proposals.remove(state.id());
                waits.remove(state.id());
                captures.remove(state.id());
            }
            long now = facts.planClock();
            var waiting = waits.get(state.id());
            if (waiting != null && now < waiting.until()) throw deferredWait();
            if (old.count() >= 8) throw new IllegalStateException("AI_DECISION_LIMIT");
            var previous = old.operation() == null ? null : engine.resultFor(state.id(), old.operation());
            if (old.operation() != null && (previous == null || previous.outcome() == OperationRecord.Outcome.UNKNOWN
                || previous.outcome() == OperationRecord.Outcome.REJECTED || previous.outcome() == OperationRecord.Outcome.INTERRUPTED)) return null;
            var cached = proposals.get(state.id());
            if (cached != null && cached.actor.equals(actor.id()) && cached.instance.equals(actor.instance())
                && cached.version == state.version()) return cached.intent;
            proposals.remove(state.id());
            var pending = captures.get(state.id());
            if (pending == null || !pending.actor.equals(actor.id()) || !pending.dependencies.equals(dependencies(actor, state, facts))) {
                pending = new Capture(actor, state, facts);
                captures.put(state.id(), pending);
            }
            var definition = AiDefinitions.definition(pending.snapshot.definition(), (Mob)actor.body());
            var input = capture(actor, state, previous, engine, pending, old, facts);
            var memory = old.memory().remember(input.perception(), state.round(), state.version());
            var memoryOwner = new MemoryOwner(state.id(), actor.id(), actor.instance());
            if (!memories.containsKey(memoryOwner) && memories.size() >= 4096) throw new IllegalStateException("AI_MEMORY_LIMIT");
            memories.put(memoryOwner, memory);
            turns.put(state.id(), new AiRuntimeState(old.actor(), old.instance(), old.round(), old.count(), old.operation(), memory));
            var decision = AiPlanner.decide(input);
            DebugDiagnostics.log("MOB_DECISION", () -> "actor=" + actor.id() + " instance=" + actor.instance()
                + " encounter=" + state.id() + " version=" + state.version() + " round=" + state.round()
                + " strategy=" + definition.id() + "@" + definition.version()
                + " decision=" + decision.getClass().getSimpleName()
                + (decision instanceof AiPlanner.Propose proposal ? " " + DebugDiagnostics.intent(proposal.intent()) : ""));
            captures.remove(state.id());
            if (decision instanceof AiPlanner.Wait wait) {
                int total = (waiting == null ? 0 : waiting.total()) + wait.ticks();
                if (total > 100) throw new IllegalStateException("AI_WAIT_LIMIT");
                waits.put(state.id(), new AiRuntimeState.Waiting(Math.addExact(now, wait.ticks()), total));
                throw deferredWait();
            }
            var intent = decision instanceof AiPlanner.Propose proposal ? proposal.intent() : null;
            if (intent != null) proposals.put(state.id(), new Proposal(actor.id(), actor.instance(), state.version(), UUID.randomUUID(), intent));
            return intent;
        }
    public void advance(MinecraftServer server, EncounterAuthority engine, TurnCommands commands) {
        if (!server.isSameThread()) throw new IllegalStateException("AI requires server thread");
        if (server.tickRateManager().isFrozen()) return;
        retain(engine.encounterIds());
        var decisionOrder = new ArrayList<>(engine.encounterIds());
        decisionOrder.sort(UUID::compareTo);
        if (!decisionOrder.isEmpty()) java.util.Collections.rotate(decisionOrder, -(int)Math.floorMod(decisionCursor++, decisionOrder.size()));
        for (UUID encounterId : decisionOrder) {
            if (commands.recovering(encounterId) || commands.runningIn(encounterId)) continue;
            var state = engine.stateView(encounterId);
            if (state.phase() == EncounterPhase.ENVIRONMENT || state.current() == null) continue;
            if (!(commands.resolve(state.current()) instanceof Mob mob) || commands.controlFault(mob.getUUID())) continue;
            var leased = commands.mobOperation(mob.getUUID());
            if (leased != null) { commands.observeMobMovement(mob, leased); continue; }
            if (engine.hasPendingOperations(encounterId)) continue;
            if (!facts.takeAbilityWork(mob.getUUID(), encounterId, AbilityWorkBudget.Work.AI)) continue;
            ActionIntent decision;
            try {
                for (UUID ignored : state.members().keySet())
                    if (!facts.takeAbilityWork(mob.getUUID(), encounterId, AbilityWorkBudget.Work.CANDIDATE)) throw new ActionFailure(ActionFailure.Code.EVALUATION_DEFERRED, ActionFailure.Retry.REFRESH_AND_REPROPOSE, "AI work budget exhausted");
                decision = next(facts.snapshots().context(mob), state, engine);
            }
            catch (RuntimeException failure) {
                DebugDiagnostics.log("MOB_DECISION_FAILURE", () -> "actor=" + mob.getUUID()
                    + " encounter=" + encounterId + " failure=" + ActionFailure.classify(failure) + " reason=" + failure.getMessage());
                if (failure instanceof ActionFailure deferred && deferred.code() == ActionFailure.Code.EVALUATION_DEFERRED) continue;
                decision = null;
            }
            if (decision == null) {
                commands.endCurrentTurn(encounterId, UUID.randomUUID(), state.version());
            } else {
                UUID operation = operation(state);
                try {
                    commands.submit(facts.snapshots().context(mob), encounterId, operation, state.version(), decision);
                    submitted(state, operation);
                } catch (ActionFailure deferred) {
                    if (deferred.code() != ActionFailure.Code.EVALUATION_DEFERRED) throw deferred;
                    // No root was accepted. Preserve this turn's decision allowance for a later tick.
                }
            }
        }
    }
        private static ActionFailure deferredWait() {
            return new ActionFailure(ActionFailure.Code.EVALUATION_DEFERRED,
                ActionFailure.Retry.REFRESH_AND_REPROPOSE, "AI observation wait");
        }
        public UUID operation(StateView state) { return proposals.get(state.id()).operation; }
        public void submitted(StateView state, UUID operation) {
            var old = turns.get(state.id());
            turns.put(state.id(), old.submitted(operation));
            proposals.remove(state.id());
        }
    }
}
