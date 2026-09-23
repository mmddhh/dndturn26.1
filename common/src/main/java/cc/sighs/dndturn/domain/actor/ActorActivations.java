package cc.sighs.dndturn.domain.actor;

import cc.sighs.dndturn.domain.encounter.operation.CausalEvent;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectSnapshot;
import cc.sighs.dndturn.domain.effect.EffectTransition;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import java.util.*;

/** Pure actor effects. Native world actions must use the separate audited executor/permit chain. */
public final class ActorActivations {
    public record Event(UUID id, UUID cause, UUID actor, UUID other, ActivationSpec.Event kind,
                        EffectDefinition.Clock clock, long sequence, EffectTransition transition,
                        CausalEvent causal) {
        public Event(UUID id, UUID cause, UUID actor, UUID other, ActivationSpec.Event kind,
                     EffectDefinition.Clock clock, long sequence, EffectTransition transition) {
            this(id, cause, actor, other, kind, clock, sequence, transition, null);
        }
        public Event(UUID id, UUID cause, UUID actor, UUID other, ActivationSpec.Event kind,
                     EffectDefinition.Clock clock, long sequence) {
            this(id, cause, actor, other, kind, clock, sequence, null);
        }
        public Event {
            Objects.requireNonNull(id); Objects.requireNonNull(actor);
            if (sequence < 0 || kind == null && clock == null) throw new IllegalArgumentException("activation event identity");
            if (transition != null && !transition.events().contains(kind))
                throw new IllegalArgumentException("effect event not present in transition");
            if (causal != null && (!id.equals(causal.id()) || !Objects.equals(cause, causal.operation())
                    || !actor.equals(causal.subject()))) throw new IllegalArgumentException("causal event identity");
            if (kind == ActivationSpec.Event.DAMAGE_ATTEMPT && (causal == null
                    || causal.phase() != CausalEvent.Phase.ATTEMPT))
                throw new IllegalArgumentException("damage attempt requires causal payload");
            if (causal != null && causal.phase() == CausalEvent.Phase.ATTEMPT
                    && kind != ActivationSpec.Event.DAMAGE_ATTEMPT)
                throw new IllegalArgumentException("attempt cannot dispatch a result activation");
        }
    }
    public record Input(Event event, AbilityBinding binding, FactSlice facts, FactSlice actorFacts, FactSlice targetFacts,
                        long actorRevision, String dimension, List<EffectSnapshot> effects) {
        public Input(Event event, AbilityBinding binding, FactSlice facts, FactSlice actorFacts, FactSlice targetFacts, long actorRevision) {
            this(event, binding, facts, actorFacts, targetFacts, actorRevision, "", List.of());
        }
        public Input { Objects.requireNonNull(dimension); effects = List.copyOf(effects); }
    }
    public static UUID root(Event event) {
        return UUID.nameUUIDFromBytes(("actor-event:" + event.id() + ":" + event.kind() + ":" + event.clock())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @FunctionalInterface public interface Rule { List<? extends RuleEmission> resolve(Input input); }
    public record Registration(AbilityDefinition definition, ActivationSpec activation, ReadContract reads, Rule rule) {
        public Registration(AbilityDefinition definition, ActivationSpec activation, Rule rule) {
            this(definition, activation, definition.reads(), rule);
        }
        public Registration {
            Objects.requireNonNull(definition); Objects.requireNonNull(activation); Objects.requireNonNull(rule);
            Objects.requireNonNull(reads);
            if (definition.activation() != activation.kind() || activation.kind() == AbilityDefinition.Activation.MANUAL
                    || activation.kind() == AbilityDefinition.Activation.CONTINUOUS
                    || definition.cost().resource() == ActionCost.Resource.MOVEMENT)
                throw new IllegalArgumentException("actor activation contract; continuous modifiers belong to effects");
        }
    }
    private final Map<String, Registration> registrations = new LinkedHashMap<>();
    private boolean frozen;
    public void register(Registration registration) {
        if (frozen || registrations.size() >= 128 || registrations.containsKey(registration.definition().id()))
            throw new IllegalStateException("activation registry closed or duplicate");
        registrations.put(registration.definition().id(), registration);
    }
    public void freeze() { frozen = true; }
    public boolean isEmpty() { return registrations.isEmpty(); }
    public boolean hasEvent(ActivationSpec.Event event) {
        return registrations.values().stream().anyMatch(value -> value.activation().event() == event);
    }
    public ReadContract reads(AbilityBinding binding) {
        var registration = registrations.get(binding.id());
        return registration != null && registration.definition().equals(binding.definition())
                ? registration.reads() : new ReadContract(Set.of());
    }
    public Registration find(AbilityBinding binding, Event event) {
        var value = registrations.get(binding.id());
        if (value == null || !value.definition().equals(binding.definition())) return null;
        var activation = value.activation();
        return activation.kind() == AbilityDefinition.Activation.TRIGGERED && activation.event() == event.kind()
                || activation.kind() == AbilityDefinition.Activation.PERIODIC && activation.clock() == event.clock()
                    && event.sequence() > 0 && event.sequence() % activation.interval() == 0 ? value : null;
    }
    public List<? extends RuleEmission> resolve(Registration registration, Input input) {
        var reads = registration.reads();
        var facts = input.facts().restrict(reads);
        var actor = input.actorFacts().restrict(reads.actor());
        var target = input.targetFacts().restrict(reads.target());
        for (var key : reads.keys()) facts.read(reads, key);
        for (var key : reads.actorKeys()) actor.read(reads.actor(), key);
        for (var key : reads.targetKeys()) target.read(reads.target(), key);
        var result = List.copyOf(registration.rule().resolve(new Input(input.event(), input.binding(), facts, actor, target,
                input.actorRevision(), input.dimension(), input.effects())));
        if (result.size() > 128 || result.stream().anyMatch(c -> c instanceof ActorStates.Advance || c instanceof ActorStates.Death))
            throw new IllegalArgumentException("activation effect bounds or lifecycle ownership");
        if (!registration.definition().cost().equals(ActionCost.FREE) && !result.isEmpty()
                && (result.size() != 1 || !(result.get(0) instanceof TriggeredAbilityInvocation invocation)
                    || !invocation.ability().equals(registration.definition().id())
                    || invocation.version() != registration.definition().version()))
            throw new IllegalArgumentException("paid activation must enter its own ability admission before any mutation");
        return result;
    }
}
