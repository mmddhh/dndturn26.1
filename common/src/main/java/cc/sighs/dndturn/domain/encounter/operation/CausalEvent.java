package cc.sighs.dndturn.domain.encounter.operation;

import java.util.Objects;
import java.util.UUID;

/** Detached causal boundary. An attempt is never evidence that a world mutation occurred. */
public record CausalEvent(UUID id, UUID operation, UUID root, UUID encounter, long revision,
                          UUID source, UUID subject, Phase phase, Damage damage) {
    public enum Phase { ATTEMPT, RESULT, OBSERVATION }
    public record Damage(float amount, boolean projectile, UUID directSource) {
        public Damage {
            if (!Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("causal damage");
        }
    }
    public CausalEvent {
        Objects.requireNonNull(id); Objects.requireNonNull(operation); Objects.requireNonNull(root);
        Objects.requireNonNull(encounter); Objects.requireNonNull(subject); Objects.requireNonNull(phase);
        Objects.requireNonNull(damage);
        if (revision < 0) throw new IllegalArgumentException("causal revision");
    }
}
