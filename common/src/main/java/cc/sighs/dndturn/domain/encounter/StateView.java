package cc.sighs.dndturn.domain.encounter;

import java.util.*;

/** Detached immutable encounter read value. */
public record StateView(UUID id, EncounterPhase phase, long version, List<UUID> order, UUID current,
                        Map<UUID, MemberView> members, long round, int environmentRemaining,
                        EncounterRegion region) {
    public StateView { order = List.copyOf(order); members = Map.copyOf(members); }
    public TurnParticipant environment() { return TurnParticipant.environment(id); }
    public List<TurnParticipant> participants() {
        return order.stream().map(value -> value.equals(environment().id()) ? environment() : TurnParticipant.entity(value)).toList();
    }
    public TurnParticipant currentParticipant() {
        return current == null ? null : current.equals(environment().id()) ? environment() : TurnParticipant.entity(current);
    }
}
