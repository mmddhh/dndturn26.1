package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.domain.encounter.operation.CausalEvent;
import cc.sighs.dndturn.domain.encounter.operation.Intervention;

import cc.sighs.dndturn.application.actor.ActorCompilation;
import cc.sighs.dndturn.application.inspection.ActorViews;
import cc.sighs.dndturn.application.inspection.InspectionPolicy;
import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.actor.TriggeredAbilityInvocation;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectTransition;
import cc.sighs.dndturn.domain.effect.EffectWave;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.domain.encounter.operation.WorldOutcomeObservation;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.persistence.ActorSavedData;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/** Bounded server-lifetime owner; no Entity, level or execution object is stored in actor state. */
public final class ActorStateAuthority {
    private final MinecraftServer server;
    private final ActorSavedData saved;
    private final ActorStates owner;
    private final RuntimeException recoveryFailure;
    private final Set<UUID> advanced = new HashSet<>();
    private final ArrayDeque<UUID> capacityChecks = new ArrayDeque<>();
    private boolean dirty;
    private int saveDelay;
    private record Queued(ActorActivations.Event event, int depth, long order) {}
    private final ArrayDeque<Queued> reactionQueue = new ArrayDeque<>();
    private boolean draining;
    private UUID reactionRoot;
    private int reactionDepth, reactionEvents, reactionMutations;
    private long reactionOrder;
    private ActorStates.ReactionDelivery reactionDelivery;
    public ActorStateAuthority(MinecraftServer server) {
        this.server = server;
        this.saved = server.getDataStorage().computeIfAbsent(ActorSavedData.TYPE);
        ActorStates restored = null; RuntimeException failed = null;
        try {
            restored = saved.restore();
            int archived = restored.prepareCapacityRecovery();
            if (archived > 0) {
                dirty = true;
                com.mojang.logging.LogUtils.getLogger().info("Actor simulation history compacted: deliveries={}, recoveryCandidates={}",
                        archived, restored.capacityCandidates().size());
            }
            for (var invocation : restored.invocations()) if (invocation.status() == TriggeredExecutionRecord.Status.STARTED) {
                restored.invocationStatus(invocation.operation(), TriggeredExecutionRecord.Status.UNKNOWN, "native execution interrupted; not replayed");
                dirty = true;
            }
        }
        catch (RuntimeException invalid) {
            failed = invalid;
            com.mojang.logging.LogUtils.getLogger().error("Actor checkpoint rejected; original data retained and actor rules unavailable", invalid);
        }
        this.owner = restored; this.recoveryFailure = failed;
        if (failed == null) capacityChecks.addAll(restored.capacityCandidates().keySet());
    }
    private void thread() { if (!server.isSameThread()) throw new IllegalStateException("actor server thread required"); }
    private void available() { if (recoveryFailure != null) throw new IllegalStateException("actor checkpoint unavailable; original retained", recoveryFailure); }
    public ActorStates.State state(UUID actor) { thread(); available(); return owner.state(actor); }
    public String fault(UUID actor) { thread(); available(); return owner.faultReason(actor); }
    public TriggeredExecutionRecord invocation(UUID operation) { thread(); available(); return owner.invocation(operation); }
    public void intervention(UUID operation, Intervention decision) {
        thread(); available(); owner.intervention(operation, decision); dirty = true;
    }
    public List<TriggeredExecutionRecord> invocations(UUID actor) {
        thread(); available(); return owner.invocations().stream().filter(i -> i.event().actor().equals(actor)).toList();
    }
    public void invocationStatus(UUID operation, TriggeredExecutionRecord.Status status, String reason) {
        thread(); available(); owner.invocationStatus(operation, status, reason); dirty = true;
    }
    public void observeInvocation(UUID operation, WorldOutcomeObservation observation) {
        thread(); available(); owner.observeInvocation(operation, observation); dirty = true;
    }
    public ActorViews.CharacterSheet character(LiveActorContext actor, ReadContract visible) {
        thread(); return ActorViews.character(MinecraftSnapshotCapture.captureActor(actor), state(actor.id()), visible);
    }
    public ActorViews.Spellbook spellbook(LiveActorContext actor) {
        thread(); return ActorViews.spellbook(MinecraftSnapshotCapture.captureActor(actor), state(actor.id()), AbilityAdapterRegistry.definitions());
    }
    /** No facts are public by default. A target integration must explicitly authorize a read set. */
    public ActorViews.Inspection inspect(LiveActorContext actor) {
        thread(); return ActorViews.inspect(MinecraftSnapshotCapture.captureActor(actor), new ReadContract(Set.of()));
    }
    /** Called only after InspectionService authorizes the current observer and target. */
    public ActorViews.InspectionView inspect(LiveActorContext observer, LiveActorContext actor, EncounterAuthority.StateView encounter) {
        thread(); observer.verifyCurrent(); actor.verifyCurrent();
        var member = encounter.members().get(actor.id());
        if (member == null || !encounter.members().containsKey(observer.id()))
            throw new IllegalStateException("inspection membership");
        var policy = AbilityAdapterRegistry.inspection();
        var nativeKeys = new HashSet<FactKey<?>>();
        var ruleKeys = new HashSet<FactKey<?>>();
        for (var key : policy.reads().keys()) {
            if (key.equals(InspectionPolicy.DODGING) || key.equals(InspectionPolicy.DISENGAGED)) continue;
            if (MinecraftFactProviders.owns(key)) nativeKeys.add(key); else ruleKeys.add(key);
        }
        var facts = MinecraftFactProviders.capture(actor, new ReadContract(nativeKeys));
        if (!ruleKeys.isEmpty()) facts = facts.merge(MinecraftSnapshotCapture.captureFacts(actor, new ReadContract(ruleKeys)));
        facts = facts.merge(new FactSlice(Map.of(InspectionPolicy.DODGING, member.dodging(),
                InspectionPolicy.DISENGAGED, member.disengaged()), Map.of()));
        return policy.project(new InspectionPolicy.Observer(observer.id(), actor.id()), actor.instance(), UUID.randomUUID(), facts);
    }
    /** Trusted integration command, never a packet handler or implicit query mutation. */
    public ActorStates.State command(LiveActorContext actor, ActorStates.Command command) {
        thread(); actor.verifyCurrent();
        available();
        if (!actor.id().equals(command.actor())) throw new IllegalArgumentException("actor command identity");
        if (!draining) resumeReactions(actor);
        if (owner.receipt(command.operation()) != null) return owner.apply(command);
        for (var change : command.changes()) {
            if (change instanceof ActorStates.Learn learn)
                AbilityAdapterRegistry.definitions().require(learn.grant().ability(), learn.grant().version());
            if (change instanceof ActorStates.Apply apply) {
                AbilityAdapterRegistry.effects().require(apply.effect().definition());
                for (var modifier : apply.effect().definition().modifiers()) if (MinecraftFactProviders.owns(new FactKey<>(modifier.stat().id(), Double.class)))
                    throw new IllegalArgumentException("effect cannot rewrite native-owned effective facts; modify a rule statistic");
            }
        }
        if (owner.state(actor.id()).revision() != command.expectedRevision()) throw new IllegalStateException("stale actor command");
        MinecraftSnapshotCapture.validateActorState(actor, ActorStates.reduce(owner.state(actor.id()), command.changes()));
        var result = owner.apply(command); dirty = true;
        if (!AbilityAdapterRegistry.activations().isEmpty()) {
            boolean root = !draining;
            if (root) beginReactions(command.operation());
            try {
                enqueueTransitions(actor.id(), command.operation(), owner.receipt(command.operation()).transitions(), root ? 0 : reactionDepth + 1);
                if (root) drain(actor);
            } catch (RuntimeException failure) { reactionFault(actor.id(), failure); }
        }
        return result;
    }
    public void advance(UUID actor, EffectDefinition.Clock clock) {
        advance(actor, clock, null);
    }
    public void advance(UUID actor, EffectDefinition.Clock clock, UUID cause) {
        thread(); if (recoveryFailure != null) return; var before = owner.state(actor);
        var reduction = owner.lifecycle(actor, new ActorStates.Advance(clock));
        dirty |= before != owner.state(actor);
        var state = owner.state(actor);
        if (state == before) return;
        LivingEntity entity = find(actor);
        if (entity != null) {
            long sequence = state.clocks().getOrDefault(clock, 0L);
            var eventId = UUID.nameUUIDFromBytes((actor + ":" + clock + ":" + sequence).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var kind = clock == EffectDefinition.Clock.TURN_START ? ActivationSpec.Event.TURN_START
                    : clock == EffectDefinition.Clock.TURN_END ? ActivationSpec.Event.TURN_END : null;
            var context = new LiveActorContext(entity);
            if (AbilityAdapterRegistry.activations().isEmpty()) return;
            try {
                beginReactions(eventId);
                enqueueTransitions(actor, cause == null ? eventId : cause, reduction.transitions(), 0);
                enqueue(new ActorActivations.Event(eventId, cause, actor, null, kind, clock, sequence), 0);
                drain(context);
            } catch (RuntimeException failure) { reactionFault(actor, failure); }
        }
    }
    public void simulated(LivingEntity entity) {
        thread();
        if (recoveryFailure != null) return;
        if (!ActorDefinitions.hasProviders() && owner.state(entity.getUUID()).revision() == 0) return;
        // Post can follow a tick which removed, transferred or replaced the original entity.
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)
                || entity.isRemoved() || level.getEntity(entity.getUUID()) != entity) return;
        if (!entity.level().tickRateManager().runsNormally() || !entity.isAlive()) return;
        var actor = new LiveActorContext(entity); actor.verifyCurrent();
        resumeReactions(actor);
        enrollDefinition(actor);
        if (owner.state(actor.id()).revision() == 0) return;
        if (advanced.add(actor.id())) advance(actor.id(), EffectDefinition.Clock.SIMULATION_STEP);
    }
    public void death(UUID actor) {
        thread(); if (recoveryFailure != null) return; var before = owner.state(actor); owner.lifecycle(actor, new ActorStates.Death());
        for (var invocation : owner.invocations()) if (invocation.event().actor().equals(actor) && invocation.status() == TriggeredExecutionRecord.Status.PENDING)
            invocationStatus(invocation.operation(), TriggeredExecutionRecord.Status.REJECTED, "actor died before triggered execution");
        dirty |= before != owner.state(actor); detach(actor);
    }
    public void detach(UUID actor) { thread(); advanced.remove(actor); }
    public void endServerTick() {
        thread(); advanced.clear();
        // Reconciliation is a server lifecycle task, independent of body simulation or input packets.
        int count = Math.min(8, capacityChecks.size());
        for (int n = 0; n < count; n++) {
            UUID id = capacityChecks.removeFirst();
            var entity = find(id);
            if (entity == null) capacityChecks.addLast(id);
            else reconcileCapacityFault(new LiveActorContext(entity));
        }
    }
    public void persist() {
        thread();
        if (saveDelay > 0) { saveDelay--; return; }
        if (recoveryFailure == null && dirty) { saved.update(owner); dirty = false; saveDelay = 19; }
    }
    public void close() { thread(); saveDelay = 0; persist(); advanced.clear(); }
    private void reconcileCapacityFault(LiveActorContext actor) {
        if (!owner.capacityCandidates().containsKey(actor.id())) return;
        // Validate unchanged rule values against current native facts; never replay the missing event.
        try { MinecraftSnapshotCapture.validateActorState(actor, owner.state(actor.id())); }
        catch (RuntimeException invalid) {
            com.mojang.logging.LogUtils.getLogger().warn("Actor capacity reconciliation retained quarantine: actor={}", actor.id(), invalid);
            return;
        }
        if (owner.reconcileCapacityFault(actor.id())) {
            dirty = true;
            com.mojang.logging.LogUtils.getLogger().info("Actor capacity fault reconciled after current-instance validation: actor={}, evidence={}",
                    actor.id(), owner.capacityRecoveries().get(actor.id()));
        }
    }
    private void enrollDefinition(LiveActorContext actor) {
        if (!ActorDefinitions.hasProviders() || owner.state(actor.id()).revision() != 0 || ActorDefinitions.capture(actor).intrinsicGrants().isEmpty()) return;
        var operation = UUID.nameUUIDFromBytes(("actor-enrollment:" + actor.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        command(actor, new ActorStates.Command(operation, actor.id(), 0, List.of()));
    }
    private LivingEntity find(UUID actor) {
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(actor);
            if (entity instanceof LivingEntity living && living.isAlive()) return living;
        }
        return null;
    }
    /** Called with confirmed, server-owned event identity, never a client-supplied trigger. */
    void deferEvent(LiveActorContext actor, ActorActivations.Event event) {
        thread(); actor.verifyCurrent(); available();
        if (draining) throw new EffectWave.Fault("EFFECT_REENTRANT_DISPATCH");
        if (!actor.id().equals(event.actor())) throw new IllegalArgumentException("activation actor");
        if (owner.faultReason(actor.id()) != null) throw new IllegalStateException("actor rules quarantined");
        enrollDefinition(actor);
        owner.registerReactions(ActorActivations.root(event), actor.id(), List.of(event)); dirty = true;
    }
    /** Delivers a confirmed event now; deferEvent is the same durable inbox for a later lifecycle boundary. */
    void dispatch(LiveActorContext actor, ActorActivations.Event event) {
        thread(); actor.verifyCurrent();
        if (recoveryFailure != null || AbilityAdapterRegistry.activations().isEmpty()) return;
        if (owner.faultReason(actor.id()) != null) return;
        try {
            deferEvent(actor, event);
            beginReactions(ActorActivations.root(event));
            enqueue(event, 0);
            drain(actor);
        }
        catch (RuntimeException failure) {
            if (owner.state(actor.id()).revision() == 0) throw failure;
            reactionFault(actor.id(), failure);
        }
    }
    private void beginReactions(UUID root) {
        reactionQueue.clear(); reactionRoot = root; reactionDepth = 0;
        reactionEvents = 0; reactionMutations = 0; reactionOrder = 0;
        reactionDelivery = null;
    }
    private void enqueue(ActorActivations.Event event, int depth) {
        if (depth > EffectWave.MAX_DEPTH) throw new EffectWave.Fault("EFFECT_DEPTH_LIMIT");
        if (reactionEvents + reactionQueue.size() >= EffectWave.MAX_EVENTS) throw new EffectWave.Fault("EFFECT_EVENT_LIMIT");
        reactionQueue.addLast(new Queued(event, depth, reactionOrder++));
    }
    private void enqueueTransitions(UUID actor, UUID cause, List<EffectTransition> transitions, int depth) {
        for (var transition : transitions) for (var kind : transition.events().stream().sorted().toList()) {
            var id = UUID.nameUUIDFromBytes((reactionRoot + ":" + actor + ":" + transition.instance() + ":"
                    + transition.afterRevision() + ":" + kind).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            enqueue(new ActorActivations.Event(id, cause, actor, null, kind, null, 0, transition), depth);
        }
    }
    private void reactionFault(UUID actor, RuntimeException failure) {
        if (owner.faultReason(actor) == null)
            com.mojang.logging.LogUtils.getLogger().error("Actor reaction failed: actor={}, root={}", actor, reactionRoot, failure);
        owner.fault(actor, failure instanceof EffectWave.Fault fault ? fault.code() : "ACTIVATION_FAILED:" + failure.getClass().getSimpleName());
        if (reactionDelivery != null && reactionDelivery.status() == ActorStates.DeliveryStatus.PENDING)
            owner.finishReactions(reactionDelivery.root(), actor, ActorStates.DeliveryStatus.FAULTED);
        reactionDelivery = null;
        dirty = true; reactionQueue.clear(); draining = false; reactionRoot = null;
    }
    private void resumeReactions(LiveActorContext actor) {
        if (draining || owner.faultReason(actor.id()) != null) return;
        for (var pending : owner.pendingReactions(actor.id())) {
            beginReactions(pending.root());
            try {
                for (var event : pending.events()) enqueue(event, 0);
                drain(actor);
            } catch (RuntimeException failure) { reactionFault(actor.id(), failure); break; }
        }
    }
    private void drain(LiveActorContext actor) {
        if (reactionQueue.isEmpty()) return;
        reactionDelivery = owner.registerReactions(reactionRoot, actor.id(), reactionQueue.stream().map(Queued::event).toList());
        dirty = true;
        if (reactionDelivery.status() != ActorStates.DeliveryStatus.PENDING) {
            reactionQueue.clear(); reactionRoot = null; reactionDelivery = null; return;
        }
        draining = true;
        try {
            var nativeSources = reactionQueue.isEmpty() ? List.<AbilityBinding>of() : MinecraftSnapshotCapture.captureReactionSources(actor);
            while (!reactionQueue.isEmpty()) {
                var wave = List.copyOf(reactionQueue); reactionQueue.clear();
                reactionEvents += wave.size(); reactionDepth = wave.get(0).depth();
                String identity = reactionRoot + ":wave:" + actor.id() + ":" + wave.stream().map(q -> q.event().id().toString()).toList();
                var operation = UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                // A delivered root event is a server-owned identity. Replay does not resolve callbacks twice.
                var committed = owner.receipt(operation);
                if (committed != null) {
                    reactionMutations += committed.command().proposedMutations();
                    if (reactionMutations > EffectWave.MAX_MUTATIONS) throw new EffectWave.Fault("EFFECT_MUTATION_LIMIT");
                    enqueueTransitions(actor.id(), operation, committed.transitions(), reactionDepth + 1);
                    continue;
                }
                var state = owner.state(actor.id());
                var snapshot = MinecraftSnapshotCapture.captureReactionWave(actor, nativeSources);
                var inputs = new ArrayList<Map.Entry<ActorActivations.Registration, ActorActivations.Input>>();
                var orders = new ArrayList<Long>();
                for (var queued : wave) for (var binding : ActorCompilation.reactionBindings(actor.id(), actor.instance(),
                        snapshot.abilities(), queued.event(), AbilityAdapterRegistry.definitions()).stream()
                        .sorted(Comparator.comparing((AbilityBinding b) -> b.source().grant() == null ? actor.id() : b.source().grant())
                                .thenComparing(AbilityBinding::id)).toList()) {
                    var event = queued.event();
                    var registration = AbilityAdapterRegistry.activations().find(binding, event);
                    if (registration == null) continue;
                    if (inputs.size() >= EffectWave.MAX_MUTATIONS) throw new EffectWave.Fault("EFFECT_MUTATION_LIMIT");
                    var keys = new HashSet<>(registration.reads().keys()); keys.remove(RuleFacts.SOURCE_VALID);
                    var facts = MinecraftFactProviders.capture(actor, new ReadContract(keys)).merge(new FactSlice(Map.of(RuleFacts.SOURCE_VALID, true), Map.of()));
                    var other = event.other() == null ? null : find(event.other());
                    var targetFacts = other == null ? new FactSlice(Map.of(), Map.of())
                            : MinecraftSnapshotCapture.captureFacts(new LiveActorContext(other), registration.reads().target());
                    inputs.add(Map.entry(registration, new ActorActivations.Input(event, binding, facts, snapshot.facts(), targetFacts,
                            state.revision(), actor.level().dimension().identifier().toString(), snapshot.effects())));
                    orders.add(queued.order());
                }
                if (inputs.isEmpty()) continue;
                var proposals = new ArrayList<EffectWave.Proposal>();
                var invocations = new ArrayList<TriggeredExecutionRecord>();
                for (int i = 0; i < inputs.size(); i++) {
                    var input = inputs.get(i);
                    var emissions = AbilityAdapterRegistry.activations().resolve(input.getKey(), input.getValue());
                    reactionMutations = Math.addExact(reactionMutations, emissions.size());
                    if (reactionMutations > EffectWave.MAX_MUTATIONS) throw new EffectWave.Fault("EFFECT_MUTATION_LIMIT");
                    var changes = new ArrayList<ActorStates.Change>();
                    for (int output = 0; output < emissions.size(); output++) {
                        var emission = emissions.get(output);
                        if (emission instanceof ActorStates.Change change) changes.add(change);
                        else if (emission instanceof TriggeredAbilityInvocation invocation) {
                            var definition = AbilityAdapterRegistry.definitions().require(invocation.ability(), invocation.version());
                            if (definition.activation() != AbilityDefinition.Activation.TRIGGERED)
                                throw new IllegalArgumentException("emission requires a triggered definition");
                            var source = input.getValue().binding();
                            UUID id = UUID.nameUUIDFromBytes((operation + ":" + input.getValue().event().id() + ":"
                                    + source.id() + ":" + source.source().grant() + ":" + output)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            invocations.add(new TriggeredExecutionRecord(id, reactionRoot, input.getValue().event(), source,
                                    invocation, TriggeredExecutionRecord.Status.PENDING, "", null,
                                    ServerRuntime.encounters(server).encounterOf(actor.id())));
                        }
                    }
                    var grant = input.getValue().binding().source().grant();
                    proposals.add(new EffectWave.Proposal(orders.get(i), grant == null ? actor.id() : grant,
                            input.getKey().definition().id(), changes));
                }
                var prepared = EffectWave.prepare(state, proposals);
                command(actor, new ActorStates.Command(operation, actor.id(), state.revision(), prepared.changes(), reactionRoot,
                        prepared.proposedMutations() + invocations.size(), invocations));
            }
            if (reactionDelivery != null) {
                owner.finishReactions(reactionDelivery.root(), actor.id(), ActorStates.DeliveryStatus.COMPLETED);
                reactionDelivery = null; dirty = true;
            }
        } catch (RuntimeException failure) {
            reactionFault(actor.id(), failure);
            throw failure;
        } finally { draining = false; reactionRoot = null; reactionQueue.clear(); }
    }
    public void turnStarted(UUID encounter, long round, UUID actor) {
        thread();
        if (recoveryFailure != null) return;
        if (actor == null || owner.state(actor).revision() == 0 || owner.faultReason(actor) != null) return;
        try { turnStartedChecked(encounter, round, actor); }
        catch (RuntimeException failure) {
            owner.fault(actor, "TURN_START_FAILED:" + failure.getClass().getSimpleName()); dirty = true;
            com.mojang.logging.LogUtils.getLogger().error("Actor turn boundary quarantined: actor={}, encounter={}", actor, encounter, failure);
        }
    }
    private void turnStartedChecked(UUID encounter, long round, UUID actor) {
        var operation = UUID.nameUUIDFromBytes(("turn-start:" + encounter + ":" + round + ":" + actor).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (owner.receipt(operation) != null) return;
        var entity = find(actor);
        if (entity == null) return;
        var context = new LiveActorContext(entity);
        command(context, new ActorStates.Command(operation, actor, owner.state(actor).revision(),
                List.of(new ActorStates.Advance(EffectDefinition.Clock.TURN_START))));
        dispatch(context, new ActorActivations.Event(operation, operation, actor, null, ActivationSpec.Event.TURN_START,
                EffectDefinition.Clock.TURN_START, owner.state(actor).clocks().get(EffectDefinition.Clock.TURN_START)));
    }
    public void damageAttempt(LiveActorContext actor, CausalEvent event) {
        if (!AbilityAdapterRegistry.activations().hasEvent(ActivationSpec.Event.DAMAGE_ATTEMPT)) return;
        if (event.phase() != CausalEvent.Phase.ATTEMPT)
            throw new IllegalArgumentException("expected damage attempt");
        dispatch(actor, new ActorActivations.Event(event.id(), event.operation(), actor.id(), event.source(),
                ActivationSpec.Event.DAMAGE_ATTEMPT, null, 0, null, event));
        available();
        if (owner.faultReason(actor.id()) != null) throw new IllegalStateException("damage reaction quarantined");
    }
    public void observedHit(UUID operation, LivingEntity actor, LivingEntity target, boolean accepted, float loss) {
        thread();
        if (accepted && actor != null && actor.isAlive()) dispatch(new LiveActorContext(actor),
                new ActorActivations.Event(operation, operation, actor.getUUID(), target.getUUID(), ActivationSpec.Event.HIT, null, 0));
        if (loss > 0 && target.isAlive()) dispatch(new LiveActorContext(target),
                new ActorActivations.Event(operation, operation, target.getUUID(), actor == null ? null : actor.getUUID(), ActivationSpec.Event.DAMAGED, null, 0));
    }
}
