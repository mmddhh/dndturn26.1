package cc.sighs.dndturn.domain.encounter.operation;

import java.util.Objects;
import java.util.UUID;

/** Only the owner of the named attempt may consume this decision. It grants no world permission. */
public record Intervention(UUID event, Disposition disposition, String reason) {
    public enum Disposition { PASS, CANCEL }
    public Intervention {
        Objects.requireNonNull(event); Objects.requireNonNull(disposition); Objects.requireNonNull(reason);
        if (reason.length() > 256) throw new IllegalArgumentException("intervention reason");
    }
    public static Intervention pass(CausalEvent event) { return new Intervention(event.id(), Disposition.PASS, ""); }
    public Intervention combine(Intervention other) {
        if (!event.equals(other.event)) throw new IllegalArgumentException("intervention event mismatch");
        return disposition == Disposition.CANCEL ? this : other;
    }
}
