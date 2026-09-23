package cc.sighs.dndturn.domain.resolution;

import cc.sighs.dndturn.domain.ability.AbilityInvocation;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import java.util.*;

/** Only the invocation participants, never the full roster/history/region state. */
public record EncounterInput(UUID id, long version, EncounterPhase phase, long round, UUID current,
                             Map<UUID, EncounterAuthority.MemberView> members) {
    public EncounterInput {
        Objects.requireNonNull(id); Objects.requireNonNull(phase); members = Map.copyOf(members);
        if (version < 0 || round < 0 || members.size() > 2) throw new IllegalArgumentException("encounter input");
    }
    public static EncounterInput capture(EncounterAuthority.StateView state, AbilityInvocation invocation) {
        var members = new LinkedHashMap<UUID, EncounterAuthority.MemberView>();
        if (state.members().containsKey(invocation.actor())) members.put(invocation.actor(), state.members().get(invocation.actor()));
        var target = invocation.intent().target().entity();
        if (target != null && state.members().containsKey(target)) members.put(target, state.members().get(target));
        return new EncounterInput(state.id(), state.version(), state.phase(), state.round(), state.current(), members);
    }
}
