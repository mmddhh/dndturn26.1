package cc.sighs.dndturn.domain.fact;

import java.util.Set;

/** Complete bounded read set of one resolution step. */
public record ReadContract(Set<FactKey<?>> keys, Set<FactKey<?>> actorKeys, Set<FactKey<?>> targetKeys, int worldFactBudget) {
    public ReadContract(Set<FactKey<?>> keys) { this(keys, Set.of(), Set.of(), 128); }
    public ReadContract {
        keys = Set.copyOf(keys);
        actorKeys = Set.copyOf(actorKeys); targetKeys = Set.copyOf(targetKeys);
        if (keys.size() + actorKeys.size() + targetKeys.size() > 128 || worldFactBudget < 1 || worldFactBudget > 128 || keys.size() > worldFactBudget
                || keys.stream().map(FactKey::id).distinct().count() != keys.size()
                || actorKeys.stream().map(FactKey::id).distinct().count() != actorKeys.size()
                || targetKeys.stream().map(FactKey::id).distinct().count() != targetKeys.size())
            throw new IllegalArgumentException("fact budget or conflicting types");
    }
    public ReadContract actor() { return new ReadContract(actorKeys); }
    public ReadContract target() { return new ReadContract(targetKeys); }
    public ReadContract world() { return new ReadContract(keys); }
}
