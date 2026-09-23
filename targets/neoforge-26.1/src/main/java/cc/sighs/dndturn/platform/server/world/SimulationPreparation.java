package cc.sighs.dndturn.platform.server.world;

import cc.sighs.dndturn.domain.control.GateDecision;
import java.util.Objects;

/** Result of an owner execution boundary, distinct from a read-only scheduling decision. */
public record SimulationPreparation(Status status, String reason) {
    public enum Status { ADVANCE, HELD, CONTACT_HANDLED, QUARANTINED, ENDED }
    public SimulationPreparation {
        Objects.requireNonNull(status);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("preparation reason required");
    }
    public boolean skipsNativeTick() { return status != Status.ADVANCE; }
    public static SimulationPreparation scheduling(GateDecision decision) {
        return new SimulationPreparation(decision.allowed() ? Status.ADVANCE : Status.HELD, decision.reason());
    }
    public static SimulationPreparation advance() { return new SimulationPreparation(Status.ADVANCE, "ENTITY_BODY_STEP"); }
    public static SimulationPreparation quarantined() { return new SimulationPreparation(Status.QUARANTINED, "PROJECTILE_QUARANTINED"); }
    public static SimulationPreparation ended() { return new SimulationPreparation(Status.ENDED, "ENTITY_REMOVED"); }
}
