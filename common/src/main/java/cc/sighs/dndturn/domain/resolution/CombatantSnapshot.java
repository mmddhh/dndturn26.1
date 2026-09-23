package cc.sighs.dndturn.domain.resolution;

import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import java.util.Objects;

/** Encounter remains the sole writer; this composition cannot spend resources or roll initiative. */
public record CombatantSnapshot(ActorSnapshot actor, EncounterAuthority.MemberView participant) {
    public CombatantSnapshot {
        Objects.requireNonNull(actor);
        if (participant != null && !actor.actor().equals(participant.id()))
            throw new IllegalArgumentException("participant mismatch");
    }
}
