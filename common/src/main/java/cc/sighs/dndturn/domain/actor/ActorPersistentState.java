package cc.sighs.dndturn.domain.actor;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;

/** Actor-owned values, independent of native health and Encounter balances. */
public record ActorPersistentState(Map<UUID, Learned> grants, Set<UUID> prepared, Map<String, Double> resources) {
    public record Learned(UUID id, String ability, int version, String origin, boolean requiresPreparation, long revision) {
        public Learned {
            Objects.requireNonNull(id); FactKey.requireId(ability); FactKey.requireId(origin);
            if (version < 1 || revision < 1) throw new IllegalArgumentException("ability version");
        }
    }
    public ActorPersistentState {
        grants = Map.copyOf(grants); prepared = Set.copyOf(prepared); resources = Map.copyOf(resources);
        if (grants.size() > 256 || resources.size() > 128 || !grants.keySet().containsAll(prepared))
            throw new IllegalArgumentException("actor state bounds or unknown prepared grant");
        grants.forEach((id, grant) -> { if (!id.equals(grant.id())) throw new IllegalArgumentException("grant identity"); });
        resources.forEach((id, value) -> {
            FactKey.requireId(id);
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("resource value");
        });
    }
    public static ActorPersistentState empty() { return new ActorPersistentState(Map.of(), Set.of(), Map.of()); }
}
