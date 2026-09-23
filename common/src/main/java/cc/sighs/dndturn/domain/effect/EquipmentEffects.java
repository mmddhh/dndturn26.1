package cc.sighs.dndturn.domain.effect;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;

/** Typed equipment contributions from live tactical effects. Neither a species table nor a permit. */
public final class EquipmentEffects {
    public enum Ammunition { HELD_OR_NATIVE_ORDINARY_ARROW }
    public enum Consumption { NATIVE_NO_ITEM_COST }
    public record Policy(String id, int version, Ammunition ammunition, Consumption consumption) {
        public Policy {
            FactKey.requireId(id); Objects.requireNonNull(ammunition); Objects.requireNonNull(consumption);
            if (version < 1) throw new IllegalArgumentException("equipment policy version");
        }
    }
    public record Contribution(Policy policy, UUID effect, long grantRevision) {
        public Contribution { Objects.requireNonNull(policy); Objects.requireNonNull(effect);
            if (grantRevision < 1) throw new IllegalArgumentException("effect grant revision"); }
    }
    private record Key(String id, int version) {}
    private final Map<Key, Policy> policies = new LinkedHashMap<>();
    private boolean frozen;
    public void register(EffectDefinition effect, Policy policy) {
        Objects.requireNonNull(effect); Objects.requireNonNull(policy);
        if (frozen || policies.size() >= 128 || policies.putIfAbsent(new Key(effect.id(), effect.version()), policy) != null)
            throw new IllegalStateException("equipment effect registration closed or duplicate");
    }
    public void freeze() { frozen = true; }
    public Optional<Contribution> resolve(Collection<EffectInstance> effects) {
        if (effects.size() > 256) throw new IllegalStateException("equipment effect budget");
        Contribution result = null;
        for (var effect : effects) {
            var policy = policies.get(new Key(effect.definition().id(), effect.definition().version()));
            if (policy == null) continue;
            if (result != null) throw new IllegalStateException("conflicting equipment effects");
            result = new Contribution(policy, effect.id(), effect.grantRevision());
        }
        return Optional.ofNullable(result);
    }
}
