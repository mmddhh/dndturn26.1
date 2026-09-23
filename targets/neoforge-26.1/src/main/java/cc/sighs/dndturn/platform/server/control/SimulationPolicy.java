package cc.sighs.dndturn.platform.server.control;

import cc.sighs.dndturn.domain.control.GateDecision;
import cc.sighs.dndturn.platform.server.world.EnvironmentProcesses;

/** No tick consumption, movement reservation, or lease revocation occurs in evaluation. */
public final class SimulationPolicy {
    private SimulationPolicy() {}
    public record BlockFacts(boolean worldRunsNormally, boolean outsideAuthorizedStep) {}
    /** Display-only projection: a held environment callback needs an audited visual signal. */
    public static boolean needsMaintenanceSignal(BlockFacts f) {
        return f.worldRunsNormally() && !process(EnvironmentProcesses.Policy.ENVIRONMENT, f).allowed();
    }
    public record EntityFacts(boolean quarantined, boolean environmentProcess,
                              boolean outsideAuthorizedStep, boolean pendingContact) {}
    public static GateDecision entity(EntityFacts f) {
        if (f.quarantined()) return GateDecision.hold("PROJECTILE_QUARANTINED");
        if (f.pendingContact()) return GateDecision.hold("PROJECTILE_CONTACT_PENDING");
        return f.environmentProcess() && f.outsideAuthorizedStep()
            ? GateDecision.hold("ENVIRONMENT_STEP_REQUIRED") : GateDecision.allow("ENTITY_BODY_STEP");
    }
    public static GateDecision presentationDuringEnvironment(boolean quarantined, boolean pendingContact,
            boolean recovering, boolean environmentPhase, boolean frozen) {
        if (recovering) return GateDecision.hold("RECOVERY_PENDING");
        if (frozen) return GateDecision.hold("SERVER_FROZEN");
        return entity(new EntityFacts(quarantined, true, !environmentPhase, pendingContact));
    }
    public static GateDecision process(EnvironmentProcesses.Policy temporal, BlockFacts f) {
        if (temporal != EnvironmentProcesses.Policy.ENVIRONMENT) return GateDecision.allow("DIRECT_OR_MAINTENANCE");
        if (!f.worldRunsNormally()) return GateDecision.hold("WORLD_SIMULATION_STOPPED");
        return f.outsideAuthorizedStep() ? GateDecision.hold("ENVIRONMENT_STEP_REQUIRED") : GateDecision.allow("ENVIRONMENT_STEP");
    }
}
