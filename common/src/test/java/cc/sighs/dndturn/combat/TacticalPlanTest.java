package cc.sighs.dndturn.combat;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TacticalPlanTest {
    @Test void itemImpactRequiresConfirmedLaunchAndEnvironmentAndDoesNotRecharge() {
        var template=plan(); var old=template.intent();
        var intent=new TacticalIntent("dndturn:egg",1,TacticalIntent.Capability.USE_ITEM,old.target(),old.source());
        var root=new OperationRecord.Snapshot(template.operationId(),null,encounter,actor,actor,null,0,
            engine.stateView(encounter).version(),null,old.target().cell(),OperationRecord.Kind.PLAN,null,intent);
        assertTrue(engine.beginOperation(root));
        var launch=child(root,OperationRecord.Kind.USE_ITEM); assertTrue(engine.beginPlanStep(launch));
        engine.publish(encounter,launch.operationId(),0,OperationRecord.Outcome.COMPLETED,"launched",0,0,true);
        engine.publish(encounter,root.operationId(),0,OperationRecord.Outcome.COMPLETED,"done",0,0,true);
        UUID projectile=UUID.randomUUID(),operation=UUID.randomUUID();
        var origin=new ProjectileOrigin(projectile,actor,launch.operationId(),encounter,"overworld",1,0,"minecraft:egg","minecraft:egg",true,true,false);
        var denied=new OperationRecord.Snapshot(operation,launch.operationId(),encounter,actor,projectile,null,2,
            engine.stateView(encounter).version(),null,null,OperationRecord.Kind.ENVIRONMENT);
        assertFalse(engine.beginCausalItemImpact(origin,denied));
        engine.endTurn(encounter,actor); UUID step=UUID.randomUUID();assertTrue(engine.authorizeEnvironmentStep(encounter,step));
        var accepted=new OperationRecord.Snapshot(operation,launch.operationId(),encounter,actor,projectile,null,2,
            engine.stateView(encounter).version(),null,null,OperationRecord.Kind.ENVIRONMENT);
        var forged=new ProjectileOrigin(projectile,actor,UUID.randomUUID(),encounter,"overworld",1,0,"minecraft:egg","minecraft:egg",true,true,false);
        var before=engine.stateView(encounter);
        assertFalse(engine.beginCausalItemImpact(forged,accepted)); assertEquals(before,engine.stateView(encounter));
        assertTrue(engine.beginCausalItemImpact(origin,accepted)); assertFalse(engine.beginCausalItemImpact(origin,accepted));
        assertThrows(IllegalStateException.class,()->engine.beginCausalItemImpact(origin,denied));
        engine.publish(encounter,operation,0,OperationRecord.Outcome.COMPLETED,"native effect",0,0,true);
        assertFalse(engine.stateView(encounter).members().get(actor).action());
        assertTrue(engine.commitEnvironmentStep(encounter,step));
        engine=CombatEngine.restoreSnapshot(engine.exportSnapshot(),new Random(4));
        assertEquals(launch.operationId(),engine.resultFor(encounter,operation).snapshot().parentId());
        assertFalse(engine.beginCausalItemImpact(origin,accepted));
    }
    @Test void selectedApproachIsPartOfCanonicalPayloadAndSurvivesRestore() {
        var base = plan();
        var approach = new TacticalIntent.Approach(UUID.randomUUID(), 0, new TacticalIntent.Point(1.5, .5, 2.5));
        var intent = base.intent().withApproach(approach);
        var root = new OperationRecord.Snapshot(base.operationId(), null, encounter, actor, actor, base.target(),
            base.serverTick(), base.encounterVersion(), base.sourceCell(), base.targetCell(), OperationRecord.Kind.PLAN, base.observationEpoch(), intent);
        assertTrue(engine.beginOperation(root));
        engine.publish(encounter, root.operationId(), 0, OperationRecord.Outcome.COMPLETED, "done", 0, 0, true);
        engine = CombatEngine.restoreSnapshot(engine.exportSnapshot(), new Random(2));
        assertEquals(approach, engine.resultFor(encounter, root.operationId()).snapshot().intent().approach());
        assertFalse(engine.beginOperation(root));
        var changed = new OperationRecord.Snapshot(root.operationId(), null, encounter, actor, actor, root.target(),
            root.serverTick(), root.encounterVersion(), root.sourceCell(), root.targetCell(), OperationRecord.Kind.PLAN, root.observationEpoch(),
            intent.withApproach(new TacticalIntent.Approach(approach.proposal(), 1, new TacticalIntent.Point(2.5, .5, 2.5))));
        assertThrows(IllegalStateException.class, () -> engine.beginOperation(changed));
    }
    @Test void previewPositionsRejectNonFiniteAndOutOfBoundsValues() {
        assertThrows(IllegalArgumentException.class, () -> new TacticalIntent.Point(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TacticalIntent.Point(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new TacticalIntent.Approach(UUID.randomUUID(), 256, new TacticalIntent.Point(0, 0, 0)));
    }
    @Test void structuredRejectionSurvivesRestoreAndCannotReuseIdWithNewPayload() {
        var root = plan();
        var details = ActionFailure.staleEncounter().details();
        var original = engine.rejectPlan(root, "本地化文案", details);
        assertEquals(details, original.failure());
        engine = CombatEngine.restoreSnapshot(engine.exportSnapshot(), new Random(2));
        assertEquals(original, engine.resultFor(encounter,root.operationId()));
        assertEquals(original,engine.rejectPlan(root,"different wording",ActionFailure.Details.REJECTED));
        assertFalse(engine.beginOperation(root));
        var changed = new OperationRecord.Snapshot(root.operationId(),null,encounter,actor,actor,null,0,
            root.encounterVersion()+1,null,root.targetCell(),OperationRecord.Kind.PLAN,null,root.intent());
        assertThrows(IllegalStateException.class,()->engine.rejectPlan(changed,"retry",details));
        assertTrue(engine.stateView(encounter).members().get(actor).action());
    }
    @Test void unknownResultRequiresReviewIndependentOfDisplayText() {
        var root = plan(); assertTrue(engine.beginOperation(root));
        var result = engine.publish(encounter,root.operationId(),0,OperationRecord.Outcome.UNKNOWN,"localized",0,0,true);
        assertEquals(ActionFailure.Details.UNKNOWN,result.failure());
        assertFalse(engine.beginOperation(root));
    }
    @Test void releaseFailurePreservesConfirmedEffectAndSpentActionAcrossRestore() {
        var root = plan(); assertTrue(engine.beginOperation(root));
        var action = child(root, OperationRecord.Kind.PLACE); assertTrue(engine.beginPlanStep(action));
        engine.publish(encounter, action.operationId(), 0, OperationRecord.Outcome.COMPLETED, "placed", 0, 0, true);
        var evidence = new ExecutionConclusion(OperationRecord.Outcome.COMPLETED,
            ExecutionConclusion.Release.FAILED, "release adapter threw");
        var result = engine.publish(encounter, root.operationId(), 0, evidence.outcome(), "placed", 0, 0, true, null, evidence);
        assertEquals(OperationRecord.Outcome.COMPLETED, result.outcome());
        assertFalse(engine.stateView(encounter).members().get(actor).action());
        engine = CombatEngine.restoreSnapshot(engine.exportSnapshot(), new Random(9));
        assertEquals(result, engine.resultFor(encounter, root.operationId()));
        assertSame(engine.resultFor(encounter, root.operationId()), engine.publish(encounter, root.operationId(), 0,
            evidence.outcome(), "placed", 0, 0, true, null, evidence));
        var corrected = new ExecutionConclusion(OperationRecord.Outcome.COMPLETED, ExecutionConclusion.Release.RELEASED, "");
        assertThrows(IllegalStateException.class, () -> engine.publish(encounter, root.operationId(), 0,
            corrected.outcome(), "placed", 0, 0, true, null, corrected));
    }
    @Test void unknownEffectDoesNotBecomeKnownWhenReleaseSucceeds() {
        assertEquals(OperationRecord.Outcome.UNKNOWN, new ExecutionConclusion(OperationRecord.Outcome.UNKNOWN,
            ExecutionConclusion.Release.RELEASED, "").outcome());
        for (var effect : OperationRecord.Outcome.values()) {
            if (effect == OperationRecord.Outcome.ACCEPTED) continue;
            for (var release : ExecutionConclusion.Release.values())
                assertEquals(effect, new ExecutionConclusion(effect, release, "release evidence").outcome());
        }
    }
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
        var intent = new TacticalIntent("dndturn:place", 1, TacticalIntent.Capability.PLACE, target, cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND, new TacticalIntent.ItemReference(0, "stack-version-1")));
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
        var changed = new TacticalIntent("extension:other", 2, old.capability(), old.target(), cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.OFF_HAND, old.item()));
        var conflict = new OperationRecord.Snapshot(request.operationId(), null, encounter, actor, actor, null,
            request.serverTick(), request.encounterVersion(), request.sourceCell(), request.targetCell(), OperationRecord.Kind.PLAN, null, changed);
        assertThrows(IllegalStateException.class, () -> engine.rejectPlan(conflict, "adapter unavailable"));
    }
    @Test void behaviorIdentityIsPartOfImmutableRequest() {
        var original = plan().intent();
        assertNotEquals(original, new TacticalIntent("extension:place", 1, original.capability(), original.target(), cc.sighs.dndturn.combat.AbilitySource.equipment(original.hand(), original.item())));
        assertThrows(IllegalArgumentException.class, () -> new TacticalIntent("bad id", 1, original.capability(), original.target(), cc.sighs.dndturn.combat.AbilitySource.equipment(original.hand(), original.item())));
        assertThrows(IllegalArgumentException.class, () -> new TacticalIntent(original.behaviorId(), 0, original.capability(), original.target(), cc.sighs.dndturn.combat.AbilitySource.equipment(original.hand(), original.item())));
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
