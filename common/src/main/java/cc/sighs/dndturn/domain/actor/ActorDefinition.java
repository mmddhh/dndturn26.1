package cc.sighs.dndturn.domain.actor;

import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import java.util.*;

/** Cold definition. Native type identity is a namespaced value, never an EntityType. */
public record ActorDefinition(String id, int version, Set<String> groups, FactSlice defaults,
                              List<EffectDefinition.Grant> intrinsicGrants, Classification classification,
                              Map<String, ResourceDescriptor> resources) {
    /** Absent rule classifications stay unknown; native dimensions/type do not synthesize them. */
    public record Classification(String creatureType, String size, Set<String> tags) {
        public Classification {
            if (creatureType != null) FactKey.requireId(creatureType);
            if (size != null) FactKey.requireId(size);
            tags = Set.copyOf(tags); tags.forEach(FactKey::requireId);
            if (tags.size() > 64) throw new IllegalArgumentException("classification tags");
        }
        public static Classification unspecified() { return new Classification(null, null, Set.of()); }
    }
    /** Describes an existing Actor-owned resource. Declaration neither creates a balance nor implements refresh. */
    public record ResourceDescriptor(String unit, String capacityFact, String refreshRule) {
        public ResourceDescriptor {
            FactKey.requireId(unit);
            if (capacityFact != null) FactKey.requireId(capacityFact);
            if (refreshRule != null) FactKey.requireId(refreshRule);
        }
    }
    public ActorDefinition {
        FactKey.requireId(id); groups = Set.copyOf(groups); Objects.requireNonNull(defaults);
        intrinsicGrants = List.copyOf(intrinsicGrants);
        Objects.requireNonNull(classification); resources = Map.copyOf(resources); resources.keySet().forEach(FactKey::requireId);
        if (resources.size() > 128) throw new IllegalArgumentException("resource descriptors");
        if (version < 1 || groups.size() > 64 || intrinsicGrants.size() > 64 || new HashSet<>(intrinsicGrants).size() != intrinsicGrants.size())
            throw new IllegalArgumentException("actor definition");
        groups.forEach(FactKey::requireId);
    }
}
