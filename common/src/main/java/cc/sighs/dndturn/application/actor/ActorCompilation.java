package cc.sighs.dndturn.application.actor;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.ability.AbilityGrant;
import cc.sighs.dndturn.domain.ability.AbilityRegistry;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.ActorPersistentState;
import cc.sighs.dndturn.domain.actor.ActorRuntimeState;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.ResourceKey;
import java.util.*;

/** Pure actor-owned contributions. No native calls or mutable snapshots. */
public final class ActorCompilation {
    private ActorCompilation() {}
    public static FactSlice resources(ActorStates.State state, Set<FactKey<?>> requested) {
        var values = new LinkedHashMap<FactKey<?>, Object>();
        state.persistent().resources().forEach((id, amount) -> {
            var key = new ResourceKey(id).fact();
            if (requested.contains(key)) values.put(key, amount);
        });
        return new FactSlice(values, Map.of());
    }
    public static List<AbilityBinding> bindings(UUID actor, UUID instance, ActorStates.State state, AbilityRegistry registry) {
        var result = new ArrayList<AbilityBinding>();
        state.persistent().grants().values().stream().sorted(Comparator.comparing(ActorPersistentState.Learned::id)).forEach(grant -> {
            if (!grant.requiresPreparation() || state.persistent().prepared().contains(grant.id())) {
                var definition = registry.find(grant.ability(), grant.version());
                if (definition != null) result.add(new AbilityBinding(definition, new AbilityGrant(actor,
                        AbilityGrant.Origin.LEARNED, AbilityGrant.Owner.ACTOR_PERSISTENT,
                        GrantEvidence.owned(GrantEvidence.Kind.PERSISTENT, "dndturn:actor_persistent", 1, actor, instance,
                                grant.id(), grant.revision(), null))));
            }
        });
        state.runtime().effects().values().stream().sorted(Comparator.comparing(EffectInstance::id)).forEach(effect -> {
            for (var grant : effect.definition().grants()) {
                if (effect.stacks() < grant.minimumStacks() || effect.rank() < grant.minimumRank()) continue;
                var definition = registry.find(grant.ability(), grant.version());
                if (definition != null) result.add(new AbilityBinding(definition, new AbilityGrant(actor,
                        AbilityGrant.Origin.EFFECT, AbilityGrant.Owner.EFFECT_INSTANCE,
                        GrantEvidence.owned(GrantEvidence.Kind.EFFECT, effect.definition().id(), effect.definition().version(),
                                actor, instance, effect.id(), effect.grantRevision(), null))));
            }
        });
        return List.copyOf(result);
    }
    /** The changed effect observes its lifecycle at the event boundary, even after its grant disappears.
     * Other sources observe the frozen current wave. This is reaction evidence, never a world permit. */
    public static List<AbilityBinding> reactionBindings(UUID actor, UUID instance, List<AbilityBinding> current,
                                                       ActorActivations.Event event, AbilityRegistry registry) {
        var transition = event.transition();
        if (transition == null) return current;
        var before = transition.before();
        var after = transition.after();
        var boundary = after == null || before != null && after.stacks() < before.stacks() ? before : after;
        var result = new ArrayList<AbilityBinding>();
        for (var binding : current)
            if (binding.grant().owner() != AbilityGrant.Owner.EFFECT_INSTANCE
                    || !transition.instance().equals(binding.source().grant())) result.add(binding);
        if (boundary != null) {
            var runtime = new ActorRuntimeState(Map.of(boundary.id(), boundary));
            result.addAll(bindings(actor, instance, new ActorStates.State(boundary.revision(),
                    ActorPersistentState.empty(), runtime), registry));
        }
        return List.copyOf(result);
    }
    public static FactSlice apply(FactSlice input, ActorStates.State state) {
        return apply(input, state, List.of());
    }
    public static FactSlice apply(FactSlice input, ActorStates.State state, List<EffectDefinition.Modifier> continuous) {
        var values = new LinkedHashMap<FactKey<?>, Object>();
        var keys = new HashSet<FactKey<?>>();
        var modifiers = state.runtime().effects().values().stream().sorted(Comparator.comparing(EffectInstance::id)).toList();
        for (var effect : modifiers) for (var modifier : effect.definition().modifiers())
            keys.add(new FactKey<>(modifier.stat().id(), Double.class));
        for (var modifier : continuous) keys.add(new FactKey<>(modifier.stat().id(), Double.class));
        for (var key : keys) {
            var numeric = new FactKey<>(key.id(), Double.class);
            double value = input.read(new ReadContract(Set.of(numeric)), numeric);
            values.put(numeric, stat(state, key.id(), value, continuous));
        }
        return input.derive(values);
    }
    public static double stat(ActorStates.State state, String key, double base) {
        return stat(state, key, base, List.of());
    }
    public static double stat(ActorStates.State state, String key, double base, List<EffectDefinition.Modifier> continuous) {
        double addition = 0, multiplier = 1;
        for (var effect : state.runtime().effects().values().stream().sorted(Comparator.comparing(EffectInstance::id)).toList())
            for (var modifier : effect.definition().modifiers()) if (modifier.stat().id().equals(key)) {
                int scale = modifier.scaling() == EffectDefinition.Scaling.PER_STACK ? effect.stacks() : 1;
                if (modifier.operator() == EffectDefinition.Operator.ADD) addition += modifier.amount() * scale;
                else multiplier *= Math.pow(modifier.amount(), scale);
            }
        for (var modifier : continuous) if (modifier.stat().id().equals(key)) {
            if (modifier.operator() == EffectDefinition.Operator.ADD) addition += modifier.amount();
            else multiplier *= modifier.amount();
        }
        double value = (base + addition) * multiplier;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("modifier overflow");
        if (key.equals(ActorFacts.ARMOR_CLASS.id()) || key.equals(ActorFacts.DAMAGE_REDUCTION.id())) {
            if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("defense statistic overflow");
            value = Math.floor(Math.max(0, value));
        }
        return value;
    }
}
