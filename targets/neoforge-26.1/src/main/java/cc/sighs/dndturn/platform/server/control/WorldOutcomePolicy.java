package cc.sighs.dndturn.platform.server.control;

import cc.sighs.dndturn.domain.control.GateDecision;

/** Pure world-effect decisions. Facts are captured by owners; decisions never grant a permit. */
public final class WorldOutcomePolicy {
    private WorldOutcomePolicy() {}
    public record IncomingFacts(boolean participant, boolean authorizedEnvironmentExplosion) {}
    public record EnvironmentImpact(boolean sourceStepCurrent, boolean targetPaused, boolean sameStep) {}
    public static GateDecision explosionImpact(EnvironmentImpact f) {
        if (!f.sourceStepCurrent()) return GateDecision.deny("EFFECT_SOURCE_STEP_REVOKED");
        return !f.targetPaused() || f.sameStep() ? GateDecision.allow("ENVIRONMENT_IMPACT")
            : GateDecision.deny("ENVIRONMENT_DOMAIN_MISMATCH");
    }
    public static GateDecision explosionDamage(EnvironmentImpact f) {
        return f.sourceStepCurrent() && f.sameStep() ? GateDecision.allow("ENVIRONMENT_DAMAGE")
            : GateDecision.deny("ENVIRONMENT_DAMAGE_STEP_REQUIRED");
    }
    public record CloudFacts(boolean confirmed, boolean liveDomain, boolean domainStepActive,
                             boolean insidePausedRegion, boolean worldRunsNormally, boolean recovering) {}
    public static GateDecision cloudSimulation(CloudFacts f) {
        if (!f.worldRunsNormally()) return GateDecision.hold("WORLD_SIMULATION_STOPPED");
        if (f.recovering()) return GateDecision.hold("RECOVERY_PENDING");
        if (!f.confirmed()) return GateDecision.hold("EFFECT_SOURCE_UNCONFIRMED");
        return (f.liveDomain() ? f.domainStepActive() : !f.insidePausedRegion())
            ? GateDecision.allow("CLOUD_SIMULATION") : GateDecision.hold("ENVIRONMENT_STEP_REQUIRED");
    }
    public record CloudTargetFacts(boolean sourceAllowed, boolean liveDomain, boolean targetMember,
                                   boolean sameStep, boolean targetPaused) {}
    public static GateDecision cloudTarget(CloudTargetFacts f) {
        if (!f.sourceAllowed()) return GateDecision.deny("EFFECT_SOURCE_UNCONFIRMED");
        return (f.liveDomain() ? f.targetMember() && f.sameStep() : !f.targetPaused())
            ? GateDecision.allow("CLOUD_TARGET") : GateDecision.deny("EFFECT_TARGET_OUTSIDE_SCOPE");
    }
    public static GateDecision unscopedDamage(IncomingFacts f) {
        return !f.participant() || f.authorizedEnvironmentExplosion()
            ? GateDecision.allow("NATIVE_DAMAGE_SCOPE") : GateDecision.deny("CAUSAL_DAMAGE_AUTHORITY_REQUIRED");
    }
}
