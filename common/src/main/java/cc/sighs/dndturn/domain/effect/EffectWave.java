package cc.sighs.dndturn.domain.effect;

import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;

/** Reconciles independently proposed writes against one immutable state. Does not commit. */
public final class EffectWave {
    public static final int MAX_DEPTH = 16, MAX_EVENTS = 256, MAX_MUTATIONS = 512;
    private EffectWave() {}
    public static final class Fault extends IllegalArgumentException {
        private final String code;
        public Fault(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
    public record Proposal(long eventSequence, UUID instance, String trigger, List<ActorStates.Change> changes) {
        public Proposal {
            Objects.requireNonNull(instance); FactKey.requireId(trigger); changes = List.copyOf(changes);
            if (eventSequence < 0 || changes.size() > MAX_MUTATIONS) throw new Fault("EFFECT_MUTATION_LIMIT");
        }
    }
    public record Prepared(List<ActorStates.Change> changes, ActorStates.Reduction reduction, int proposedMutations) {
        public Prepared {
            changes = List.copyOf(changes); Objects.requireNonNull(reduction);
            if (proposedMutations < changes.size() || proposedMutations > MAX_MUTATIONS)
                throw new Fault("EFFECT_MUTATION_LIMIT");
        }
    }
    public static Prepared prepare(ActorStates.State before, List<Proposal> proposals) {
        if (proposals.size() > MAX_MUTATIONS) throw new Fault("EFFECT_MUTATION_LIMIT");
        var ordered = proposals.stream().sorted(Comparator.comparingLong(Proposal::eventSequence)
                .thenComparing(Proposal::instance).thenComparing(Proposal::trigger)).toList();
        var writes = new LinkedHashMap<Object, List<ActorStates.Change>>();
        int count = 0;
        for (var proposal : ordered) for (var change : proposal.changes()) {
            if (++count > MAX_MUTATIONS) throw new Fault("EFFECT_MUTATION_LIMIT");
            Object key = key(before, change);
            writes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(change);
        }
        var merged = new ArrayList<ActorStates.Change>();
        for (var group : writes.values()) {
            var first = group.get(0);
            if (group.size() == 1) { merged.add(first); continue; }
            if (group.stream().allMatch(c -> c instanceof ActorStates.AddStacks)) {
                int total = group.stream().mapToInt(c -> ((ActorStates.AddStacks)c).count()).sum();
                // Reduce the aggregate once, including CLAMP; do not split an overflowing wave into ordered writes.
                var add = (ActorStates.AddStacks) first;
                var effect = before.runtime().effects().get(add.effect());
                if (effect == null) throw new Fault("EFFECT_UNKNOWN_INSTANCE");
                if (effect.definition().overflow() == EffectDefinition.Overflow.REJECT
                        && total > effect.definition().maxStacks() - effect.stacks())
                    throw new Fault("EFFECT_STACK_OVERFLOW");
                merged.add(new ActorStates.AddStacks(add.effect(), Math.min(64, total)));
            } else if (group.stream().allMatch(c -> c instanceof ActorStates.ConsumeStacks)) {
                var consume = (ActorStates.ConsumeStacks) first;
                int total = group.stream().mapToInt(c -> ((ActorStates.ConsumeStacks)c).count()).sum();
                var effect = before.runtime().effects().get(consume.effect());
                if (effect == null || total > effect.stacks()) throw new Fault("EFFECT_OVER_CONSUMPTION");
                merged.add(new ActorStates.ConsumeStacks(consume.effect(), total));
            } else throw new Fault("EFFECT_WRITE_CONFLICT");
        }
        try { return new Prepared(merged, ActorStates.preview(before, merged), count); }
        catch (Fault fault) { throw fault; }
        catch (IllegalArgumentException | ArithmeticException invalid) { throw new Fault("EFFECT_INVALID_MUTATION"); }
    }
    private record Key(String kind, Object value) {}
    private static Object key(ActorStates.State before, ActorStates.Change change) {
        if (change instanceof ActorStates.Apply apply) return new Key("effect", apply.effect().key());
        UUID id = change instanceof ActorStates.AddStacks add ? add.effect()
                : change instanceof ActorStates.ConsumeStacks consume ? consume.effect()
                : change instanceof ActorStates.RefreshDuration refresh ? refresh.effect()
                : change instanceof ActorStates.Remove remove ? remove.effect() : null;
        if (id != null) {
            var effect = before.runtime().effects().get(id);
            if (effect == null) throw new Fault("EFFECT_UNKNOWN_INSTANCE");
            return new Key("effect", effect.key());
        }
        if (change instanceof ActorStates.Resource resource) return new Key("resource", resource.key());
        if (change instanceof ActorStates.Learn learn) return new Key("grant", learn.grant().id());
        if (change instanceof ActorStates.Forget forget) return new Key("grant", forget.grant());
        if (change instanceof ActorStates.Prepare) return new Key("prepare", "all");
        throw new Fault("EFFECT_LIFECYCLE_WRITE_DENIED");
    }
}
