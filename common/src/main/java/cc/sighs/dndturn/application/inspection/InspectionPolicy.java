package cc.sighs.dndturn.application.inspection;

import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import java.util.*;
import java.util.function.BiPredicate;

/** Pure disclosure registry. No persistent observer knowledge is implied. */
public final class InspectionPolicy {
    public static final FactKey<String> NAME = new FactKey<>("dndturn:public_name", String.class);
    public static final FactKey<String> TYPE = new FactKey<>("dndturn:public_type", String.class);
    public static final FactKey<Boolean> DODGING = new FactKey<>("dndturn:public_dodging", Boolean.class);
    public static final FactKey<Boolean> DISENGAGED = new FactKey<>("dndturn:public_disengaged", Boolean.class);
    public record Observer(UUID observer, UUID target) {
        public Observer { Objects.requireNonNull(observer); Objects.requireNonNull(target); }
    }
    public record Field(String id, String value) {
        public Field {
            Objects.requireNonNull(id); Objects.requireNonNull(value);
            if (id.isBlank() || id.length() > 128 || value.length() > 256)
                throw new IllegalArgumentException("inspection field bounds");
        }
    }
    private record Rule(FactKey<?> output, ReadContract reads, BiPredicate<Observer, FactSlice> visible) {}
    private final Map<String, Rule> rules = new TreeMap<>();
    private boolean frozen;
    public InspectionPolicy() {
        register(NAME, new ReadContract(Set.of(NAME)), (observer, facts) -> true);
        register(TYPE, new ReadContract(Set.of(TYPE)), (observer, facts) -> true);
        register(DODGING, new ReadContract(Set.of(DODGING)), (observer, facts) -> facts.read(new ReadContract(Set.of(DODGING)), DODGING));
        register(DISENGAGED, new ReadContract(Set.of(DISENGAGED)), (observer, facts) -> facts.read(new ReadContract(Set.of(DISENGAGED)), DISENGAGED));
    }
    /** Trusted pure integrations only; each output has exactly one disclosure owner. */
    public synchronized void register(FactKey<?> output, ReadContract reads, BiPredicate<Observer, FactSlice> visible) {
        Objects.requireNonNull(output); Objects.requireNonNull(reads); Objects.requireNonNull(visible);
        if (frozen) throw new IllegalStateException("inspection policy frozen");
        if (rules.size() >= 32 || rules.containsKey(output.id()) || !reads.keys().contains(output)
                || !reads.actorKeys().isEmpty() || !reads.targetKeys().isEmpty())
            throw new IllegalArgumentException("inspection disclosure conflict or bounds");
        var combined = new HashSet<>(reads().keys()); combined.addAll(reads.keys());
        new ReadContract(combined);
        rules.put(output.id(), new Rule(output, reads, visible));
    }
    public synchronized void freeze() { frozen = true; }
    public ReadContract reads() {
        var keys = new HashSet<FactKey<?>>();
        rules.values().forEach(rule -> keys.addAll(rule.reads().keys()));
        return new ReadContract(keys);
    }
    public ActorViews.InspectionView project(Observer observer, UUID instance, UUID capture, FactSlice facts) {
        if (!frozen) throw new IllegalStateException("inspection policy must be frozen");
        var fields = new ArrayList<Field>();
        for (var rule : rules.values()) {
            var slice = facts.restrict(rule.reads());
            try {
                if (rule.visible().test(observer, slice))
                    fields.add(new Field(rule.output().id(), String.valueOf(slice.read(rule.reads(), rule.output()))));
            } catch (FactSlice.MissingFact missing) {
                // Unknown facts disclose neither a fabricated value nor the hidden field name.
            }
        }
        return new ActorViews.InspectionView(observer.target(), instance, capture, fields);
    }
}
