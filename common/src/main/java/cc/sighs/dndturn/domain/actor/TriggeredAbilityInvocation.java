package cc.sighs.dndturn.domain.actor;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.Objects;

/** A reaction's request to the normal ability backend. Timing is owned by the server driver. */
public record TriggeredAbilityInvocation(String ability, int version, ActionIntent.Target target, Timing timing)
        implements RuleEmission {
    public enum Timing { IMMEDIATE, OWNER_TURN_END }
    public TriggeredAbilityInvocation {
        FactKey.requireId(ability); Objects.requireNonNull(target); Objects.requireNonNull(timing);
        if (version < 1) throw new IllegalArgumentException("triggered ability version");
    }
}
