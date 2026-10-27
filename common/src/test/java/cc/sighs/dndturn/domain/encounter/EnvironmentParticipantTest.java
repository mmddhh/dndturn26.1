package cc.sighs.dndturn.domain.encounter;

import java.util.*;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.spatial.GridCell;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentParticipantTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-1, -5, Integer.MIN_VALUE})
    void negativeInitiativeStillPrecedesTypedEnvironment(int initiative) {
        UUID id = UUID.randomUUID(), member = UUID.randomUUID();
        var engine = new EncounterAuthority(new Random(1), 7, 1);
        engine.beginCandidate(id, EncounterRegion.generate("test:world",
            new EncounterRegion.Discovery(-4,-4,-4,4,4,4),
            List.of(new EncounterRegion.Anchor(member, new EncounterRegion.Point(0,0,0))), 3, 1), Set.of(member));
        var saved = engine.exportSnapshot(); var s = saved.encounters().get(0); var m = s.members().get(0);
        var negative = new EncounterStateSnapshot.MemberState(m.id(), initiative, m.tieBreak(), m.eligibleRound(),
            m.movementTicks(), m.action(), m.reaction(), m.dodging(), m.disengaged(), m.preserveSpentActionOnNextTurn());
        var changed = new EncounterStateSnapshot.EncounterState(s.id(), s.bounds(), s.region(), s.phase(), s.version(),
            s.structuralRevision(), s.round(), s.cursor(), s.environmentRemaining(), s.authorizedEnvironmentStep(),
            s.environmentStepAuthorized(), s.boundaryInitiative(), s.boundaryTieBreak(), List.of(negative), s.hostile(),
            s.order(), s.history(), s.pending(), s.causes(), s.attackersWithRegisteredAttempt(), s.permits(),
            s.childrenByRoot(), s.completedEnvironmentSteps(), s.causalMergeNeighbors(), s.movementTicksPerTurn(), s.environmentTicks(), s.environmentTime());
        engine = EncounterAuthority.restoreSnapshot(new EncounterStateSnapshot(7, 1,
            List.of(changed), saved.closed(), saved.mergedInto(), saved.mergeReceipts()), new Random(1));
        engine.endTurn(id, member);
        UUID step = UUID.randomUUID(); engine.authorizeEnvironmentStep(id, step); engine.commitEnvironmentStep(id, step);
        assertEquals(List.of(member, TurnParticipant.environment(id).id()), engine.stateView(id).order());
        assertEquals(member, engine.stateView(id).current());
    }
    @Test void nativeCallbackRequiresCurrentEnvironmentStepAndFiniteTargetPermit() {
        var engine=new EncounterAuthority(new Random(1),30,3);
        UUID id=UUID.randomUUID(), actor=UUID.randomUUID(), source=UUID.randomUUID(), step=UUID.randomUUID();
        var region=EncounterRegion.generate("test:world",new EncounterRegion.Discovery(-4,-4,-4,4,4,4),
            List.of(new EncounterRegion.Anchor(actor,new EncounterRegion.Point(0,0,0))),3,1);
        engine.beginCandidate(id,region,Set.of(actor));
        engine.endTurn(id,actor);
        var cell=new GridCell(0,0,0);
        var environment=TurnParticipant.environment(id).id();
        var root=new OperationRecord.Snapshot(UUID.randomUUID(),null,id,environment,source,actor,
            1,engine.stateView(id).version(),cell,cell,OperationRecord.Kind.ENVIRONMENT);
        assertFalse(engine.beginEnvironmentEffect(root,step));
        engine.authorizeEnvironmentStep(id,step);
        root=new OperationRecord.Snapshot(root.operationId(),null,id,environment,source,actor,
            1,engine.stateView(id).version(),cell,cell,root.kind());
        assertFalse(engine.beginEnvironmentEffect(root,UUID.randomUUID()));
        assertTrue(engine.beginEnvironmentEffect(root,step));
        assertFalse(engine.beginEnvironmentEffect(root,step));
        UUID permit=UUID.randomUUID();
        engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permit,id,root.operationId(),environment,
            Set.of(actor),Set.of(EncounterPhase.ENVIRONMENT),1,engine.stateView(id).round()));
        var child=new OperationRecord.Snapshot(UUID.randomUUID(),root.operationId(),id,environment,environment,actor,
            1,engine.stateView(id).version(),cell,cell,OperationRecord.Kind.NATIVE_EFFECT);
        assertTrue(engine.beginOperation(child,permit));
        engine.publish(id,child.operationId(),0,OperationRecord.Outcome.COMPLETED,"native",0,0,true);
        engine.publish(id,root.operationId(),0,OperationRecord.Outcome.COMPLETED,"cloud",0,0,true);
        assertFalse(engine.beginEnvironmentEffect(root,step));
        assertEquals(0,engine.environmentTime(id));
        assertTrue(engine.commitEnvironmentStep(id,step));
        assertEquals(1,engine.environmentTime(id));
    }
    @Test void permanentLastSeatConsumesOneCapturedBudgetPerRoundAndSurvivesRestore() {
        UUID encounter = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        var region = EncounterRegion.generate("test:world", new EncounterRegion.Discovery(-4,-4,-4,4,4,4),
            List.of(new EncounterRegion.Anchor(first, new EncounterRegion.Point(0,0,0))), 3, 1);
        var engine = new EncounterAuthority(new Random(12), 7, 3);
        engine.beginCandidate(encounter, region, Set.of(first, second));
        var environment = TurnParticipant.environment(encounter);
        assertEquals(environment, engine.stateView(encounter).participants().get(2));
        assertEquals(2, engine.stateView(encounter).members().size());
        assertNull(engine.encounterOf(environment.id()));
        assertThrows(IllegalArgumentException.class, () -> engine.join(encounter, environment.id()));
        for (int round = 0; round < 3; round++) {
            for (int member = 0; member < 2; member++) {
                assertEquals(TurnParticipant.Kind.ENTITY, engine.stateView(encounter).currentParticipant().kind());
                engine.endTurn(encounter, engine.stateView(encounter).current());
                assertEquals(round, engine.stateView(encounter).round());
            }
            assertEquals(environment, engine.stateView(encounter).currentParticipant());
            assertEquals(3, engine.stateView(encounter).environmentRemaining());
            UUID cancelled = UUID.randomUUID();
            engine.authorizeEnvironmentStep(encounter, cancelled);
            engine.cancelEnvironmentStep(encounter, cancelled);
            assertEquals(round * 3L, engine.environmentTime(encounter));
            UUID step = UUID.randomUUID();
            engine.authorizeEnvironmentStep(encounter, step);
            engine.commitEnvironmentStep(encounter, step);
            assertFalse(engine.commitEnvironmentStep(encounter, step));
            var restored = EncounterAuthority.restoreSnapshot(engine.exportSnapshot(), new Random(14));
            assertEquals(environment, restored.stateView(encounter).currentParticipant());
            assertEquals(2, restored.stateView(encounter).environmentRemaining());
            assertEquals(round * 3L + 1, restored.environmentTime(encounter));
            for (int remaining = 2; remaining > 0; remaining--) {
                UUID next = UUID.randomUUID();
                engine.authorizeEnvironmentStep(encounter, next); engine.commitEnvironmentStep(encounter, next);
            }
            assertEquals(round + 1, engine.stateView(encounter).round());
        }
        engine.leave(encounter, first); engine.leave(encounter, second);
        assertFalse(engine.encounterIds().contains(encounter));
    }
}
