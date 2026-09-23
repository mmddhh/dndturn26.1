package cc.sighs.dndturn.platform.server.control;

import cc.sighs.dndturn.domain.control.GateDecision;
import java.util.UUID;

/** Runtime readiness only; RuleResolver still decides legality and costs. */
public final class OperationAdmissionPolicy {
    private OperationAdmissionPolicy() {}
    public record Facts(UUID generation, UUID encounter, long version, boolean closing,
        boolean recoveryFailed, boolean recoveryPending, boolean worldEffectActive,
        boolean frozen, boolean running, boolean controlFault, boolean currentActor,
        boolean participantPhase, boolean insideRegion, boolean supportedActor) {}
    public static GateDecision evaluate(Facts f) {
        return readiness(f).observedAt(new GateDecision.Evidence(f.generation(), f.encounter(),
            f.encounter() == null ? null : f.version(), null, null, null, null));
    }
    private static GateDecision readiness(Facts f) {
        if (f.closing()) return GateDecision.deny("SERVICE_CLOSING");
        if (f.recoveryFailed() || f.controlFault()) return GateDecision.deny("CONTROL_FAULT");
        if (f.encounter() == null || !f.supportedActor()) return GateDecision.deny("ACTOR_UNAVAILABLE");
        if (f.recoveryPending()) return GateDecision.hold("RECOVERY_PENDING");
        if (f.worldEffectActive()) return GateDecision.hold("WORLD_EFFECT_ACTIVE");
        if (f.frozen()) return GateDecision.hold("SERVER_FROZEN");
        if (f.running()) return GateDecision.hold("OPERATION_RUNNING");
        if (!f.currentActor() || !f.participantPhase()) return GateDecision.deny("NOT_ACTOR_TURN");
        if (!f.insideRegion()) return GateDecision.deny("OUTSIDE_ENCOUNTER");
        return GateDecision.allow("ADMISSION_READY");
    }
}
