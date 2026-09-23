package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import java.util.*;

/** Server-normalized evaluation input. Operation identity remains in the existing ledger envelope. */
public record AbilityInvocation(UUID actor, AbilityBinding binding, ActionIntent intent,
                                ActorSnapshot.Revision revision) {
    public AbilityInvocation {
        Objects.requireNonNull(actor); Objects.requireNonNull(binding); Objects.requireNonNull(intent);
        Objects.requireNonNull(revision);
        if (!actor.equals(binding.grant().actor()) || !binding.id().equals(intent.behaviorId())
                || binding.version() != intent.behaviorVersion() || binding.kind() != intent.capability())
            throw new IllegalArgumentException("binding invocation mismatch");
    }
}
