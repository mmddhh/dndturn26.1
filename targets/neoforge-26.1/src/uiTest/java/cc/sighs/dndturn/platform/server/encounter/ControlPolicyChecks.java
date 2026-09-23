package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.control.GateDecision;
import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.control.InputPolicy;
import cc.sighs.dndturn.platform.server.control.SimulationPolicy;
import cc.sighs.dndturn.platform.server.control.WorldOutcomePolicy;
import cc.sighs.dndturn.platform.server.world.EnvironmentProcesses;
import cc.sighs.dndturn.platform.server.world.SimulationPreparation;
import java.util.UUID;

/** Pure policy counterexamples; platform behavior is additionally covered by GameTest. */
public final class ControlPolicyChecks {
    private static void check(boolean condition) { if (!condition) throw new AssertionError("control policy contract"); }
    public static void main(String[] args) {
        var evidence = new GateDecision.Evidence(UUID.randomUUID(), UUID.randomUUID(), 7L, UUID.randomUUID(), UUID.randomUUID(), null, 12L);
        var idle = new ActorControlPolicy.Facts(true, false, false, evidence);
        var moving = new ActorControlPolicy.Facts(true, true, false, evidence);
        check(ActorControlPolicy.autonomousDecision(idle).disposition() == GateDecision.Disposition.HOLD);
        check(ActorControlPolicy.nativeMovement(idle).disposition() == GateDecision.Disposition.HOLD);
        var old = ActorControlPolicy.nativeMovement(moving);
        check(old.allowed());
        check(!ActorControlPolicy.autonomousDecision(moving).allowed());
        check(!ActorControlPolicy.activeUse(moving).allowed());
        check(SimulationPolicy.entity(new SimulationPolicy.EntityFacts(false, false, false, false)).allowed());
        check(!ActorControlPolicy.nativeMovement(idle).allowed()); // retained old decision does not change new evaluation
        check(old.equals(ActorControlPolicy.nativeMovement(moving)));
        check(old.evidence().equals(evidence));
        check(!ActorControlPolicy.bodyTravel(moving, true).allowed());
        check(ActorControlPolicy.bodyTravel(idle, true).allowed());
        var held = new SimulationPolicy.BlockFacts(true, true);
        check(!SimulationPolicy.process(EnvironmentProcesses.Policy.ENVIRONMENT, held).allowed());
        check(SimulationPolicy.process(EnvironmentProcesses.Policy.ENVIRONMENT, new SimulationPolicy.BlockFacts(true, false)).allowed());
        check(SimulationPolicy.process(EnvironmentProcesses.Policy.DIRECT, held).allowed());
        check(SimulationPolicy.process(EnvironmentProcesses.Policy.PASS_THROUGH, held).allowed());
        check(!SimulationPolicy.process(EnvironmentProcesses.Policy.ENVIRONMENT, new SimulationPolicy.BlockFacts(false, false)).allowed());
        var input = new InputPolicy.Facts(true, true, true, null, false);
        check(InputPolicy.movement(input).allowed());
        check(InputPolicy.gameplay(input).disposition() == GateDecision.Disposition.DENY);
        check(!InputPolicy.carriedSlot(input).allowed());
        check(!WorldOutcomePolicy.unscopedDamage(new WorldOutcomePolicy.IncomingFacts(true, false)).allowed());
        check(WorldOutcomePolicy.unscopedDamage(new WorldOutcomePolicy.IncomingFacts(true, true)).allowed());
        for (boolean current : new boolean[]{false,true}) for (boolean paused : new boolean[]{false,true})
            for (boolean same : new boolean[]{false,true}) {
                var impact = new WorldOutcomePolicy.EnvironmentImpact(current,paused,same);
                check(WorldOutcomePolicy.explosionImpact(impact).allowed() == (current && (!paused || same)));
                check(WorldOutcomePolicy.explosionDamage(impact).allowed() == (current && same));
            }
        var cloud = new WorldOutcomePolicy.CloudFacts(true,true,true,true,true,false);
        check(WorldOutcomePolicy.cloudSimulation(cloud).allowed());
        check(!WorldOutcomePolicy.cloudSimulation(new WorldOutcomePolicy.CloudFacts(true,true,true,true,false,false)).allowed());
        check(!WorldOutcomePolicy.cloudSimulation(new WorldOutcomePolicy.CloudFacts(true,true,true,true,true,true)).allowed());
        check(!WorldOutcomePolicy.cloudSimulation(new WorldOutcomePolicy.CloudFacts(false,false,false,false,true,false)).allowed());
        check(!WorldOutcomePolicy.cloudSimulation(new WorldOutcomePolicy.CloudFacts(true,true,false,false,true,false)).allowed());
        check(!WorldOutcomePolicy.cloudSimulation(new WorldOutcomePolicy.CloudFacts(true,false,false,true,true,false)).allowed());
        check(WorldOutcomePolicy.cloudSimulation(new WorldOutcomePolicy.CloudFacts(true,false,false,false,true,false)).allowed());
        check(WorldOutcomePolicy.cloudTarget(new WorldOutcomePolicy.CloudTargetFacts(true,true,true,true,true)).allowed());
        check(!WorldOutcomePolicy.cloudTarget(new WorldOutcomePolicy.CloudTargetFacts(true,true,true,false,true)).allowed());
        check(!WorldOutcomePolicy.cloudTarget(new WorldOutcomePolicy.CloudTargetFacts(true,true,false,true,true)).allowed());
        check(!WorldOutcomePolicy.cloudTarget(new WorldOutcomePolicy.CloudTargetFacts(false,false,false,false,false)).allowed());
        check(!WorldOutcomePolicy.cloudTarget(new WorldOutcomePolicy.CloudTargetFacts(true,false,false,false,true)).allowed());
        check(WorldOutcomePolicy.cloudTarget(new WorldOutcomePolicy.CloudTargetFacts(true,false,false,false,false)).allowed());
        check(!SimulationPreparation.advance().skipsNativeTick());
        for (var status : SimulationPreparation.Status.values())
            check(new SimulationPreparation(status,"TEST_RESULT").skipsNativeTick() == (status != SimulationPreparation.Status.ADVANCE));
        check(SimulationPreparation.scheduling(GateDecision.hold("ENVIRONMENT_STEP_REQUIRED")).status() == SimulationPreparation.Status.HELD);
        System.out.println("Control policy checks passed: subsystem isolation, stale decisions, environment classification, determinism");
    }
}
