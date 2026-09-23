package cc.sighs.dndturn.domain.actor;

import cc.sighs.dndturn.domain.encounter.operation.Intervention;

import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EffectTransition;
import cc.sighs.dndturn.domain.effect.EffectWave;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.domain.encounter.operation.WorldOutcomeObservation;
import cc.sighs.dndturn.domain.fact.ResourceKey;
import java.util.*;

/** Single actor writer. The target confines this owner to the server thread. */
public final class ActorStates {
    public record State(long revision, ActorPersistentState persistent, ActorRuntimeState runtime,
                        Map<EffectDefinition.Clock, Long> clocks) {
        public State(long revision, ActorPersistentState persistent, ActorRuntimeState runtime) {
            this(revision, persistent, runtime, Map.of());
        }
        public State {
            if (revision < 0) throw new IllegalArgumentException("actor revision");
            Objects.requireNonNull(persistent); Objects.requireNonNull(runtime);
            if (runtime.effects().values().stream().anyMatch(effect -> effect.revision() > revision))
                throw new IllegalArgumentException("effect revision ahead of actor");
            clocks = Map.copyOf(clocks);
            if (clocks.values().stream().anyMatch(n -> n < 0)) throw new IllegalArgumentException("actor clock");
        }
        public static State empty() { return new State(0, ActorPersistentState.empty(), ActorRuntimeState.empty()); }
    }
    public sealed interface Change extends RuleEmission permits Learn, Forget, Prepare, Resource, Apply, Remove, AddStacks, ConsumeStacks, RefreshDuration, Advance, Death {}
    public record AddStacks(UUID effect, int count) implements Change {
        public AddStacks { Objects.requireNonNull(effect); if (count < 1 || count > 64) throw new IllegalArgumentException("stack delta"); }
    }
    public record ConsumeStacks(UUID effect, int count) implements Change {
        public ConsumeStacks { Objects.requireNonNull(effect); if (count < 1 || count > 64) throw new IllegalArgumentException("stack delta"); }
    }
    public record RefreshDuration(UUID effect, long duration) implements Change {
        public RefreshDuration { Objects.requireNonNull(effect); if (duration < 1) throw new IllegalArgumentException("effect duration"); }
    }
    public record Learn(ActorPersistentState.Learned grant) implements Change { public Learn { Objects.requireNonNull(grant); } }
    public record Forget(UUID grant) implements Change { public Forget { Objects.requireNonNull(grant); } }
    public record Prepare(Set<UUID> grants) implements Change { public Prepare { grants = Set.copyOf(grants); } }
    public record Resource(ResourceKey key, double delta, boolean create) implements Change {
        public Resource { Objects.requireNonNull(key); if (!Double.isFinite(delta)) throw new IllegalArgumentException("resource delta"); }
    }
    public record Apply(EffectInstance effect) implements Change { public Apply { Objects.requireNonNull(effect); } }
    public record Remove(UUID effect) implements Change { public Remove { Objects.requireNonNull(effect); } }
    public record Advance(EffectDefinition.Clock clock) implements Change { public Advance { Objects.requireNonNull(clock); } }
    public record Death() implements Change {}
    public record Command(UUID operation, UUID actor, long expectedRevision, List<Change> changes, UUID cause,
                          int proposedMutations, List<TriggeredExecutionRecord> invocations) {
        public Command(UUID operation, UUID actor, long expectedRevision, List<Change> changes, UUID cause, int proposedMutations) {
            this(operation, actor, expectedRevision, changes, cause, proposedMutations, List.of());
        }
        public Command(UUID operation, UUID actor, long expectedRevision, List<Change> changes, UUID cause) {
            this(operation, actor, expectedRevision, changes, cause, changes.size());
        }
        public Command(UUID operation, UUID actor, long expectedRevision, List<Change> changes) {
            this(operation, actor, expectedRevision, changes, null);
        }
        public Command {
            Objects.requireNonNull(operation); Objects.requireNonNull(actor); changes = List.copyOf(changes);
            invocations = List.copyOf(invocations);
            if (invocations.size() > 128 || invocations.stream().anyMatch(i -> !i.event().actor().equals(actor)
                    || i.status() != TriggeredExecutionRecord.Status.PENDING || !Objects.equals(i.root(), cause))
                    || invocations.stream().map(TriggeredExecutionRecord::operation).distinct().count() != invocations.size())
                throw new IllegalArgumentException("actor emission bounds or provenance");
            if (expectedRevision < 0 || proposedMutations < changes.size() + invocations.size() || proposedMutations > EffectWave.MAX_MUTATIONS)
                throw new IllegalArgumentException("actor command bounds");
        }
    }
    public record Receipt(Command command, State result, List<EffectTransition> transitions) {
        public Receipt {
            Objects.requireNonNull(command); Objects.requireNonNull(result); transitions = List.copyOf(transitions);
            if (result.revision() != Math.addExact(command.expectedRevision(), 1) || transitions.size() > 512
                    || transitions.stream().map(EffectTransition::instance).distinct().count() != transitions.size())
                throw new IllegalArgumentException("actor receipt revisions or transition bound");
            for (var transition : transitions)
                if (transition.beforeRevision() > command.expectedRevision() || transition.afterRevision() > result.revision()
                        || !Objects.equals(transition.after(), result.runtime().effects().get(transition.instance())))
                    throw new IllegalArgumentException("actor receipt transition mismatch");
        }
    }
    private final Map<UUID, State> actors = new LinkedHashMap<>();
    private static final State EMPTY = State.empty();
    private final Map<UUID, Receipt> receipts = new LinkedHashMap<>();
    private final Map<UUID, String> faults = new LinkedHashMap<>();
    private final Map<UUID, TriggeredExecutionRecord> invocations = new LinkedHashMap<>();
    // Derived reference index; receipts/outbox remain the owners of this evidence.
    private final Set<UUID> retainedReactionRoots = new HashSet<>();
    public List<TriggeredExecutionRecord> invocations() { return List.copyOf(invocations.values()); }
    public TriggeredExecutionRecord invocation(UUID operation) { return invocations.get(operation); }
    public void intervention(UUID operation, Intervention decision) {
        invocations.put(operation, Objects.requireNonNull(invocations.get(operation), "unknown invocation").intervene(decision));
    }
    public void observeInvocation(UUID operation, WorldOutcomeObservation observation) {
        var previous = Objects.requireNonNull(invocations.get(operation), "unknown effect invocation");
        invocations.put(operation, previous.observe(observation));
    }
    public void invocationStatus(UUID operation, TriggeredExecutionRecord.Status status, String reason) {
        var previous = Objects.requireNonNull(invocations.get(operation), "unknown effect invocation");
        invocations.put(operation, previous.withStatus(status, reason));
    }
    public void restoreInvocations(List<TriggeredExecutionRecord> saved) {
        if (!invocations.isEmpty() || saved.size() > 16384) throw new IllegalArgumentException("invocation restore bounds");
        var checked = new LinkedHashMap<UUID, TriggeredExecutionRecord>();
        var expected = new HashMap<UUID, TriggeredExecutionRecord>();
        for (var receipt : receipts.values()) for (var invocation : receipt.command().invocations())
            if (expected.putIfAbsent(invocation.operation(), invocation) != null)
                throw new IllegalArgumentException("duplicate persisted emission");
        for (var invocation : saved) {
            var original = expected.get(invocation.operation());
            if (original != null && invocation.observation() != null) {
                if (invocation.status() == TriggeredExecutionRecord.Status.PENDING) throw new IllegalArgumentException("unstarted world observation");
                original = original.withStatus(TriggeredExecutionRecord.Status.STARTED, "").observe(invocation.observation());
            }
            if (original != null && invocation.intervention() != null)
                original = original.withStatus(TriggeredExecutionRecord.Status.STARTED, "").intervene(invocation.intervention());
            if (!actors.containsKey(invocation.event().actor()) || checked.putIfAbsent(invocation.operation(), invocation) != null
                    || original == null || !original.withStatus(invocation.status(), invocation.reason()).equals(invocation))
                throw new IllegalArgumentException("invocation receipt evidence missing");
        }
        if (!checked.keySet().equals(expected.keySet())) throw new IllegalArgumentException("persisted emission lost from outbox");
        invocations.putAll(checked);
        checked.values().forEach(i -> retainedReactionRoots.add(i.root()));
    }
    public enum DeliveryStatus { PENDING, COMPLETED, FAULTED }
    public record ReactionDelivery(UUID root, UUID actor, List<ActorActivations.Event> events, DeliveryStatus status) {
        public ReactionDelivery {
            Objects.requireNonNull(root); Objects.requireNonNull(actor); Objects.requireNonNull(status);
            events = List.copyOf(events);
            if (events.isEmpty() || events.size() > EffectWave.MAX_EVENTS
                    || events.stream().anyMatch(event -> !actor.equals(event.actor()))
                    || events.stream().map(ActorActivations.Event::id).distinct().count() != events.size())
                throw new IllegalArgumentException("reaction delivery payload");
        }
        public UUID key() {
            return UUID.nameUUIDFromBytes(("actor-reaction:" + actor + ":" + root).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private final Map<UUID, ReactionDelivery> deliveries = new LinkedHashMap<>();
    /** Exact ranges of completed, canonical simulation events with no rule/world receipts. */
    public record SimulationRange(long first, long last) {
        public SimulationRange { if (first < 1 || last < first) throw new IllegalArgumentException("simulation archive range"); }
        boolean contains(long value) { return first <= value && value <= last; }
    }
    private final Map<UUID, List<SimulationRange>> simulationArchive = new LinkedHashMap<>();
    public Map<UUID, List<SimulationRange>> simulationArchive() { return Map.copyOf(simulationArchive); }
    public void restoreSimulationArchive(Map<UUID, List<SimulationRange>> saved) {
        if (!simulationArchive.isEmpty() || !actors.keySet().containsAll(saved.keySet()))
            throw new IllegalArgumentException("simulation archive owner");
        var checked = new LinkedHashMap<UUID, List<SimulationRange>>();
        saved.forEach((actor, ranges) -> {
            if (ranges.isEmpty() || ranges.size() > 128) throw new IllegalArgumentException("simulation archive bounds");
            long last = 0;
            for (var range : ranges) {
                if (range.first() <= last || range.last() > state(actor).clocks().getOrDefault(EffectDefinition.Clock.SIMULATION_STEP, 0L))
                    throw new IllegalArgumentException("simulation archive clock");
                last = range.last();
            }
            checked.put(actor, List.copyOf(ranges));
        });
        for (var delivery : deliveries.values()) for (var event : delivery.events())
            if (event.clock() == EffectDefinition.Clock.SIMULATION_STEP
                    && checked.getOrDefault(delivery.actor(), List.of()).stream().anyMatch(r -> r.contains(event.sequence())))
                throw new IllegalArgumentException("simulation archive overlaps live delivery");
        simulationArchive.putAll(checked);
    }
    private static boolean canonicalSimulation(ReactionDelivery value) {
        if (value.events().size() != 1) return false;
        var event = value.events().get(0);
        var expected = UUID.nameUUIDFromBytes((value.actor() + ":SIMULATION_STEP:" + event.sequence())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return event.clock() == EffectDefinition.Clock.SIMULATION_STEP && event.sequence() > 0
                && event.kind() == null && event.cause() == null && event.other() == null && event.transition() == null
                && event.id().equals(expected) && value.root().equals(expected);
    }
    private boolean archiveSimulation(ReactionDelivery value) {
        if (value.status() != DeliveryStatus.COMPLETED || !canonicalSimulation(value)
                || retainedReactionRoots.contains(value.root())) return false;
        long sequence = value.events().get(0).sequence();
        if (sequence > state(value.actor()).clocks().getOrDefault(EffectDefinition.Clock.SIMULATION_STEP, 0L)) return false;
        var ranges = new ArrayList<>(simulationArchive.getOrDefault(value.actor(), List.of()));
        ranges.add(new SimulationRange(sequence, sequence));
        ranges.sort(Comparator.comparingLong(SimulationRange::first));
        var merged = new ArrayList<SimulationRange>();
        for (var range : ranges) {
            if (!merged.isEmpty() && range.first() - 1 <= merged.get(merged.size() - 1).last()) {
                var previous = merged.remove(merged.size() - 1);
                merged.add(new SimulationRange(previous.first(), Math.max(previous.last(), range.last())));
            } else merged.add(range);
        }
        if (merged.size() > 128) return false; // Retain exact evidence rather than bridge a gap.
        simulationArchive.put(value.actor(), List.copyOf(merged));
        return true;
    }
    public int compactSimulationHistory() {
        int before = deliveries.size();
        deliveries.values().removeIf(this::archiveSimulation);
        return before - deliveries.size();
    }
    /** Evidence for a narrowly scoped legacy repair; the original fault is never lost. */
    public record CapacityRecovery(String originalFault, long actorRevision, int archivedDeliveries) {
        public CapacityRecovery {
            if (!"ACTIVATION_FAILED:IllegalStateException".equals(originalFault) || actorRevision < 0
                    || archivedDeliveries < 1 || archivedDeliveries > 16384)
                throw new IllegalArgumentException("capacity recovery evidence");
        }
    }
    private final Map<UUID, CapacityRecovery> capacityCandidates = new LinkedHashMap<>();
    private final Map<UUID, CapacityRecovery> capacityRecoveries = new LinkedHashMap<>();
    public Map<UUID, CapacityRecovery> capacityCandidates() { return Map.copyOf(capacityCandidates); }
    public Map<UUID, CapacityRecovery> capacityRecoveries() { return Map.copyOf(capacityRecoveries); }
    public void restoreCapacityRecoveries(Map<UUID, CapacityRecovery> candidates, Map<UUID, CapacityRecovery> recovered) {
        if (!capacityCandidates.isEmpty() || !capacityRecoveries.isEmpty()
                || !actors.keySet().containsAll(candidates.keySet()) || !actors.keySet().containsAll(recovered.keySet())
                || !Collections.disjoint(candidates.keySet(), recovered.keySet()))
            throw new IllegalArgumentException("capacity recovery owner");
        candidates.forEach((actor, evidence) -> {
            if (!evidence.originalFault().equals(faults.get(actor)) || evidence.actorRevision() != state(actor).revision())
                throw new IllegalArgumentException("capacity recovery fault evidence");
        });
        recovered.forEach((actor, evidence) -> {
            if (evidence.actorRevision() > state(actor).revision()) throw new IllegalArgumentException("capacity recovery revision");
        });
        capacityCandidates.putAll(candidates); capacityRecoveries.putAll(recovered);
    }
    private boolean inertRecoveryCandidate(UUID actor) {
        var state = state(actor);
        return state.persistent().equals(ActorPersistentState.empty()) && state.runtime().effects().isEmpty()
                && deliveries.values().stream().noneMatch(d -> d.actor().equals(actor) && d.status() != DeliveryStatus.COMPLETED)
                && invocations.values().stream().noneMatch(i -> i.event().actor().equals(actor))
                && receipts.values().stream().filter(r -> r.command().actor().equals(actor))
                    .allMatch(r -> r.command().changes().stream().allMatch(c -> c instanceof Advance)
                            && r.transitions().isEmpty() && r.command().invocations().isEmpty());
    }
    /** Run only after all checkpoint evidence is installed. No old event is executed. */
    public int prepareCapacityRecovery() {
        boolean saturated = deliveries.size() == 16384
                && deliveries.values().stream().allMatch(d -> d.status() == DeliveryStatus.COMPLETED);
        int archived = compactSimulationHistory();
        if (saturated && archived > 0) faults.forEach((actor, reason) -> {
            if (reason.equals("ACTIVATION_FAILED:IllegalStateException") && inertRecoveryCandidate(actor)
                    && !capacityRecoveries.containsKey(actor))
                capacityCandidates.putIfAbsent(actor, new CapacityRecovery(reason, state(actor).revision(), archived));
        });
        return archived;
    }
    /** Platform must first validate current instance and compile the unchanged candidate state. */
    public boolean reconcileCapacityFault(UUID actor) {
        var evidence = capacityCandidates.get(actor);
        if (evidence == null || !evidence.originalFault().equals(faults.get(actor))
                || evidence.actorRevision() != state(actor).revision() || !inertRecoveryCandidate(actor)) return false;
        capacityRecoveries.put(actor, evidence);
        capacityCandidates.remove(actor);
        faults.remove(actor);
        return true;
    }
    public List<ReactionDelivery> deliveries() { return List.copyOf(deliveries.values()); }
    public List<ReactionDelivery> pendingReactions(UUID actor) {
        return deliveries.values().stream().filter(value -> value.actor().equals(actor) && value.status() == DeliveryStatus.PENDING).toList();
    }
    public ReactionDelivery registerReactions(UUID root, UUID actor, List<ActorActivations.Event> events) {
        var incoming = new ReactionDelivery(root, actor, events, DeliveryStatus.PENDING);
        for (var event : events) if (event.clock() == EffectDefinition.Clock.SIMULATION_STEP
                && simulationArchive.getOrDefault(actor, List.of()).stream().anyMatch(r -> r.contains(event.sequence()))) {
            if (!canonicalSimulation(incoming)) throw new IllegalArgumentException("archived simulation payload reused");
            return new ReactionDelivery(root, actor, events, DeliveryStatus.COMPLETED);
        }
        var previous = deliveries.get(incoming.key());
        if (previous != null) {
            if (!previous.root().equals(root) || !previous.actor().equals(actor) || !previous.events().equals(events))
                throw new IllegalArgumentException("reaction root ID reused with different payload");
            return previous;
        }
        if (deliveries.size() >= 16384 || !actors.containsKey(actor) && actors.size() >= 4096)
            throw new IllegalStateException("reaction delivery capacity; history cannot be evicted");
        actors.putIfAbsent(actor, EMPTY);
        deliveries.put(incoming.key(), incoming);
        return incoming;
    }
    public void finishReactions(UUID root, UUID actor, DeliveryStatus status) {
        if (status == DeliveryStatus.PENDING) throw new IllegalArgumentException("reaction delivery is not terminal");
        UUID key = UUID.nameUUIDFromBytes(("actor-reaction:" + actor + ":" + root).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var previous = Objects.requireNonNull(deliveries.get(key), "unregistered reaction root");
        if (previous.status() != DeliveryStatus.PENDING) {
            if (previous.status() != status) throw new IllegalStateException("reaction delivery terminal conflict");
            return;
        }
        var terminal = new ReactionDelivery(root, actor, previous.events(), status);
        if (archiveSimulation(terminal)) deliveries.remove(key);
        else deliveries.put(key, terminal);
    }
    public void restoreDeliveries(List<ReactionDelivery> saved) {
        if (!deliveries.isEmpty() || saved.size() > 16384) throw new IllegalArgumentException("reaction delivery restore bound");
        var checked = new LinkedHashMap<UUID, ReactionDelivery>();
        for (var value : saved) if (!actors.containsKey(value.actor()) || checked.putIfAbsent(value.key(), value) != null)
            throw new IllegalArgumentException("reaction delivery owner or duplicate");
        deliveries.putAll(checked);
    }
    public Map<UUID, String> faults() { return Map.copyOf(faults); }
    public void fault(UUID actor, String reason) {
        if (!actors.containsKey(actor) || reason == null || reason.length() > 256) throw new IllegalArgumentException("actor fault evidence");
        faults.putIfAbsent(actor, reason);
    }
    public void restoreFaults(Map<UUID, String> saved) {
        if (!actors.keySet().containsAll(saved.keySet()) || saved.values().stream().anyMatch(v -> v == null || v.length() > 256))
            throw new IllegalArgumentException("actor fault checkpoint");
        faults.putAll(saved);
    }
    public State state(UUID actor) { return actors.getOrDefault(Objects.requireNonNull(actor), EMPTY); }
    public String faultReason(UUID actor) { return faults.get(actor); }
    public Map<UUID, State> snapshot() { return Map.copyOf(actors); }
    public List<Receipt> receipts() { return List.copyOf(receipts.values()); }
    public Receipt receipt(UUID operation) { return receipts.get(operation); }
    public void restore(Map<UUID, State> states, List<Receipt> history) {
        var checked = Map.copyOf(states);
        var ledger = new LinkedHashMap<UUID, Receipt>();
        if (!actors.isEmpty() || !receipts.isEmpty() || checked.size() > 4096 || history.size() > 16384)
            throw new IllegalArgumentException("actor restore bounds or already installed");
        for (var receipt : history) {
            if (ledger.putIfAbsent(receipt.command().operation(), receipt) != null
                    || !checked.containsKey(receipt.command().actor())
                    || checked.get(receipt.command().actor()).revision() < receipt.result().revision())
                throw new IllegalArgumentException("actor receipt mismatch");
        }
        actors.putAll(checked); receipts.putAll(ledger);
        ledger.values().forEach(r -> { if (r.command().cause() != null) retainedReactionRoots.add(r.command().cause()); });
    }
    public State apply(Command command) {
        var previous = receipts.get(command.operation());
        if (previous != null) {
            if (!previous.command().equals(command)) throw new IllegalArgumentException("actor operation ID reused");
            return previous.result();
        }
        if (faults.containsKey(command.actor())) throw new IllegalStateException("actor quarantined: " + faults.get(command.actor()));
        if (receipts.size() >= 16384 || !actors.containsKey(command.actor()) && actors.size() >= 4096)
            throw new IllegalStateException("actor ledger capacity; receipts cannot be silently evicted");
        var before = state(command.actor());
        if (before.revision() != command.expectedRevision()) throw new IllegalStateException("stale actor command");
        var reduction = preview(before, command.changes());
        if (invocations.size() + command.invocations().size() > 16384
                || command.invocations().stream().anyMatch(i -> invocations.containsKey(i.operation())))
            throw new IllegalStateException("invocation capacity or duplicate emission");
        State after = reduction.state();
        actors.put(command.actor(), after);
        receipts.put(command.operation(), new Receipt(command, after, reduction.transitions()));
        command.invocations().forEach(i -> invocations.put(i.operation(), i));
        if (command.cause() != null) retainedReactionRoots.add(command.cause());
        command.invocations().forEach(i -> retainedReactionRoots.add(i.root()));
        return after;
    }
    /** Lifecycle clocks are delivered exactly once by their owner, not retriable network commands. */
    public Reduction lifecycle(UUID actor, Change change) {
        if (!(change instanceof Advance || change instanceof Death)) throw new IllegalArgumentException("not a lifecycle event");
        var before = state(actor);
        if (faults.containsKey(actor) || !actors.containsKey(actor)) return new Reduction(before, List.of());
        var reduction = preview(before, List.of(change));
        var after = reduction.state();
        if (!after.runtime().equals(before.runtime()) || !after.clocks().equals(before.clocks())) actors.put(actor, after);
        return reduction;
    }
    /** Validate detached candidates before either state or receipts change. */
    public static State reduce(State before, List<Change> changes) {
        return reduce(before, changes, new HashSet<>());
    }
    public record Reduction(State state, List<EffectTransition> transitions) {
        public Reduction { Objects.requireNonNull(state); transitions = List.copyOf(transitions); }
    }
    public static Reduction preview(State before, List<Change> changes) {
        var expired = new HashSet<UUID>();
        var after = reduce(before, changes, expired);
        return new Reduction(after, EffectTransition.between(before, after, expired));
    }
    private static State reduce(State before, List<Change> changes, Set<UUID> expired) {
        if (changes.size() > EffectWave.MAX_MUTATIONS) throw new IllegalArgumentException("actor mutation bound");
        var grants = new LinkedHashMap<>(before.persistent().grants());
        var prepared = new HashSet<>(before.persistent().prepared());
        var resources = new LinkedHashMap<>(before.persistent().resources());
        var effects = new LinkedHashMap<>(before.runtime().effects());
        var clocks = new HashMap<>(before.clocks());
        for (var change : changes) {
            if (change instanceof Learn learn) {
                if (learn.grant().revision() != before.revision() + 1) throw new IllegalArgumentException("grant acquisition revision");
                if (grants.putIfAbsent(learn.grant().id(), learn.grant()) != null) throw new IllegalArgumentException("duplicate grant");
            } else if (change instanceof Forget forget) {
                if (grants.remove(forget.grant()) == null) throw new IllegalArgumentException("unknown grant");
                prepared.remove(forget.grant());
            } else if (change instanceof Prepare prepare) {
                for (var id : grants.keySet()) if (prepared.contains(id) != prepare.grants().contains(id)) {
                    var old = grants.get(id);
                    grants.put(id, new ActorPersistentState.Learned(id, old.ability(), old.version(), old.origin(),
                            old.requiresPreparation(), Math.addExact(before.revision(), 1)));
                }
                prepared.clear(); prepared.addAll(prepare.grants());
            } else if (change instanceof Resource resource) {
                if (resource.create() == resources.containsKey(resource.key().id())) throw new IllegalArgumentException("resource ownership");
                double value = resources.getOrDefault(resource.key().id(), 0.0) + resource.delta();
                if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("resource unavailable");
                resources.put(resource.key().id(), value);
            } else if (change instanceof Apply apply) {
                var incoming = apply.effect();
                var identity = effects.get(incoming.id());
                if (identity != null && !identity.key().equals(incoming.key()))
                    throw new IllegalArgumentException("effect identity reused");
                var key = incoming.key();
                var old = effects.values().stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);
                if (old == null && (incoming.revision() != Math.addExact(before.revision(), 1)
                        || incoming.grantRevision() != incoming.revision()))
                    throw new IllegalArgumentException("effect acquisition revision");
                if (old != null) {
                    if (!old.definition().equals(incoming.definition())) throw new IllegalArgumentException("effect identity reused");
                    var definition = old.definition();
                    if (definition.stacking() == EffectDefinition.Stacking.REJECT) throw new IllegalArgumentException("effect already applied");
                    if (definition.stacking() != EffectDefinition.Stacking.REPLACE && old.rank() != incoming.rank())
                        throw new IllegalArgumentException("effect rank conflict");
                    int stacks = definition.stacking() == EffectDefinition.Stacking.STACK
                            ? boundedStacks(definition, Math.addExact(old.stacks(), incoming.stacks()))
                            : definition.stacking() == EffectDefinition.Stacking.REFRESH ? old.stacks() : incoming.stacks();
                    incoming = new EffectInstance(old.id(), incoming.sourceOperation(), definition, stacks,
                            refreshed(definition, old.remaining(), incoming.remaining()),
                            Math.addExact(before.revision(), 1), incoming.rank(), incoming.sourceActor(), incoming.sourceAbility(),
                            stacks == old.stacks() && incoming.rank() == old.rank()
                                    && incoming.sourceOperation().equals(old.sourceOperation())
                                    && Objects.equals(incoming.sourceActor(), old.sourceActor())
                                    && Objects.equals(incoming.sourceAbility(), old.sourceAbility())
                                    ? old.grantRevision() : Math.addExact(before.revision(), 1));
                }
                effects.put(incoming.id(), incoming);
                expired.remove(incoming.id());
            } else if (change instanceof AddStacks add) {
                var old = requireEffect(effects, add.effect());
                if (old.definition().stacking() != EffectDefinition.Stacking.STACK)
                    throw new IllegalArgumentException("effect does not support stacking");
                effects.put(old.id(), old.withValues(boundedStacks(old.definition(), Math.addExact(old.stacks(), add.count())),
                        old.remaining(), Math.addExact(before.revision(), 1)));
            } else if (change instanceof ConsumeStacks consume) {
                var old = requireEffect(effects, consume.effect());
                if (consume.count() > old.stacks()) throw new IllegalArgumentException("insufficient effect stacks");
                if (consume.count() == old.stacks()) effects.remove(old.id());
                else effects.put(old.id(), old.withValues(old.stacks() - consume.count(), old.remaining(), Math.addExact(before.revision(), 1)));
            } else if (change instanceof RefreshDuration refresh) {
                var old = requireEffect(effects, refresh.effect());
                long remaining = refreshed(old.definition(), old.remaining(), refresh.duration());
                if (remaining != old.remaining())
                    effects.put(old.id(), old.withValues(old.stacks(), remaining, Math.addExact(before.revision(), 1)));
            } else if (change instanceof Remove remove) {
                if (effects.remove(remove.effect()) == null) throw new IllegalArgumentException("unknown effect");
            } else if (change instanceof Advance advance) {
                if (advance.clock() == EffectDefinition.Clock.EXPLICIT) throw new IllegalArgumentException("explicit effects require removal");
                clocks.put(advance.clock(), Math.addExact(clocks.getOrDefault(advance.clock(), 0L), 1));
                for (var entry : List.copyOf(effects.values())) if (entry.definition().clock() == advance.clock()) {
                    if (entry.remaining() == 1) { effects.remove(entry.id()); expired.add(entry.id()); }
                    else effects.put(entry.id(), entry.withValues(entry.stacks(), entry.remaining() - 1, Math.addExact(before.revision(), 1)));
                }
            } else if (change instanceof Death) effects.values().removeIf(c -> !c.definition().surviveDeath());
            else throw new IllegalArgumentException("unknown actor change");
        }
        return new State(Math.addExact(before.revision(), 1), new ActorPersistentState(grants, prepared, resources), new ActorRuntimeState(effects), clocks);
    }
    private static EffectInstance requireEffect(Map<UUID, EffectInstance> effects, UUID id) {
        var value = effects.get(id);
        if (value == null) throw new IllegalArgumentException("unknown effect");
        return value;
    }
    private static int boundedStacks(EffectDefinition definition, int count) {
        if (count > definition.maxStacks() && definition.overflow() == EffectDefinition.Overflow.REJECT)
            throw new IllegalArgumentException("effect stack overflow");
        return Math.min(count, definition.maxStacks());
    }
    private static long refreshed(EffectDefinition definition, long old, long incoming) {
        return switch (definition.refresh()) {
            case KEEP -> old;
            case REPLACE -> incoming;
            case MAXIMUM -> Math.max(old, incoming);
        };
    }
}
