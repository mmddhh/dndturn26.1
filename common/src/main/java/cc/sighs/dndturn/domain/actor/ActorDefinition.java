package cc.sighs.dndturn.domain.actor;

import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import java.util.*;

/** Cold definition. Native type identity is a namespaced value, never an EntityType. */
public record ActorDefinition(String id, int version, Set<String> groups, FactSlice defaults,
                              List<EffectDefinition.Grant> intrinsicGrants) {
    public ActorDefinition(String id, int version, Set<String> groups, FactSlice defaults) {
        this(id, version, groups, defaults, List.of());
    }
    public ActorDefinition {
        FactKey.requireId(id); groups = Set.copyOf(groups); Objects.requireNonNull(defaults);
        intrinsicGrants = List.copyOf(intrinsicGrants);
        if (version < 1 || groups.size() > 64 || intrinsicGrants.size() > 64 || new HashSet<>(intrinsicGrants).size() != intrinsicGrants.size())
            throw new IllegalArgumentException("actor definition");
        groups.forEach(FactKey::requireId);
    }
}
