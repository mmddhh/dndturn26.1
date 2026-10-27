package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.effect.EffectDefinition;
import java.util.*;

/** Continuous contributions are derived from live bindings, never written as duplicate effects. */
public final class ContinuousAbilities {
    public record Contribution(List<EffectDefinition.Modifier> modifiers, List<EffectDefinition.Grant> grants) {
        public Contribution {
            modifiers = List.copyOf(modifiers); grants = List.copyOf(grants);
            if (modifiers.size() > 64 || grants.size() > 64 || new HashSet<>(grants).size() != grants.size())
                throw new IllegalArgumentException("continuous contribution bounds");
        }
    }
    public record SourcedModifier(AbilityBinding source, EffectDefinition.Modifier modifier) {
        public SourcedModifier { Objects.requireNonNull(source); Objects.requireNonNull(modifier); }
    }
    public record Expanded(List<AbilityBinding> bindings, List<SourcedModifier> sources) {
        public Expanded { bindings = List.copyOf(bindings); sources = List.copyOf(sources); }
        public List<EffectDefinition.Modifier> modifiers() { return sources.stream().map(SourcedModifier::modifier).toList(); }
    }
    private final Map<String, Contribution> contributions = new LinkedHashMap<>();
    private boolean frozen;
    public void register(AbilityDefinition definition, Contribution contribution) {
        Objects.requireNonNull(contribution);
        if (definition.activation() != AbilityDefinition.Activation.CONTINUOUS || !definition.cost().equals(ActionCost.FREE))
            throw new IllegalArgumentException("continuous ability contract");
        if (frozen || contributions.size() >= 64 || contributions.containsKey(definition.id()))
            throw new IllegalStateException("continuous registration conflict or closed");
        contributions.put(definition.id(), contribution);
    }
    public void freeze(AbilityRegistry registry) {
        for (var definition : registry.all())
            if (definition.activation() == AbilityDefinition.Activation.CONTINUOUS
                    && !contributions.containsKey(definition.id()))
                throw new IllegalStateException("continuous contribution missing: " + definition.id());
        for (var contribution : contributions.values()) for (var grant : contribution.grants()) registry.require(grant.ability(), grant.version());
        for (var id : contributions.keySet()) checkCycle(id, new HashSet<>(), new HashSet<>());
        frozen = true;
    }
    private void checkCycle(String id, Set<String> path, Set<String> complete) {
        if (complete.contains(id) || !contributions.containsKey(id)) return;
        if (!path.add(id)) throw new IllegalArgumentException("continuous grant cycle: " + id);
        for (var grant : contributions.get(id).grants()) checkCycle(grant.ability(), path, complete);
        path.remove(id); complete.add(id);
    }
    public boolean isEmpty() { return contributions.isEmpty(); }
    public List<EffectDefinition.Modifier> registeredModifiers() {
        return contributions.values().stream().flatMap(value -> value.modifiers().stream()).toList();
    }
    public Expanded expand(List<AbilityBinding> roots, AbilityRegistry registry) {
        var bindings = new LinkedHashSet<>(roots);
        var pending = new ArrayDeque<>(bindings);
        var modifiers = new ArrayList<SourcedModifier>();
        while (!pending.isEmpty()) {
            var binding = pending.removeFirst();
            var contribution = contributions.get(binding.id());
            if (contribution == null) continue;
            registry.require(binding.id(), binding.version());
            contribution.modifiers().forEach(modifier -> modifiers.add(new SourcedModifier(binding, modifier)));
            if (modifiers.size() > 256) throw new IllegalStateException("continuous modifier budget exceeded");
            for (var grant : contribution.grants()) {
                var child = new AbilityBinding(registry.require(grant.ability(), grant.version()), binding.grant());
                if (bindings.add(child)) {
                    if (bindings.size() > 1024) throw new IllegalStateException("continuous grant budget exceeded");
                    pending.addLast(child);
                }
            }
        }
        return new Expanded(List.copyOf(bindings), modifiers);
    }
}
