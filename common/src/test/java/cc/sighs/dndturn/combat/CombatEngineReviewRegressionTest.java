package cc.sighs.dndturn.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEngineReviewRegressionTest {
    private static UUID id(long value) { return new UUID(0, value); }

    private static EncounterRegion region(double x) {
        var discovery = new EncounterRegion.Discovery(x - 10, -20, -10, x + 20, 40, 20);
        return EncounterRegion.generate("overworld", discovery,
            List.of(new EncounterRegion.Anchor(id(90), new EncounterRegion.Point(x, 0, 0))), 2, 1);
    }

    private static EncounterRegion mergedRegion() {
        var discovery = new EncounterRegion.Discovery(-10, -20, -10, 26, 40, 20);
        return EncounterRegion.generate("overworld", discovery, List.of(
            new EncounterRegion.Anchor(id(90), new EncounterRegion.Point(0, 0, 0)),
            new EncounterRegion.Anchor(id(91), new EncounterRegion.Point(3, 0, 0)),
            new EncounterRegion.Anchor(id(92), new EncounterRegion.Point(6, 0, 0))), 2, 2);
    }

    private static EncounterRegion projectedRegion(long version, double... positions) {
        List<EncounterRegion.Anchor> anchors = new java.util.ArrayList<>();
        for (int i = 0; i < positions.length; i++)
            anchors.add(new EncounterRegion.Anchor(id(1000 + i),
                new EncounterRegion.Point(positions[i], 0, 0)));
        return EncounterRegion.generate("overworld",
            new EncounterRegion.Discovery(-10, -20, -10, 30, 40, 20), anchors, 2, version);
    }

    private record Fixture(CombatEngine engine, UUID encounter, UUID owner, UUID target) {}

    @Test
    void restoredEncountersRetainBudgetsWhileNewSessionsCaptureNewConfiguration() {
        CombatEngine original = new CombatEngine(new Random(3), 28, 20);
        original.beginCandidate(id(100), region(0), Set.of(id(1), id(3)), 7, 3);
        CombatEngine engine = CombatEngine.restoreSnapshot(original.exportSnapshot(), new Random(4));
        engine.beginCandidate(id(200), region(20), Set.of(id(2), id(4)), 19, 2);
        assertEquals(7, engine.movementTicksPerTurn(id(100)));
        assertEquals(19, engine.movementTicksPerTurn(id(200)));
        for (UUID encounter : List.of(id(100), id(200))) {
            List<UUID> members = engine.view(encounter).order();
            engine.setHostile(encounter, members.get(0), members.get(1), true);
            engine.endTurn(encounter, engine.view(encounter).current());
            UUID owner = engine.view(encounter).current();
            int budget = engine.movementTicksPerTurn(encounter);
            var dash = new OperationRecord.Snapshot(UUID.randomUUID(), null, encounter,
                owner, owner, null, 1, engine.view(encounter).version(), null, null, OperationRecord.Kind.DASH);
            assertTrue(engine.beginOperation(dash));
            engine.publish(encounter, dash.operationId(), 0, OperationRecord.Outcome.COMPLETED,
                "dash", 0, 0, true);
            assertEquals(budget * 2, engine.stateView(encounter).members().get(owner).movementTicks());
            engine.endTurn(encounter, owner);
            while (engine.stateView(encounter).phase() != EncounterPhase.ENVIRONMENT)
                engine.endTurn(encounter, engine.view(encounter).current());
            int environment = engine.environmentTicks(encounter);
            assertEquals(environment, engine.stateView(encounter).environmentRemaining());
            for (int i = 0; i < environment; i++) {
                UUID step = UUID.randomUUID();
                engine.authorizeEnvironmentStep(encounter, step);
                engine.commitEnvironmentStep(encounter, step);
            }
            assertEquals(budget, engine.stateView(encounter).members().get(owner).movementTicks());
        }
    }

    @Test
    void environmentWithOnlyNextRoundMembersSurvivesRestoreAndAdvances() {
        CombatEngine engine = new CombatEngine(new Random(3), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)));
        engine.join(id(100), id(2));
        engine.leave(id(100), id(1));
        var saved = engine.exportSnapshot();
        assertEquals(EncounterPhase.ENVIRONMENT, engine.stateView(id(100)).phase());
        assertTrue(saved.encounters().get(0).order().isEmpty());
        CombatEngine restored = CombatEngine.restoreSnapshot(saved, new Random(4));
        assertEquals(saved, restored.exportSnapshot());
        assertNull(restored.view(id(100)).current());
        for (int step = 0; step < 2; step++) {
            UUID operationId = id(500 + step);
            engine.authorizeEnvironmentStep(id(100), operationId);
            restored.authorizeEnvironmentStep(id(100), operationId);
            engine.commitEnvironmentStep(id(100), operationId);
            restored.commitEnvironmentStep(id(100), operationId);
        }
        assertEquals(id(2), restored.view(id(100)).current());
        assertEquals(engine.exportSnapshot(), restored.exportSnapshot());
    }

    @Test
    void mergePreservesCurrentResourcesAndUsesPrimaryBudgetsForFutureTurns() {
        CombatEngine engine = new CombatEngine(new Random(3), 28, 20);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)), 7, 3);
        engine.beginCandidate(id(200), region(3), Set.of(id(2)), 19, 5);
        var plan = engine.planMerge(id(100));
        int movement = engine.movementTicksPerTurn(plan.primary());
        int environment = engine.environmentTicks(plan.primary());
        engine.endTurn(plan.primary(), engine.view(plan.primary()).current());
        engine.commitMerge(plan, projectedRegion(2, 0, 3), id(800), 1);
        assertEquals(7, engine.stateView(plan.primary()).members().get(id(1)).movementTicks());
        assertEquals(19, engine.stateView(plan.primary()).members().get(id(2)).movementTicks());
        engine = CombatEngine.restoreSnapshot(engine.exportSnapshot(), new Random(4));
        for (int i = 0; i < environment; i++) {
            UUID step = id(810 + i);
            engine.authorizeEnvironmentStep(plan.primary(), step);
            engine.commitEnvironmentStep(plan.primary(), step);
        }
        UUID first = engine.view(plan.primary()).current();
        assertEquals(movement, engine.stateView(plan.primary()).members().get(first).movementTicks());
        engine.endTurn(plan.primary(), first);
        UUID next = engine.view(plan.primary()).current();
        assertNotEquals(first, next);
        assertEquals(movement, engine.stateView(plan.primary()).members().get(next).movementTicks());
    }

    @Test
    void restoreRejectsEmptyActionQueueInvalidCursorAndPrematureEligibility() {
        CombatEngine engine = new CombatEngine(new Random(3), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)));
        engine.join(id(100), id(2));
        engine.leave(id(100), id(1));
        CombatStateSnapshot saved = engine.exportSnapshot();
        var state = saved.encounters().get(0);
        for (var broken : List.of(
            withTurn(state, EncounterPhase.CANDIDATE, 0, List.of()),
            withTurn(state, EncounterPhase.ENVIRONMENT, 1, List.of()),
            withTurn(state, EncounterPhase.ENVIRONMENT, 0, List.of(id(2))))) {
            var corrupt = new CombatStateSnapshot(saved.schemaVersion(), saved.movementTicksPerTurn(),
                saved.environmentTicks(), List.of(broken), saved.closed(), saved.mergedInto(), saved.mergeReceipts());
            assertThrows(IllegalArgumentException.class,
                () -> CombatEngine.restoreSnapshot(corrupt, new Random(4)));
        }
        CombatEngine eligible = new CombatEngine(new Random(3), 28, 2);
        eligible.beginCandidate(id(100), region(0), Set.of(id(1)));
        eligible.endTurn(id(100), id(1));
        var original = eligible.exportSnapshot();
        var missing = withTurn(original.encounters().get(0), EncounterPhase.ENVIRONMENT, 0, List.of());
        assertThrows(IllegalArgumentException.class, () -> CombatEngine.restoreSnapshot(
            new CombatStateSnapshot(original.schemaVersion(), 28, 2, List.of(missing),
                original.closed(), original.mergedInto(), original.mergeReceipts()), new Random(4)));
    }

    private static CombatStateSnapshot.EncounterState withTurn(CombatStateSnapshot.EncounterState s,
                                                               EncounterPhase phase, int cursor,
                                                               List<UUID> order) {
        return new CombatStateSnapshot.EncounterState(s.id(), s.bounds(), s.region(), phase,
            s.version(), s.structuralRevision(), s.round(), cursor, s.environmentRemaining(),
            s.authorizedEnvironmentStep(), s.environmentStepAuthorized(), s.boundaryInitiative(),
            s.boundaryTieBreak(), s.members(), s.hostile(), order, s.history(), s.pending(), s.causes(),
            s.attackersWithRegisteredAttempt(), s.permits(), s.childrenByRoot(),
            s.completedEnvironmentSteps(), s.causalMergeNeighbors(), s.movementTicksPerTurn(), s.environmentTicks());
    }

    @Test
    void projectileCausalityConnectsDisjointSessionsWithoutChangingTheirFixedRegions() {
        CombatEngine engine = new CombatEngine(new Random(3), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)));
        engine.beginCandidate(id(200), region(20), Set.of(id(2)));
        assertThrows(IllegalStateException.class, () -> engine.planMerge(id(100)));
        var plan = engine.requestCausalMerge(id(100), id(200));
        assertEquals(Set.of(id(100), id(200)), plan.encounters());
        assertEquals(region(0).anchors().get(0).center(), engine.stateView(id(100)).region().anchors().get(0).center());
        assertEquals(plan.encounters(), engine.requestCausalMerge(id(100), id(200)).encounters());
        engine.end(id(200));
        assertThrows(IllegalStateException.class, () -> engine.planMerge(id(100)));
    }

    @Test
    void restoreRejectsOneSidedCausalMergeLink() {
        CombatEngine engine = new CombatEngine(new Random(3), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)));
        engine.beginCandidate(id(200), region(20), Set.of(id(2)));
        engine.requestCausalMerge(id(100), id(200));
        CombatStateSnapshot saved = engine.exportSnapshot();
        assertEquals(Set.of(id(100), id(200)),
            CombatEngine.restoreSnapshot(saved, new Random(4)).planMerge(id(100)).encounters());
        var original = saved.encounters().stream()
            .filter(state -> state.id().equals(id(200))).findFirst().orElseThrow();
        var broken = new CombatStateSnapshot.EncounterState(original.id(), original.bounds(),
            original.region(), original.phase(), original.version(), original.structuralRevision(),
            original.round(), original.cursor(), original.environmentRemaining(),
            original.authorizedEnvironmentStep(), original.environmentStepAuthorized(),
            original.boundaryInitiative(), original.boundaryTieBreak(), original.members(),
            original.hostile(), original.order(), original.history(), original.pending(),
            original.causes(), original.attackersWithRegisteredAttempt(), original.permits(),
            original.childrenByRoot(), original.completedEnvironmentSteps(), Set.of(),
            original.movementTicksPerTurn(), original.environmentTicks());
        var corrupt = new CombatStateSnapshot(saved.schemaVersion(), saved.movementTicksPerTurn(),
            saved.environmentTicks(), saved.encounters().stream()
                .map(state -> state.id().equals(id(200)) ? broken : state).toList(),
            saved.closed(), saved.mergedInto(), saved.mergeReceipts());
        assertThrows(IllegalArgumentException.class,
            () -> CombatEngine.restoreSnapshot(corrupt, new Random(5)));
    }

    private static Fixture fixture(boolean active) {
        CombatEngine engine = new CombatEngine(new Random(3), 28, 2);
        UUID encounter = id(100);
        engine.beginCandidate(encounter, region(0), Set.of(id(1), id(2)));
        if (active) {
            engine.setHostile(encounter, id(1), id(2), true);
            engine.endTurn(encounter, engine.view(encounter).current());
        }
        UUID owner = engine.view(encounter).current();
        return new Fixture(engine, encounter, owner, owner.equals(id(1)) ? id(2) : id(1));
    }

    private static OperationRecord.Snapshot operation(Fixture f, long number, OperationRecord.Kind kind,
                                                       UUID parent, UUID owner, UUID source, UUID target) {
        return new OperationRecord.Snapshot(id(number), parent, f.encounter(), owner, source, target,
            1, f.engine().view(f.encounter()).version(), null, null, kind);
    }

    private static OperationRecord.Snapshot attack(Fixture f) {
        var attack = operation(f, 200, OperationRecord.Kind.ATTACK, null,
            f.owner(), f.owner(), f.target());
        assertTrue(f.engine().beginOperation(attack));
        return attack;
    }

    private static void finish(Fixture f, UUID operationId) {
        f.engine().publish(f.encounter(), operationId, 0, OperationRecord.Outcome.COMPLETED,
            "observed", 0, 0, true);
    }

    @Test
    void firstAttackIsConsumedByAcceptedAttemptAndResetsForNewEncounter() {
        Fixture f = fixture(false);
        assertFalse(f.engine().hasAttemptedAttack(f.encounter(), f.owner()));
        var invalid = operation(f, 199, OperationRecord.Kind.ATTACK, null,
            f.owner(), f.owner(), null);
        assertFalse(f.engine().beginOperation(invalid));
        assertFalse(f.engine().hasAttemptedAttack(f.encounter(), f.owner()));

        var first = attack(f);
        assertTrue(f.engine().hasAttemptedAttack(f.encounter(), f.owner()));
        assertFalse(f.engine().beginOperation(first));
        f.engine().publish(f.encounter(), first.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "miss", 0, 0, true);
        assertTrue(f.engine().hasAttemptedAttack(f.encounter(), f.owner()));

        f.engine().end(f.encounter());
        UUID nextEncounter = id(101);
        f.engine().beginCandidate(nextEncounter, region(100), Set.of(f.owner(), f.target()));
        assertFalse(f.engine().hasAttemptedAttack(nextEncounter, f.owner()));
    }

    @Test
    void candidateReviewCannotSkipExecutingEndTurnAndCompletedEndTurnAdvancesOnce() {
        Fixture f = fixture(false);
        var end = operation(f, 200, OperationRecord.Kind.END_TURN, null,
            f.owner(), f.owner(), null);
        assertTrue(f.engine().beginOperation(end));
        var before = f.engine().view(f.encounter());
        assertThrows(IllegalStateException.class, () -> f.engine().endTurn(f.encounter(), f.owner()));
        assertEquals(before, f.engine().view(f.encounter()));
        finish(f, end.operationId());
        assertEquals(f.target(), f.engine().view(f.encounter()).current());
        assertEquals(before.version() + 1, f.engine().view(f.encounter()).version());
    }

    @Test
    void candidateAttackActivatesAndItsDamageNeedsAnExplicitPermit() {
        Fixture f = fixture(false);
        var attack = attack(f);
        assertEquals(EncounterPhase.ACTIVE, f.engine().view(f.encounter()).phase());
        assertFalse(f.engine().view(f.encounter()).members().get(f.owner()).action());
        var damage = operation(f, 201, OperationRecord.Kind.DAMAGE, attack.operationId(),
            f.owner(), f.owner(), f.target());
        assertFalse(f.engine().beginOperation(damage));
        UUID permitId = id(300);
        f.engine().issueEffectPermit(new CombatEngine.EffectPermit(permitId, f.encounter(),
            attack.operationId(), f.owner(), Set.of(f.target()),
            Set.of(EncounterPhase.ACTIVE), 1, f.engine().view(f.encounter()).round()));
        damage = operation(f, 201, OperationRecord.Kind.DAMAGE, attack.operationId(),
            f.owner(), f.owner(), f.target());
        assertTrue(f.engine().beginOperation(damage, permitId));
        assertFalse(f.engine().beginOperation(attack));
    }

    @Test
    void candidateAttackCompletesWithoutPuttingLowInitiativeAttackerFirst() {
        CombatEngine engine = new CombatEngine(new Random() {
            private final int[] rolls = {19, 0, 11, 15};
            private int index;
            @Override public int nextInt(int bound) { return rolls[index++]; }
            @Override public int nextInt() { return 0; }
        }, 28, 2);
        UUID encounter = id(100), attacker = id(1), opponent = id(2), attackId = id(200);
        engine.beginCandidate(encounter, region(0), Set.of(attacker, opponent));
        assertEquals(attacker, engine.stateView(encounter).current());
        var attack = new OperationRecord.Snapshot(attackId, null, encounter, attacker, attacker,
            opponent, 1, engine.stateView(encounter).version(), null, null,
            OperationRecord.Kind.ATTACK);
        assertTrue(engine.beginOperation(attack));
        assertEquals(opponent, engine.stateView(encounter).current());
        assertEquals(List.of(opponent, attacker), engine.stateView(encounter).order());
        assertFalse(engine.stateView(encounter).members().get(attacker).action());
        assertEquals(OperationRecord.Outcome.COMPLETED, engine.publish(encounter, attackId, 0,
            OperationRecord.Outcome.COMPLETED, "miss", 0, 0, true).outcome());
        assertEquals(opponent, engine.stateView(encounter).current());
        engine.endTurn(encounter, opponent);
        assertEquals(attacker, engine.stateView(encounter).current());
        assertFalse(engine.stateView(encounter).members().get(attacker).action());
    }

    @Test
    void leavingDuringEnvironmentDoesNotRestartItsBudget() {
        CombatEngine engine = new CombatEngine(new Random(1), 28, 2);
        UUID encounter = id(100);
        engine.beginCandidate(encounter, region(0), Set.of(id(1)));
        engine.join(encounter, id(2));
        engine.endTurn(encounter, id(1));
        assertThrows(NullPointerException.class, () -> engine.cancelEnvironmentStep(encounter, null));
        assertTrue(engine.authorizeEnvironmentStep(encounter, id(400)));
        assertTrue(engine.cancelEnvironmentStep(encounter, id(400)));
        assertEquals(EncounterPhase.ENVIRONMENT, engine.view(encounter).phase());
        assertThrows(IllegalStateException.class, () -> engine.commitEnvironmentStep(encounter, id(401)));
        assertTrue(engine.authorizeEnvironmentStep(encounter, id(401)));
        assertTrue(engine.commitEnvironmentStep(encounter, id(401)));
        engine.leave(encounter, id(1));
        assertTrue(engine.authorizeEnvironmentStep(encounter, id(402)));
        assertTrue(engine.commitEnvironmentStep(encounter, id(402)));
        assertEquals(EncounterPhase.CANDIDATE, engine.view(encounter).phase());
        assertEquals(id(2), engine.view(encounter).current());
        assertFalse(engine.commitEnvironmentStep(encounter, id(401)));
    }

    @Test
    void terminalAndClosedResultsRejectConflictingObservations() {
        Fixture f = fixture(true);
        var attack = attack(f);
        var finalResult = f.engine().publish(f.encounter(), attack.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "hit", 0, 1.5f, true);
        assertSame(finalResult, f.engine().publish(f.encounter(), attack.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "hit", 0, 1.5f, true));
        assertThrows(IllegalStateException.class, () -> f.engine().publish(f.encounter(),
            attack.operationId(), 0, OperationRecord.Outcome.COMPLETED, "hit", 0, 9.5f, true));
        assertThrows(IllegalStateException.class, () -> f.engine().publish(f.encounter(),
            attack.operationId(), 1, OperationRecord.Outcome.COMPLETED, "hit", 0, 1.5f, true));
        f.engine().end(f.encounter());
        assertSame(finalResult, f.engine().publish(f.encounter(), attack.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "hit", 0, 1.5f, true));
        var page = f.engine().resultPage(f.encounter(), 0, 1);
        assertEquals(List.of(finalResult), page.results());
        assertEquals(1, page.nextIndex());
        assertThrows(IllegalStateException.class, () -> f.engine().publish(f.encounter(),
            attack.operationId(), 0, OperationRecord.Outcome.COMPLETED, "hit", 0, 9.5f, true));
    }

    @Test
    void delayedProjectileKeepsCauseAfterParentCompletionAndOwnerDeparture() {
        Fixture f = fixture(true);
        var attack = attack(f);
        UUID projectile = id(99), permitId = id(300);
        f.engine().issueEffectPermit(new CombatEngine.EffectPermit(permitId, f.encounter(),
            attack.operationId(), projectile, Set.of(f.target()),
            Set.of(EncounterPhase.ACTIVE, EncounterPhase.ENVIRONMENT), 1, Long.MAX_VALUE));
        finish(f, attack.operationId());
        f.engine().endTurn(f.encounter(), f.owner());
        f.engine().endTurn(f.encounter(), f.target());
        f.engine().leave(f.encounter(), f.owner());
        assertEquals(EncounterPhase.ENVIRONMENT, f.engine().view(f.encounter()).phase());
        var damage = operation(f, 201, OperationRecord.Kind.DAMAGE, attack.operationId(),
            f.owner(), projectile, f.target());
        assertFalse(f.engine().beginOperation(damage));
        assertTrue(f.engine().beginOperation(damage, permitId));
        assertEquals(f.owner(), f.engine().publish(f.encounter(), damage.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "impact", 0, 2.5f, true).snapshot().owner());
        var second = operation(f, 202, OperationRecord.Kind.DAMAGE, attack.operationId(),
            f.owner(), projectile, f.target());
        assertFalse(f.engine().beginOperation(second, permitId));
    }

    @Test
    void permitAllowsOnlyNamedSecondaryTargetsAndRetainedGrandchildLineage() {
        Fixture f = fixture(true);
        f.engine().join(f.encounter(), id(3));
        var attack = attack(f);
        UUID firstPermit = id(301);
        f.engine().issueEffectPermit(new CombatEngine.EffectPermit(firstPermit, f.encounter(),
            attack.operationId(), id(99), Set.of(f.target(), id(3)),
            Set.of(EncounterPhase.ACTIVE), 2, f.engine().view(f.encounter()).round()));
        var child = operation(f, 201, OperationRecord.Kind.DAMAGE, attack.operationId(),
            f.owner(), id(99), id(3));
        assertTrue(f.engine().beginOperation(child, firstPermit));
        UUID grandchildPermit = id(302);
        f.engine().issueEffectPermit(new CombatEngine.EffectPermit(grandchildPermit, f.encounter(),
            child.operationId(), id(98), Set.of(f.target()),
            Set.of(EncounterPhase.ACTIVE), 1, f.engine().view(f.encounter()).round()));
        finish(f, attack.operationId());
        finish(f, child.operationId());
        var grandchild = operation(f, 202, OperationRecord.Kind.DAMAGE, child.operationId(),
            f.owner(), id(98), f.target());
        assertTrue(f.engine().beginOperation(grandchild, grandchildPermit));
        var wrongTarget = operation(f, 203, OperationRecord.Kind.DAMAGE, attack.operationId(),
            f.owner(), id(99), id(4));
        assertFalse(f.engine().beginOperation(wrongTarget, firstPermit));
    }

    @Test
    void primaryDomainKeepsInitiativeAtEnvironmentBoundary() {
        CombatEngine engine = new CombatEngine(new Random() {
            private int n;
            @Override public int nextInt(int bound) { return n++ == 0 ? 1 : bound == 20 ? 18 : 0; }
        }, 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)));
        engine.beginCandidate(id(101), region(0), Set.of(id(2)));
        UUID primary = engine.primaryDomain(id(100), id(101));
        engine.endTurn(id(100), id(1));
        engine.endTurn(id(101), id(2));
        assertEquals(primary, engine.primaryDomain(id(100), id(101)));
    }

    @Test
    void mergePlanCapturesWholeConnectedComponentWithoutRequiringEveryPairToOverlap() {
        CombatEngine engine = new CombatEngine(new Random(7), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)));
        engine.beginCandidate(id(101), region(3), Set.of(id(2)));
        engine.beginCandidate(id(102), region(6), Set.of(id(3)));
        assertThrows(IllegalArgumentException.class, () -> engine.primaryDomain(id(100), id(102)));
        var plan = engine.planMerge(id(100));
        assertEquals(Set.of(id(100), id(101), id(102)), plan.encounters());
        assertEquals(3, plan.versions().size());
        assertTrue(plan.encounters().contains(plan.primary()));
    }

    @Test
    void originalResultIdentitySurvivesTwoSuccessiveMergesAndOwnerDeparture() {
        CombatEngine engine = new CombatEngine(new Random(7), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1), id(2)));
        engine.beginCandidate(id(101), region(3), Set.of(id(3), id(4)));
        var firstPlan = engine.planMerge(id(100));
        UUID originalSource = firstPlan.encounters().stream()
            .filter(value -> !value.equals(firstPlan.primary())).findFirst().orElseThrow();
        UUID owner = engine.stateView(originalSource).current();
        UUID target = engine.stateView(originalSource).members().keySet().stream()
            .filter(value -> !value.equals(owner)).findFirst().orElseThrow();
        var snapshot = new OperationRecord.Snapshot(id(450), null, originalSource,
            owner, owner, target, 10, engine.stateView(originalSource).version(),
            null, null, OperationRecord.Kind.ATTACK);
        assertTrue(engine.beginOperation(snapshot));
        var original = engine.publish(originalSource, snapshot.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "miss", 0, 0, true);
        while (engine.stateView(firstPlan.primary()).phase() != EncounterPhase.ENVIRONMENT) {
            var state = engine.stateView(firstPlan.primary());
            engine.endTurn(firstPlan.primary(), state.current());
        }
        engine.commitMerge(firstPlan, projectedRegion(2, 0, 3), id(451), 20);
        engine.beginCandidate(id(102), region(6), Set.of(id(5), id(6)));
        var secondPlan = engine.planMerge(firstPlan.primary());
        assertEquals(Set.of(firstPlan.primary(), id(102)), secondPlan.encounters());
        while (engine.stateView(secondPlan.primary()).phase() != EncounterPhase.ENVIRONMENT) {
            var state = engine.stateView(secondPlan.primary());
            engine.endTurn(secondPlan.primary(), state.current());
        }
        engine.commitMerge(secondPlan, projectedRegion(3, 0, 3, 6), id(452), 21);
        assertEquals(secondPlan.primary(), engine.canonicalEncounterId(originalSource));
        assertEquals(original, engine.resultFor(originalSource, snapshot.operationId()));
        assertEquals(original, engine.resultFor(secondPlan.primary(), snapshot.operationId()));
        engine.leave(secondPlan.primary(), owner);
        assertEquals(original, engine.resultFor(originalSource, snapshot.operationId()));
    }

    @Test
    void resampledRegionFindsNewOverlapBeforeAnyMigration() {
        CombatEngine engine = new CombatEngine(new Random(7), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)));
        engine.beginCandidate(id(101), region(3), Set.of(id(2)));
        engine.beginCandidate(id(102), region(11), Set.of(id(3)));
        var initial = engine.planMerge(id(100));
        assertEquals(Set.of(id(100), id(101)), initial.encounters());
        var firstSample = projectedRegion(2, 0, 3, 8);
        var expanded = engine.planMergeProjected(initial.primary(), firstSample);
        assertEquals(Set.of(id(100), id(101), id(102)), expanded.encounters());
        while (engine.stateView(initial.primary()).phase() != EncounterPhase.ENVIRONMENT) {
            var state = engine.stateView(initial.primary());
            engine.endTurn(initial.primary(), state.current());
        }
        var oldState = engine.stateView(initial.primary());
        assertThrows(IllegalStateException.class,
            () -> engine.commitMerge(initial, firstSample, id(901), 20));
        assertEquals(oldState, engine.stateView(initial.primary()));
        assertEquals(3, engine.encounterIds().size());
        while (engine.stateView(expanded.primary()).phase() != EncounterPhase.ENVIRONMENT) {
            var state = engine.stateView(expanded.primary());
            engine.endTurn(expanded.primary(), state.current());
        }
        var finalSample = projectedRegion(3, 0, 3, 11);
        assertEquals(OperationRecord.Outcome.COMPLETED,
            engine.commitMerge(expanded, finalSample, id(902), 21).outcome());
        assertEquals(1, engine.encounterIds().size());
    }

    @Test
    void mergeCommitKeepsCapturedPrimaryAndMigratesSpentStateAndOldResults() {
        CombatEngine engine = new CombatEngine(new Random(7), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1), id(2)));
        engine.beginCandidate(id(101), region(3), Set.of(id(3), id(4)));
        engine.beginCandidate(id(102), region(6), Set.of(id(5), id(6)));
        var plan = engine.planMerge(id(100));
        UUID source = plan.encounters().stream().filter(value -> !value.equals(plan.primary()))
            .sorted().findFirst().orElseThrow();
        UUID owner = engine.stateView(source).current();
        UUID target = engine.stateView(source).members().keySet().stream()
            .filter(value -> !value.equals(owner)).findFirst().orElseThrow();
        var attack = new OperationRecord.Snapshot(id(400), null, source, owner, owner,
            target, 10, engine.stateView(source).version(), null, null, OperationRecord.Kind.ATTACK);
        assertTrue(engine.beginOperation(attack));
        var permit = new CombatEngine.EffectPermit(id(401), source,
            attack.operationId(), owner, Set.of(target), Set.of(EncounterPhase.ACTIVE),
            1, engine.stateView(source).round() + 1);
        engine.issueEffectPermit(permit);
        var oldResult = engine.publish(source, attack.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "miss", 0, 0, true);
        assertTrue(engine.hasAttemptedAttack(source, owner));
        assertFalse(engine.stateView(source).members().get(owner).action());
        while (engine.stateView(plan.primary()).phase() != EncounterPhase.ENVIRONMENT) {
            var state = engine.stateView(plan.primary());
            engine.endTurn(plan.primary(), state.current());
        }
        var primaryBefore = engine.stateView(plan.primary());
        var sampled = mergedRegion();
        var merged = engine.commitMerge(plan, sampled, id(900), 20);
        assertEquals(OperationRecord.Kind.MERGE, merged.snapshot().kind());
        assertEquals(plan.primary(), merged.snapshot().encounterId());
        assertEquals(Set.of(plan.primary()), engine.encounterIds());
        assertEquals(6, engine.stateView(plan.primary()).members().size());
        assertEquals(primaryBefore.environmentRemaining(), engine.stateView(plan.primary()).environmentRemaining());
        assertEquals(primaryBefore.members().get(primaryBefore.order().get(0)).movementTicks(),
            engine.stateView(plan.primary()).members().get(primaryBefore.order().get(0)).movementTicks());
        assertFalse(engine.stateView(plan.primary()).members().get(owner).action());
        assertEquals(primaryBefore.round() + 1, engine.stateView(plan.primary()).members().get(owner).eligibleRound());
        assertTrue(engine.hasAttemptedAttack(plan.primary(), owner));
        assertEquals(plan.primary(), engine.encounterOf(owner));
        assertEquals(plan.primary(), engine.canonicalEncounterId(source));
        assertEquals(oldResult, engine.resultFor(source, attack.operationId()));
        assertEquals(oldResult, engine.resultFor(plan.primary(), attack.operationId()));
        assertSame(merged, engine.commitMerge(plan, sampled, id(900), 20));
        engine.issueEffectPermit(permit);
        for (int step = 0; step < 2; step++) {
            UUID stepId = id(910 + step);
            assertTrue(engine.authorizeEnvironmentStep(plan.primary(), stepId));
            assertTrue(engine.commitEnvironmentStep(plan.primary(), stepId));
        }
        var child = new OperationRecord.Snapshot(id(402), attack.operationId(),
            plan.primary(), owner, owner, target, 21, engine.stateView(plan.primary()).version(),
            null, null, OperationRecord.Kind.DAMAGE);
        assertTrue(engine.beginOperation(child, id(401)));
        engine.publish(plan.primary(), child.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "observed", 0, 0, true);
        var duplicateUse = new OperationRecord.Snapshot(id(403), attack.operationId(),
            plan.primary(), owner, owner, target, 22, engine.stateView(plan.primary()).version(),
            null, null, OperationRecord.Kind.DAMAGE);
        assertFalse(engine.beginOperation(duplicateUse, id(401)));
        engine.end(plan.primary());
        assertEquals(merged, engine.commitMerge(plan, sampled, id(900), 20));
        assertThrows(IllegalStateException.class,
            () -> engine.commitMerge(plan, sampled, id(900), 21));
    }

    @Test
    void mergeCommitRejectsChangedRosterWithoutPartialMigration() {
        CombatEngine engine = new CombatEngine(new Random(8), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)));
        engine.beginCandidate(id(101), region(3), Set.of(id(2)));
        var plan = engine.planMerge(id(100));
        UUID source = plan.encounters().stream().filter(value -> !value.equals(plan.primary()))
            .findFirst().orElseThrow();
        assertTrue(engine.join(source, id(3)));
        while (engine.stateView(plan.primary()).phase() != EncounterPhase.ENVIRONMENT) {
            var state = engine.stateView(plan.primary());
            engine.endTurn(plan.primary(), state.current());
        }
        var primaryBefore = engine.stateView(plan.primary());
        var sourceBefore = engine.stateView(source);
        assertThrows(IllegalStateException.class,
            () -> engine.commitMerge(plan, mergedRegion(), id(901), 20));
        assertEquals(primaryBefore, engine.stateView(plan.primary()));
        assertEquals(sourceBefore, engine.stateView(source));
        assertEquals(source, engine.encounterOf(id(3)));
    }

    @Test
    void mergePlanExpiresAfterItsFirstEnvironmentStepIsAuthorized() {
        CombatEngine engine = new CombatEngine(new Random(8), 28, 2);
        engine.beginCandidate(id(100), region(0), Set.of(id(1)));
        engine.beginCandidate(id(101), region(3), Set.of(id(2)));
        var plan = engine.planMerge(id(100));
        UUID primary = plan.primary();
        UUID source = plan.encounters().stream().filter(id -> !id.equals(primary)).findFirst().orElseThrow();
        engine.endTurn(primary, engine.stateView(primary).current());
        assertEquals(plan.targetEnvironmentRound(), engine.stateView(primary).round());

        UUID firstStep = id(950);
        assertTrue(engine.authorizeEnvironmentStep(primary, firstStep));
        assertTrue(engine.cancelEnvironmentStep(primary, firstStep));
        assertTrue(engine.mergeWindowExpired(plan));
        var replanned = engine.renewMergePlan(plan);
        assertEquals(primary, replanned.primary());
        assertEquals(plan.encounters(), replanned.encounters());
        assertEquals(plan.targetEnvironmentRound() + 1, replanned.targetEnvironmentRound());
        var primaryBefore = engine.stateView(primary);
        var sourceBefore = engine.stateView(source);
        assertThrows(IllegalStateException.class,
            () -> engine.commitMerge(plan, mergedRegion(), id(951), 20));
        assertEquals(primaryBefore, engine.stateView(primary));
        assertEquals(sourceBefore, engine.stateView(source));

        for (int step = 0; step < 2; step++) {
            UUID stepId = id(952 + step);
            assertTrue(engine.authorizeEnvironmentStep(primary, stepId));
            assertTrue(engine.commitEnvironmentStep(primary, stepId));
        }
        engine.endTurn(primary, engine.stateView(primary).current());
        assertThrows(IllegalStateException.class,
            () -> engine.commitMerge(plan, mergedRegion(), id(951), 20));
        assertEquals(OperationRecord.Outcome.COMPLETED,
            engine.commitMerge(replanned, mergedRegion(), id(951), 20).outcome());
    }

    @Test
    void invalidDamageTraceCannotPartiallyPublishOrSpendResources() {
        Fixture f = fixture(true);
        var attack = attack(f);
        var before = f.engine().stateView(f.encounter());
        var wrongTrace = new DamageTrace(id(999), f.target(), CombatRules.RollMode.NORMAL,
            10, 10, 10, 15, 1, true, false, 2, 0, 2, true, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> f.engine().publish(f.encounter(),
            attack.operationId(), 0, OperationRecord.Outcome.COMPLETED, "hit", 0, 1, true, wrongTrace));
        assertEquals(before, f.engine().stateView(f.encounter()));
        assertEquals(0, f.engine().resultPage(f.encounter(), 0, 1).total());
        var wrongTarget = new DamageTrace(attack.operationId(), id(998), CombatRules.RollMode.NORMAL,
            10, 10, 10, 15, 1, true, false, 2, 0, 2, true, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> f.engine().publish(f.encounter(),
            attack.operationId(), 0, OperationRecord.Outcome.COMPLETED, "hit", 0, 1, true, wrongTarget));
        assertEquals(before, f.engine().stateView(f.encounter()));
        assertEquals(0, f.engine().resultPage(f.encounter(), 0, 1).total());
        var wrongSource = new DamageTrace(attack.operationId(), f.target(), CombatRules.RollMode.NORMAL,
            10, 10, 10, 15, 1, true, false, 2, 0, 2, true, 0, 1,
            new DamageTrace.DamageEvidence(id(997), CombatRules.RULES_REVISION, 1, false,
                DamageTrace.Stage.VANILLA_ACCEPTED, 0, false, false, false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> f.engine().publish(f.encounter(),
            attack.operationId(), 0, OperationRecord.Outcome.COMPLETED, "hit", 0, 1, true, wrongSource));
        assertEquals(before, f.engine().stateView(f.encounter()));
        assertEquals(0, f.engine().resultPage(f.encounter(), 0, 1).total());
        var trace = new DamageTrace(attack.operationId(), f.target(), CombatRules.RollMode.NORMAL,
            10, 10, 10, 15, 1, true, false, 2, 0, 2, true, 0, 1);
        assertEquals(trace, f.engine().publish(f.encounter(), attack.operationId(), 0,
            OperationRecord.Outcome.COMPLETED, "hit", 0, 1, true, trace).damageTrace());
    }

    @Test
    void plannerStopsBeforeExpandingPastNodeBudget() {
        TacticalPlanner.CellProbe open = new TacticalPlanner.CellProbe() {
            @Override public boolean canOccupy(GridCell cell) { return true; }
            @Override public int traversalCost(GridCell from, GridCell to) { return 1; }
        };
        assertThrows(IllegalStateException.class, () -> TacticalPlanner.propose(
            new GridCell(0, 0, 0), new GridCell(4, 0, 0), 4, 1, 1, open));
    }
}
