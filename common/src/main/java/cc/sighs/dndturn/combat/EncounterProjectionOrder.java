package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.UUID;

/** Orders immutable client projections without changing the underlying encounter identity. */
public final class EncounterProjectionOrder {
    public enum Decision { IGNORE, ACCEPT_KEEP_RESULTS, ACCEPT_RESET_RESULTS }

    public record Update(Decision decision, int nextResultIndex, boolean resyncPending) {}

    public record Stamp(UUID generation, UUID encounterId, long sequence,
                        long version, boolean active, long projectionRevision) {
        public Stamp(UUID generation, UUID encounterId, long sequence, long version, boolean active) {
            this(generation, encounterId, sequence, version, active, 0);
        }
        public Stamp {
            Objects.requireNonNull(generation);
            Objects.requireNonNull(encounterId);
            if (sequence < 0 || version < 0 || projectionRevision < 0) throw new IllegalArgumentException("projection stamp");
        }
    }

    private EncounterProjectionOrder() {}

    public static Update advance(Stamp previous, int nextResultIndex, boolean resyncPending,
                                 Stamp incoming) {
        if (nextResultIndex < 0) throw new IllegalArgumentException("negative result cursor");
        Decision decision = decide(previous, incoming);
        return decision == Decision.ACCEPT_RESET_RESULTS
            ? new Update(decision, 0, false)
            : new Update(decision, nextResultIndex, resyncPending);
    }

    public static Decision decide(Stamp previous, Stamp incoming) {
        Objects.requireNonNull(incoming);
        if (previous == null || !previous.generation().equals(incoming.generation()))
            return Decision.ACCEPT_RESET_RESULTS;
        if (incoming.sequence() < previous.sequence()) return Decision.IGNORE;
        boolean sameEncounter = previous.encounterId().equals(incoming.encounterId());
        if (incoming.sequence() == previous.sequence() && !sameEncounter) return Decision.IGNORE;
        if (sameEncounter) {
            if (incoming.version() < previous.version()
                || incoming.sequence() == previous.sequence() && incoming.projectionRevision() < previous.projectionRevision()
                || !previous.active() && incoming.active()
                || incoming.sequence() == previous.sequence()
                    && incoming.version() == previous.version()
                    && incoming.active() == previous.active()
                    && incoming.projectionRevision() == previous.projectionRevision()) return Decision.IGNORE;
            return Decision.ACCEPT_KEEP_RESULTS;
        }
        return Decision.ACCEPT_RESET_RESULTS;
    }
}
