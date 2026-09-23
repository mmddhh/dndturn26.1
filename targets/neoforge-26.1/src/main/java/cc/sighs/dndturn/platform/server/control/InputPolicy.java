package cc.sighs.dndturn.platform.server.control;

import cc.sighs.dndturn.domain.control.GateDecision;

/** Input authority only. ALLOW never bypasses vanilla packet/container validation. */
public final class InputPolicy {
    private InputPolicy() {}
    public record Facts(boolean member, boolean regionControlled, boolean movementLease,
                        OperationAdmissionPolicy.Facts organization, boolean containerAuthorized) {}
    public static GateDecision gameplay(Facts f) {
        return f.member() || f.regionControlled() ? GateDecision.deny("TACTICAL_INPUT_REQUIRED") : GateDecision.allow("VANILLA_INPUT");
    }
    public static GateDecision movement(Facts f) {
        return f.regionControlled() && !f.movementLease() ? GateDecision.deny("MOVEMENT_LEASE_REQUIRED") : GateDecision.allow("MOVEMENT_INPUT");
    }
    public static GateDecision organize(Facts f) {
        return f.organization() == null ? GateDecision.deny("NO_ENCOUNTER") : OperationAdmissionPolicy.evaluate(f.organization());
    }
    public static GateDecision carriedSlot(Facts f) {
        if (gameplay(f).allowed()) return GateDecision.allow("VANILLA_SLOT");
        return f.member() ? GateDecision.deny("EXPLICIT_SELECTION_REQUIRED") : organize(f);
    }
    public static GateDecision container(Facts f) {
        return f.containerAuthorized() ? GateDecision.allow("CONTAINER_SCOPE") : GateDecision.deny("NO_CONTAINER_SCOPE");
    }
}
