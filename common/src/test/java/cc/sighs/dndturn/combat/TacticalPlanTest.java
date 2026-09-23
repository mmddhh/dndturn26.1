package cc.sighs.dndturn.combat;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TacticalPlanTest {
    final UUID encounter = UUID.randomUUID(), actor = UUID.randomUUID();
    CombatEngine engine = new CombatEngine(new Random(4), 28, 20);
    TacticalPlanTest() {
        engine.beginCandidate(encounter, EncounterRegion.generate("overworld",
            new EncounterRegion.Discovery(0, 0, 0, 10, 10, 10),
            List.of(new EncounterRegion.Anchor(actor, new EncounterRegion.Point(5, 5, 5))), 5, 1), Set.of(actor));
    }
    OperationRecord.Snapshot plan() {
        var target = new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK, "overworld", null,
            new GridCell(5, 4, 5), 1, .5, 1, .5);
        var intent = new TacticalIntent(TacticalIntent.Capability.PLACE, target,
            new TacticalIntent.ItemReference(0, "stack-version-1"));
        return new OperationRecord.Snapshot(UUID.randomUUID(), null, encounter, actor, actor, null,
            0, engine.stateView(encounter).version(), null, target.cell(), OperationRecord.Kind.PLAN, null, intent);
    }
    OperationRecord.Snapshot child(OperationRecord.Snapshot plan, OperationRecord.Kind kind) {
        return new OperationRecord.Snapshot(UUID.randomUUID(), plan.operationId(), encounter, actor, actor, null,
            1, engine.stateView(encounter).version(), null, kind == OperationRecord.Kind.MOVE ? null : plan.targetCell(), kind);
    }
    @Test void rejectionSurvivesRestoreAndDoesNotGrantPermissionOrSpendResources() {
        var request = plan(); long version = engine.stateView(encounter).version();
        assertThrows(NullPointerException.class, () -> engine.rejectPlan(request, null));
        assertEquals(version, engine.stateView(encounter).version());
        var rejection = engine.rejectPlan(request, "adapter unavailable");
        assertEquals(OperationRecord.Outcome.REJECTED, rejection.outcome());
        assertTrue(engine.stateView(encounter).members().get(actor).action());
        assertNull(engine.pendingOperation(encounter, request.operationId()));
        engine = CombatEngine.restoreSnapshot(engine.exportSnapshot(), new Random(9));
        assertEquals(rejection, engine.resultFor(encounter, request.operationId()));
        assertEquals(rejection, engine.rejectPlan(request, "adapter unavailable"));
        var old = request.intent();
        var changed = new TacticalIntent("extension:other", 2, TacticalIntent.Hand.OFF_HAND, old.capability(), old.target(), old.item());
        var conflict = new OperationRecord.Snapshot(request.operationId(), null, encounter, actor, actor, null,
            request.serverTick(), request.encounterVersion(), request.sourceCell(), request.targetCell(), OperationRecord.Kind.PLAN, null, changed);
        assertThrows(IllegalStateException.class, () -> engine.rejectPlan(conflict, "adapter unavailable"));
    }
    @Test void behaviorIdentityIsPartOfImmutableRequest() {
        var original = plan().intent();
        assertNotEquals(original, new TacticalIntent("extension:place", 1, original.hand(), original.capability(), original.target(), original.item()));
        assertThrows(IllegalArgumentException.class, () -> new TacticalIntent("bad id", 1, original.hand(), original.capability(), original.target(), original.item()));
        assertThrows(IllegalArgumentException.class, () -> new TacticalIntent(original.behaviorId(), 0, original.hand(), original.capability(), original.target(), original.item()));
    }
    @Test void approachReservesActionAndUsesCurrentVersionWithoutActivatingCombat() {
        var plan = plan(); assertTrue(engine.beginOperation(plan));
        var move = child(plan, OperationRecord.Kind.MOVE);
        assertFalse(engine.beginOperation(move));
        assertTrue(engine.beginPlanStep(move));
        engine.publish(encounter, move.operationId(), 0, OperationRecord.Outcome.COMPLETED, "moved", 4, 0, true);
        assertEquals(24, engine.stateView(encounter).members().get(actor).movementTicks());
        assertTrue(engine.stateView(encounter).members().get(actor).action());
        assertEquals(EncounterPhase.CANDIDATE, engine.stateView(encounter).phase());
        assertFalse(engine.beginPlanStep(child(plan, OperationRecord.Kind.USE_ITEM)));
        var use = child(plan, OperationRecord.Kind.PLACE);
        assertTrue(engine.beginPlanStep(use));
        engine.publish(encounter, use.operationId(), 0, OperationRecord.Outcome.REJECTED, "protected", 0, 0, true);
        engine.publish(encounter, plan.operationId(), 0, OperationRecord.Outcome.REJECTED, "protected", 0, 0, true);
        assertTrue(engine.stateView(encounter).members().get(actor).action());
        assertFalse(engine.beginOperation(plan));
    }
    @Test void acceptedUseChargesOnceAndInvalidPublicationDoesNotPartiallyCommit() {
        var plan = plan(); assertTrue(engine.beginOperation(plan));
        var use = child(plan, OperationRecord.Kind.PLACE); assertTrue(engine.beginPlanStep(use));
        long version = engine.stateView(encounter).version();
        assertThrows(IllegalArgumentException.class, () -> engine.publish(encounter, use.operationId(), 0,
            OperationRecord.Outcome.ACCEPTED, "invalid", 0, Float.NaN, false));
        assertEquals(version, engine.stateView(encounter).version());
        assertTrue(engine.stateView(encounter).members().get(actor).action());
        var first = engine.publish(encounter, use.operationId(), 0, OperationRecord.Outcome.ACCEPTED, "started", 0, 0, false);
        assertSame(first, engine.publish(encounter, use.operationId(), 0, OperationRecord.Outcome.ACCEPTED, "started", 0, 0, false));
        assertFalse(engine.stateView(encounter).members().get(actor).action());
        engine.publish(encounter, use.operationId(), 1, OperationRecord.Outcome.INTERRUPTED, "cancelled", 0, 0, true);
        engine.publish(encounter, plan.operationId(), 0, OperationRecord.Outcome.INTERRUPTED, "cancelled", 0, 0, true);
        assertFalse(engine.stateView(encounter).members().get(actor).action());
    }
    @Test void recoveryAndExitCloseChildrenBeforeRootWithoutReplay() {
        var plan = plan(); assertTrue(engine.beginOperation(plan));
        var move = child(plan, OperationRecord.Kind.MOVE); assertTrue(engine.beginPlanStep(move));
        engine = CombatEngine.restoreSnapshot(engine.exportSnapshot(), new Random(9));
        assertEquals(2, engine.failRestoredWork(encounter, 20, "unconfirmed"));
        assertEquals(OperationRecord.Outcome.UNKNOWN, engine.resultFor(encounter, plan.operationId()).outcome());
        assertTrue(engine.stateView(encounter).members().get(actor).action());
        var second = plan(); assertTrue(engine.beginOperation(second));
        assertTrue(engine.beginPlanStep(child(second, OperationRecord.Kind.MOVE)));
        assertTrue(engine.leave(encounter, actor));
    }
    @Test void confirmedChildAndRootRemainTerminalAfterOwnerDeathAndRestore() {
        var root=plan(); assertTrue(engine.beginOperation(root));
        var action=child(root,OperationRecord.Kind.PLACE); assertTrue(engine.beginPlanStep(action));
        engine.publish(encounter,action.operationId(),0,OperationRecord.Outcome.COMPLETED,"confirmed effect",0,0,true);
        var result=engine.publish(encounter,root.operationId(),0,OperationRecord.Outcome.COMPLETED,"confirmed effect",0,0,true);
        engine.leave(encounter,actor);
        assertEquals(result,engine.resultFor(encounter,root.operationId()));
        engine=CombatEngine.restoreSnapshot(engine.exportSnapshot(),new Random(1));
        assertEquals(result,engine.resultFor(encounter,root.operationId()));
    }
    @Test void exceptionAfterUnconfirmedChildStaysUnknownOnLeaveAndRecovery() {
        var root=plan(); assertTrue(engine.beginOperation(root));
        var action=child(root,OperationRecord.Kind.PLACE); assertTrue(engine.beginPlanStep(action));
        engine.publish(encounter,action.operationId(),0,OperationRecord.Outcome.UNKNOWN,"world effect uncertain",0,0,true);
        engine.leave(encounter,actor);
        assertEquals(OperationRecord.Outcome.UNKNOWN,engine.resultFor(encounter,root.operationId()).outcome());
        engine=CombatEngine.restoreSnapshot(engine.exportSnapshot(),new Random(1));
        assertEquals(OperationRecord.Outcome.UNKNOWN,engine.resultFor(encounter,root.operationId()).outcome());
    }
    @Test void leavingBeforeWorldExecutionInterruptsPlanWithoutClaimingUnknownEffects() {
        var root=plan(); assertTrue(engine.beginOperation(root));
        engine.leave(encounter,actor);
        assertEquals(OperationRecord.Outcome.INTERRUPTED,engine.resultFor(encounter,root.operationId()).outcome());
    }
    @Test void planRejectsDifferentTargetAndRootCannotFinishBeforeChild() {
        var plan = plan(); assertTrue(engine.beginOperation(plan));
        var wrong = new OperationRecord.Snapshot(UUID.randomUUID(), plan.operationId(), encounter, actor, actor,
            null, 1, engine.stateView(encounter).version(), null, new GridCell(8, 4, 5), OperationRecord.Kind.PLACE);
        long version = engine.stateView(encounter).version();
        assertFalse(engine.beginPlanStep(wrong));
        assertEquals(version, engine.stateView(encounter).version());
        var move = child(plan, OperationRecord.Kind.MOVE); assertTrue(engine.beginPlanStep(move));
        version = engine.stateView(encounter).version();
        assertThrows(IllegalStateException.class, () -> engine.publish(encounter, plan.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "too early", 0, 0, true));
        assertEquals(version, engine.stateView(encounter).version());
        assertNotNull(engine.pendingOperation(encounter, move.operationId()));
    }

}
