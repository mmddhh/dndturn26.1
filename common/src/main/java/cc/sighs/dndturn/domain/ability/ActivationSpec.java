package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.effect.EffectDefinition;
import java.util.Objects;

/** Scheduling semantics, independent of UI active/passive categories. */
public record ActivationSpec(AbilityDefinition.Activation kind, Event event, EffectDefinition.Clock clock, int interval) {
    public enum Event { APPLIED, REMOVED, EXPIRED, STACK_CHANGED, HIT, DAMAGED, TURN_START, TURN_END, ABILITY_USED, ABILITY_RESOLVED, DAMAGE_ATTEMPT }
    public ActivationSpec {
        Objects.requireNonNull(kind);
        if (kind == AbilityDefinition.Activation.TRIGGERED ? event == null : event != null)
            throw new IllegalArgumentException("activation event");
        if (kind == AbilityDefinition.Activation.PERIODIC
                ? clock == null || clock == EffectDefinition.Clock.EXPLICIT || interval < 1 || interval > 1000000
                : clock != null || interval != 0) throw new IllegalArgumentException("activation clock");
    }
    public static ActivationSpec manual() { return new ActivationSpec(AbilityDefinition.Activation.MANUAL, null, null, 0); }
}
