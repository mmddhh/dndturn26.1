package cc.sighs.dndturn.domain.actor;

import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import java.util.*;

/** Rule-only derivation after native mapping. It does not reapply native modifiers. */
public record StatResolution(String stat, double base, double addition, double multiplier, double value,
                             List<Contribution> contributions) {
    public record Contribution(String source, UUID effect, GrantEvidence grant,
                               EffectDefinition.Operator operator, double operand, int scale) {
        public Contribution {
            Objects.requireNonNull(source); Objects.requireNonNull(operator);
            if (!Double.isFinite(operand) || scale < 1 || scale > 64) throw new IllegalArgumentException("stat contribution");
        }
    }
    public StatResolution {
        Objects.requireNonNull(stat); contributions = List.copyOf(contributions);
        if (!Double.isFinite(base) || !Double.isFinite(addition) || !Double.isFinite(multiplier) || !Double.isFinite(value)
                || contributions.size() > 16640) throw new IllegalArgumentException("stat resolution bounds");
    }
}
