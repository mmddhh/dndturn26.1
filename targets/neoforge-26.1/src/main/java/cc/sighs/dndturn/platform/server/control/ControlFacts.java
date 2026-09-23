package cc.sighs.dndturn.platform.server.control;

import cc.sighs.dndturn.domain.control.GateDecision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Bounded cross-owner ControlFacts commands; implementations retain thread and identity validation. */
public interface ControlFacts {
    GateDecision.Evidence controlEvidence(Entity entity);
    OperationAdmissionPolicy.Facts captureAdmission(LivingEntity actor, boolean rejectRunning);
    SimulationPolicy.EntityFacts captureEntitySimulation(Entity entity);
    boolean cloudSimulationPaused(Entity cloud);
    boolean cloudTargetAllowed(Entity cloud, LivingEntity target);
}
