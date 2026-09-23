package cc.sighs.dndturn.platform.server.control;

import cc.sighs.dndturn.domain.control.GateDecision;

/** Pure subsystem decisions. No native references, mutations, or transferable authority. */
public final class ActorControlPolicy {
    private ActorControlPolicy() {}
    public record Facts(boolean regionControlled, boolean movementLease, boolean useStep, GateDecision.Evidence evidence) {}
    public static GateDecision autonomousDecision(Facts f) {
        return (f.regionControlled() ? GateDecision.hold("AUTONOMOUS_PROCESS_HELD") : GateDecision.allow("NATIVE_AUTONOMY")).observedAt(f.evidence());
    }
    public static GateDecision nativeMovement(Facts f) {
        return (!f.regionControlled() || f.movementLease()
            ? GateDecision.allow("NATIVE_MOVEMENT") : GateDecision.hold("MOVEMENT_LEASE_REQUIRED")).observedAt(f.evidence());
    }
    public static GateDecision activeUse(Facts f) {
        return (!f.regionControlled() || f.useStep()
            ? GateDecision.allow("NATIVE_USE_STEP") : GateDecision.hold("USE_SCOPE_REQUIRED")).observedAt(f.evidence());
    }
    public static GateDecision leasedMovement(Facts f) {
        return (f.movementLease() ? GateDecision.allow("MOVEMENT_LEASE") : GateDecision.deny("NO_MOVEMENT_LEASE")).observedAt(f.evidence());
    }
    public static GateDecision bodyTravel(Facts f, boolean packetDrivenPlayer) {
        return (packetDrivenPlayer && f.regionControlled() && f.movementLease()
            ? GateDecision.hold("PACKET_DRIVER_OWNS_MOVEMENT") : GateDecision.allow("BODY_TRAVEL")).observedAt(f.evidence());
    }
}
