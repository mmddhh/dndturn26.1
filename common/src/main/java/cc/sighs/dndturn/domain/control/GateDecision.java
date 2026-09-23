package cc.sighs.dndturn.domain.control;

import java.util.Objects;
import java.util.UUID;

/** One evaluation, never a permit or lease. Native seams must re-evaluate each step. */
public record GateDecision(Disposition disposition, String reason, Evidence evidence) {
    public enum Disposition {
        /** This native step may proceed now. */ ALLOW,
        /** Skip this step; retention/rescheduling belongs to the seam. */ HOLD,
        /** Unauthorized/invalid; do not retain as accepted work. */ DENY
    }
    /** Diagnostic values only; absent fields mean that owner/identity does not apply. */
    public record Evidence(UUID generation, UUID encounter, Long encounterVersion, UUID actorInstance,
                           UUID operation, UUID environmentStep, Long serverTick) {}
    public GateDecision(Disposition disposition, String reason) { this(disposition, reason, null); }
    public GateDecision {
        Objects.requireNonNull(disposition);
        if (reason == null || !reason.matches("[A-Z][A-Z0-9_]{0,95}"))
            throw new IllegalArgumentException("gate reason code");
    }
    public boolean allowed() { return disposition == Disposition.ALLOW; }
    public GateDecision observedAt(Evidence evidence) { return new GateDecision(disposition, reason, evidence); }
    public static GateDecision allow(String reason) { return new GateDecision(Disposition.ALLOW, reason); }
    public static GateDecision hold(String reason) { return new GateDecision(Disposition.HOLD, reason); }
    public static GateDecision deny(String reason) { return new GateDecision(Disposition.DENY, reason); }
}
