package cc.sighs.dndturn.platform.server.action;
import cc.sighs.dndturn.domain.encounter.StateView;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import net.minecraft.world.entity.LivingEntity;

/** GameTest-only friend access. Never included in the production source set. */
public final class ActionTestAccess {
    private ActionTestAccess() {}
    public static EncounterAuthority authority(ActionExecutionCoordinator actions) { return actions.engine; }
    public static void validate(ActionExecutionCoordinator actions, LivingEntity actor, ActionIntent intent, StateView state) {
        actions.validate(actor, intent, state);
    }
    public static void verifyObservationIdentity(net.minecraft.gametest.framework.GameTestHelper helper,
            ActionExecutionCoordinator actions, LivingEntity actor,
            cc.sighs.dndturn.domain.encounter.operation.OperationRecord.Snapshot root) {
        var sample = AbilityObservations.capture(actions.actorStates.context(actor), root.intent(),
            java.util.List.of(), java.util.Set.of());
        for (boolean missing : new boolean[] {false, true}) {
            var execution = new ActionExecutionCoordinator.Execution(root, java.util.List.of(), root.targetCell(),
                "", actor.position(), missing ? ActionExecutionCoordinator.instance(actor) : java.util.UUID.randomUUID());
            execution.before = sample;
            execution.observed = sample;
            execution.phase = cc.sighs.dndturn.platform.server.checkpoint.AbilityCheckpoint.Phase.EXECUTE;
            actions.captureFinalObservation(missing ? null : actor, execution);
            helper.assertTrue(!execution.observationFailure.isEmpty() && execution.observed == sample,
                "unavailable/replaced owner overwrote prior observation or hid uncertainty");
            helper.assertTrue(execution.phase == cc.sighs.dndturn.platform.server.checkpoint.AbilityCheckpoint.Phase.EXECUTE,
                "shutdown sampling advanced the resumable phase");
        }
    }
}
