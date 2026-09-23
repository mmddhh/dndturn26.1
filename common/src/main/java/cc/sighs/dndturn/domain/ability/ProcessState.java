package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** What remains to execute, distinct from the observations of what has already happened. */
public record ProcessState(UUID id, ProcessDefinition definition, Owner owner, UUID cause, UUID root,
        UUID encounter, UUID instance, int roundTicks, long revision, String phase, Map<String, String> values,
        long steps, long nextBoundary, Entropy entropy, boolean cancellationRequested,
        boolean nativePending, OperationRecord.Outcome terminal, Release release, String reason) {
    public enum Ownership { ACTOR, ENCOUNTER, ENVIRONMENT }
    public enum Release { HELD, RELEASED, FAILED }
    public record Owner(Ownership kind, UUID id) {
        public Owner { Objects.requireNonNull(kind); Objects.requireNonNull(id); }
    }
    /** A separate process stream: pure sampling returns the next cursor rather than changing entity RNG. */
    public record Entropy(long seed, long cursor) {
        public Entropy { if (cursor < 0) throw new IllegalArgumentException("entropy cursor"); }
        public Draw draw() {
            long z = seed + 0x9e3779b97f4a7c15L * (cursor + 1);
            z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
            z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
            return new Draw(z ^ (z >>> 31), new Entropy(seed, Math.addExact(cursor, 1)));
        }
    }
    public record Draw(long value, Entropy next) {}
    public ProcessState {
        Objects.requireNonNull(id); Objects.requireNonNull(definition); Objects.requireNonNull(owner);
        Objects.requireNonNull(cause); Objects.requireNonNull(root); Objects.requireNonNull(encounter);
        Objects.requireNonNull(instance); Objects.requireNonNull(phase); Objects.requireNonNull(entropy);
        Objects.requireNonNull(release); Objects.requireNonNull(reason); values = Map.copyOf(values);
        if (roundTicks < 1 || revision < 0 || steps < 0 || steps > definition.maximumSteps() || nextBoundary < steps
                || !definition.transitions().containsKey(phase) || reason.length() > 512 || values.size() > 32
                || values.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getKey().length() > 64 || e.getValue().length() > 512)
                || terminal != null && terminal != OperationRecord.Outcome.COMPLETED && terminal != OperationRecord.Outcome.PARTIAL
                    && terminal != OperationRecord.Outcome.INTERRUPTED && terminal != OperationRecord.Outcome.REJECTED
                    && terminal != OperationRecord.Outcome.UNKNOWN)
            throw new IllegalArgumentException("process state bounds");
    }
}
