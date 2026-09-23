package cc.sighs.dndturn.domain.encounter;

import java.util.*;
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
