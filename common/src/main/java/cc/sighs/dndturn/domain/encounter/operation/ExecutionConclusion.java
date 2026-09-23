package cc.sighs.dndturn.domain.encounter.operation;

import java.util.Objects;

/** Immutable world conclusion and independent control-release evidence. Costs remain in child results. */
public record ExecutionConclusion(OperationRecord.Outcome effect, Release release, String releaseReason) {
    public enum Release { RELEASED, FAILED }
    public ExecutionConclusion {
        Objects.requireNonNull(effect); Objects.requireNonNull(release); Objects.requireNonNull(releaseReason);
        if (effect == OperationRecord.Outcome.ACCEPTED || releaseReason.length() > 512
            || release == Release.FAILED && releaseReason.isBlank())
            throw new IllegalArgumentException("invalid execution conclusion");
    }
    /** A control failure cannot erase confirmed effects or make unknown effects known. */
    public OperationRecord.Outcome outcome() { return effect; }
}
