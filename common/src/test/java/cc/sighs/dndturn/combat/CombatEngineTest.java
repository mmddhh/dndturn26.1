package cc.sighs.dndturn.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEngineTest {
    private static EncounterRegion bounds(int x) {
        return EncounterRegion.generate("overworld", new EncounterRegion.Discovery(x, 0, 0, x + 10, 10, 10),
            java.util.List.of(new EncounterRegion.Anchor(new UUID(0, 99), new EncounterRegion.Point(x + 5, 5, 5))), 5, 1);
    }

    @Test
    void candidateRerollsOnHostilityAndMembersCannotJoinTwice() {
        CombatEngine rules = new CombatEngine(new Random(5), 28, 20);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), id = UUID.randomUUID();
        rules.beginCandidate(id, bounds(0), Set.of(a, b));
        assertThrows(IllegalStateException.class, () -> rules.beginCandidate(UUID.randomUUID(), bounds(30), Set.of(a)));
        long candidateVersion = rules.view(id).version();
        rules.setHostile(id, b, a, true);
        assertTrue(rules.endTurn(id, rules.view(id).current()));
        assertEquals(EncounterPhase.ACTIVE, rules.view(id).phase());
        assertTrue(rules.view(id).version() > candidateVersion);
    }

    @Test
    void duplicateOperationAndStepNeverSpendAgain() {
        CombatEngine rules = new CombatEngine(new Random(3), 28, 20);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), id = UUID.randomUUID(), op = UUID.randomUUID();
        rules.beginCandidate(id, bounds(0), Set.of(a, b));
        rules.setHostile(id, a, b, true);
        rules.endTurn(id, rules.view(id).current());
        UUID owner = rules.view(id).current();
        OperationRecord.Snapshot snapshot = new OperationRecord.Snapshot(op, null, id, owner, owner, b,
            10, rules.view(id).version(), null, null, OperationRecord.Kind.MOVE);
        assertTrue(rules.beginOperation(snapshot));
        assertFalse(rules.beginOperation(snapshot));
        var first = rules.publish(id, op, 0, OperationRecord.Outcome.PARTIAL, "collision", 4, 0, false);
        assertSame(first, rules.publish(id, op, 0, OperationRecord.Outcome.PARTIAL, "collision", 4, 0, false));
        assertEquals(24, rules.view(id).members().get(owner).movementTicks());
        var end = rules.publish(id, op, 1, OperationRecord.Outcome.COMPLETED, "stopped", 2, 0, true);
        assertSame(end, rules.publish(id, op, 1, OperationRecord.Outcome.COMPLETED, "stopped", 2, 0, true));
        assertThrows(IllegalStateException.class,
            () -> rules.publish(id, op, 2, OperationRecord.Outcome.COMPLETED, "retry", 100, 0, true));
        assertEquals(22, rules.view(id).members().get(owner).movementTicks());
    }

    @Test
    void environmentTurnHasExactConfiguredLength() {
        CombatEngine rules = new CombatEngine(new Random(1), 28, 20);
        UUID a = UUID.randomUUID(), id = UUID.randomUUID();
        rules.beginCandidate(id, bounds(0), Set.of(a));
        rules.endTurn(id, rules.view(id).current());
        assertEquals(EncounterPhase.ENVIRONMENT, rules.view(id).phase());
        for (int i = 0; i < 19; i++) {
            UUID step = new UUID(0, i + 1);
            assertTrue(rules.authorizeEnvironmentStep(id, step));
            assertTrue(rules.commitEnvironmentStep(id, step));
        }
        assertEquals(EncounterPhase.ENVIRONMENT, rules.view(id).phase());
        UUID finalStep = new UUID(0, 20);
        assertTrue(rules.authorizeEnvironmentStep(id, finalStep));
        assertTrue(rules.commitEnvironmentStep(id, finalStep));
        assertEquals(EncounterPhase.CANDIDATE, rules.view(id).phase());
    }

    @Test
    void failedWorldTickKeepsUnknownStepWithoutSpendingEnvironmentBudget() {
        CombatEngine rules = new CombatEngine(new Random(1), 28, 20);
        UUID member = UUID.randomUUID(), encounter = UUID.randomUUID(), step = UUID.randomUUID();
        rules.beginCandidate(encounter, bounds(0), Set.of(member));
        rules.endTurn(encounter, member);
        assertTrue(rules.authorizeEnvironmentStep(encounter, step));
        int budget = rules.stateView(encounter).environmentRemaining();
        var result = rules.failEnvironmentStep(encounter, step, 41,
            "world tick aborted after partial execution: TestFailure");
        assertSame(result, rules.failEnvironmentStep(encounter, step, 41, result.reason()));
        assertEquals(OperationRecord.Outcome.UNKNOWN, result.outcome());
        assertEquals(OperationRecord.Kind.ENVIRONMENT, result.snapshot().kind());
        assertEquals(step, result.snapshot().operationId());
        assertEquals(budget, rules.stateView(encounter).environmentRemaining());
        assertFalse(rules.commitEnvironmentStep(encounter, step));
        assertThrows(IllegalStateException.class,
            () -> rules.failEnvironmentStep(encounter, step, 41, "different reason"));
        rules.end(encounter);
        assertEquals(result, rules.resultFor(encounter, step));
    }

    @Test
    void externalArrowNeedsAuthorizedEnvironmentStepAndCannotSpendMemberAction() {
        CombatEngine rules = new CombatEngine(new Random(1), 28, 20);
        UUID target = UUID.randomUUID(), shooter = UUID.randomUUID(), encounter = UUID.randomUUID();
        rules.beginCandidate(encounter, bounds(0), Set.of(target));
        rules.endTurn(encounter, target);
        UUID arrow = UUID.randomUUID(), attack = UUID.randomUUID(), step = UUID.randomUUID();
        ProjectileOrigin origin = new ProjectileOrigin(arrow, shooter, null, null,
            "overworld", 12, 2.0, "minecraft:bow", "minecraft:arrow", false, true, false);
        var unauthorized = new OperationRecord.Snapshot(attack, null, encounter,
            shooter, arrow, target, 13, rules.stateView(encounter).version(),
            null, null, OperationRecord.Kind.ATTACK);
        assertFalse(rules.beginCausalProjectileAttack(origin, unauthorized));
        assertTrue(rules.authorizeEnvironmentStep(encounter, step));
        var accepted = new OperationRecord.Snapshot(attack, null, encounter,
            shooter, arrow, target, 13, rules.stateView(encounter).version(),
            null, null, OperationRecord.Kind.ATTACK);
        assertTrue(rules.beginCausalProjectileAttack(origin, accepted));
        assertFalse(rules.beginCausalProjectileAttack(origin, accepted));
        assertThrows(IllegalStateException.class, () -> rules.beginCausalProjectileAttack(origin, unauthorized));
        rules.publish(encounter, attack, 0, OperationRecord.Outcome.COMPLETED, "miss", 0, 0, true);
        assertTrue(rules.commitEnvironmentStep(encounter, step));
    }

    @Test
    void unauthorizedArrowImpactHasOneTerminalRejectionWithoutSpendingEnvironmentBudget() {
        CombatEngine rules = new CombatEngine(new Random(1), 28, 2);
        UUID member = UUID.randomUUID(), encounter = UUID.randomUUID();
        rules.beginCandidate(encounter, bounds(0), Set.of(member));
        rules.endTurn(encounter, member);
        UUID arrow = UUID.randomUUID(), rejected = UUID.randomUUID(), step = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> rules.rejectCausalProjectileEffect(
            encounter, rejected, arrow, arrow, null, 10, "block impact unsupported"));
        rules.authorizeEnvironmentStep(encounter, step);
        int remaining = rules.stateView(encounter).environmentRemaining();
        var result = rules.rejectCausalProjectileEffect(encounter, rejected, arrow, arrow,
            null, 10, "block impact unsupported");
        assertEquals(OperationRecord.Outcome.REJECTED, result.outcome());
        assertEquals(OperationRecord.Kind.ENVIRONMENT, result.snapshot().kind());
        assertSame(result, rules.rejectCausalProjectileEffect(encounter, rejected, arrow, arrow,
            null, 11, "block impact unsupported"));
        assertThrows(IllegalStateException.class, () -> rules.rejectCausalProjectileEffect(
            encounter, rejected, arrow, arrow, null, 10, "different collision"));
        assertEquals(remaining, rules.stateView(encounter).environmentRemaining());
        rules.commitEnvironmentStep(encounter, step);
        assertEquals(remaining - 1, rules.stateView(encounter).environmentRemaining());
    }

    @Test
    void defensiveActionsSpendOneActionAndExpireAtTheirOwnBoundaries() {
        CombatEngine dodgeRules = new CombatEngine(new Random(4), 28, 1);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), encounter = UUID.randomUUID();
        dodgeRules.beginCandidate(encounter, bounds(0), Set.of(a, b));
        dodgeRules.setHostile(encounter, a, b, true);
        dodgeRules.endTurn(encounter, dodgeRules.stateView(encounter).current());
        UUID dodger = dodgeRules.stateView(encounter).current();
        UUID dodgeId = UUID.randomUUID();
        var dodge = new OperationRecord.Snapshot(dodgeId, null, encounter, dodger, dodger,
            null, 10, dodgeRules.stateView(encounter).version(), null, null,
            OperationRecord.Kind.DODGE);
        assertTrue(dodgeRules.beginOperation(dodge));
        assertFalse(dodgeRules.beginOperation(dodge));
        dodgeRules.publish(encounter, dodgeId, 0, OperationRecord.Outcome.COMPLETED,
            "dodge", 0, 0, true);
        assertFalse(dodgeRules.stateView(encounter).members().get(dodger).action());
        dodgeRules.endTurn(encounter, dodger);
        assertTrue(dodgeRules.stateView(encounter).members().get(dodger).dodging());
        while (dodgeRules.stateView(encounter).phase() == EncounterPhase.ACTIVE
            && !dodger.equals(dodgeRules.stateView(encounter).current()))
            dodgeRules.endTurn(encounter, dodgeRules.stateView(encounter).current());
        if (dodgeRules.stateView(encounter).phase() == EncounterPhase.ENVIRONMENT) {
            UUID step = UUID.randomUUID();
            dodgeRules.authorizeEnvironmentStep(encounter, step);
            dodgeRules.commitEnvironmentStep(encounter, step);
        }
        while (!dodger.equals(dodgeRules.stateView(encounter).current()))
            dodgeRules.endTurn(encounter, dodgeRules.stateView(encounter).current());
        assertFalse(dodgeRules.stateView(encounter).members().get(dodger).dodging());

        CombatEngine disengageRules = new CombatEngine(new Random(5), 28, 1);
        UUID otherEncounter = UUID.randomUUID();
        disengageRules.beginCandidate(otherEncounter, bounds(0), Set.of(a, b));
        disengageRules.setHostile(otherEncounter, a, b, true);
        disengageRules.endTurn(otherEncounter, disengageRules.stateView(otherEncounter).current());
        UUID mover = disengageRules.stateView(otherEncounter).current();
        UUID disengageId = UUID.randomUUID();
        var disengage = new OperationRecord.Snapshot(disengageId, null, otherEncounter,
            mover, mover, null, 11, disengageRules.stateView(otherEncounter).version(),
            null, null, OperationRecord.Kind.DISENGAGE);
        assertTrue(disengageRules.beginOperation(disengage));
        disengageRules.publish(otherEncounter, disengageId, 0, OperationRecord.Outcome.COMPLETED,
            "disengage", 0, 0, true);
        assertTrue(disengageRules.stateView(otherEncounter).members().get(mover).disengaged());
        disengageRules.endTurn(otherEncounter, mover);
        assertFalse(disengageRules.stateView(otherEncounter).members().get(mover).disengaged());
    }

    @Test
    void valueSnapshotRestoresPendingStepsAndImmutableRetryLedger() {
        CombatEngine rules = new CombatEngine(new Random(8), 28, 20);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), encounter = UUID.randomUUID();
        rules.beginCandidate(encounter, bounds(0), Set.of(a, b));
        rules.setHostile(encounter, a, b, true);
        rules.endTurn(encounter, rules.view(encounter).current());
        UUID owner = rules.stateView(encounter).current(), operation = UUID.randomUUID();
        var initial = new OperationRecord.Snapshot(operation, null, encounter, owner, owner,
            null, 10, rules.stateView(encounter).version(), null, null,
            OperationRecord.Kind.MOVE);
        assertTrue(rules.beginOperation(initial));
        var first = rules.publish(encounter, operation, 0, OperationRecord.Outcome.PARTIAL,
            "observed movement", 2, 0, false);
        CombatStateSnapshot saved = rules.exportSnapshot();
        CombatEngine restored = CombatEngine.restoreSnapshot(saved, new Random(9));
        assertEquals(saved, restored.exportSnapshot());
        assertEquals(initial, restored.pendingOperation(encounter, operation));
        assertFalse(restored.beginOperation(initial));
        assertEquals(first, restored.publish(encounter, operation, 0,
            OperationRecord.Outcome.PARTIAL, "observed movement", 2, 0, false));
        var terminal = restored.publish(encounter, operation, 1,
            OperationRecord.Outcome.COMPLETED, "settled", 0, 0, true);
        assertEquals(terminal, restored.resultFor(encounter, operation));
        assertEquals(26, restored.stateView(encounter).members().get(owner).movementTicks());
    }

    @Test
    void restoredExecutingWorkBecomesUnknownWithoutAnotherEffect() {
        CombatEngine rules = new CombatEngine(new Random(5), 28, 1);
        UUID member = UUID.randomUUID(), encounter = UUID.randomUUID();
        rules.beginCandidate(encounter, bounds(0), Set.of(member));
        rules.endTurn(encounter, member);
        UUID step = UUID.randomUUID();
        assertTrue(rules.authorizeEnvironmentStep(encounter, step));
        CombatEngine restored = CombatEngine.restoreSnapshot(rules.exportSnapshot(), new Random(6));
        assertEquals(1, restored.failRestoredWork(encounter, 100, "unconfirmed after restart"));
        assertEquals(OperationRecord.Outcome.UNKNOWN,
            restored.resultFor(encounter, step).outcome());
        assertFalse(restored.commitEnvironmentStep(encounter, step));
        assertEquals(1, restored.stateView(encounter).environmentRemaining());
        UUID arrow = UUID.randomUUID(), attack = UUID.randomUUID();
        var lost = restored.recordRestoredUnknown(encounter, attack, arrow, arrow,
            member, 101, "projectile missing after restart");
        assertEquals(OperationRecord.Outcome.UNKNOWN, lost.outcome());
        assertSame(lost, restored.recordRestoredUnknown(encounter, attack, arrow, arrow,
            member, 101, "projectile missing after restart"));
    }

    @Test
    void restoredEncounterUnknownAcceptsNoTargetAndRemainsIdempotent() {
        CombatEngine rules = new CombatEngine(new Random(5), 28, 1);
        UUID member = UUID.randomUUID(), encounter = UUID.randomUUID(), operation = UUID.randomUUID();
        rules.beginCandidate(encounter, bounds(0), Set.of(member));
        CombatEngine restored = CombatEngine.restoreSnapshot(rules.exportSnapshot(), new Random(6));
        var before = restored.stateView(encounter);
        var result = restored.recordRestoredUnknown(encounter, operation, member, member,
            null, 100, "members unavailable after restart");
        assertNull(result.snapshot().target());
        assertEquals(OperationRecord.Outcome.UNKNOWN, result.outcome());
        assertEquals(before.members(), restored.stateView(encounter).members());
        var committed = restored.exportSnapshot();
        assertSame(result, restored.recordRestoredUnknown(encounter, operation, member, member,
            null, 100, "members unavailable after restart"));
        assertThrows(IllegalStateException.class, () -> restored.recordRestoredUnknown(encounter,
            operation, member, member, member, 100, "members unavailable after restart"));
        assertThrows(IllegalStateException.class, () -> restored.recordRestoredUnknown(encounter,
            UUID.randomUUID(), member, member, UUID.randomUUID(), 100, "invalid target"));
        assertEquals(committed, restored.exportSnapshot());
        restored.end(encounter);
        CombatEngine reloaded = CombatEngine.restoreSnapshot(restored.exportSnapshot(), new Random(7));
        assertEquals(result, reloaded.resultFor(encounter, operation));
        assertNull(reloaded.encounterOf(member));
    }

    @Test
    void pathProposalRejectsChangedWorldSnapshot() {
        GridCell start = new GridCell(0, 0, 0), target = new GridCell(2, 0, 0);
        TacticalPlanner.CellProbe probe = new TacticalPlanner.CellProbe() {
            public boolean canOccupy(GridCell cell) { return cell.y() == 0 && cell.z() == 0 && cell.x() >= 0 && cell.x() <= 2; }
            public int traversalCost(GridCell from, GridCell to) { return 1; }
        };
        var proposal = TacticalPlanner.propose(start, target, 3, 32, 7, probe);
        assertEquals(2, proposal.cost());
        assertTrue(TacticalPlanner.revalidate(start, proposal, 7, probe));
        assertFalse(TacticalPlanner.revalidate(start, proposal, 8, probe));
    }

    @Test
    void armorConversionAndNaturalRollsHaveSingleRule() {
        assertEquals(12, CombatRules.armorClass(10.9, true));
        assertEquals(3, CombatRules.damageAfterReduction(5, CombatRules.damageReduction(2.7), false));
        assertEquals(6, CombatRules.damageAfterReduction(5, 2, true));
        assertEquals(7, CombatRules.damageAfterReduction(4.75, 1, true));
        assertEquals(3, CombatRules.damageAfterReduction(4.75, 1, false));
        assertEquals(CombatRules.RollMode.NORMAL, CombatRules.mode(true, true));
        assertFalse(CombatRules.rollAttack(new Random() { public int nextInt(int bound) { return 0; } },
            CombatRules.RollMode.NORMAL, 0).hit());
        assertTrue(CombatRules.rollAttack(new Random() { public int nextInt(int bound) { return 19; } },
            CombatRules.RollMode.NORMAL, 100).critical());
    }

    @Test
    void domainMatrixAllowsOwnedAttackButRejectsEnvironmentAndCrossDomain() {
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
        var battle = CombatEngine.Domain.encounter(id);
        assertTrue(InteractionPolicy.decide(battle, battle, InteractionPolicy.Effect.DAMAGE, owner).allowed());
        assertFalse(InteractionPolicy.decide(battle, battle, InteractionPolicy.Effect.ENVIRONMENT_DAMAGE, owner).allowed());
        assertFalse(InteractionPolicy.decide(CombatEngine.Domain.world(), battle,
            InteractionPolicy.Effect.PROJECTILE_COLLISION, owner).allowed());
    }

    @Test
    void endingDuringWorldExecutionKeepsAnUnknownResult() {
        CombatEngine rules = new CombatEngine(new Random(2), 28, 20);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), id = UUID.randomUUID(), op = UUID.randomUUID();
        rules.beginCandidate(id, bounds(0), Set.of(a, b));
        rules.setHostile(id, a, b, true);
        rules.endTurn(id, rules.view(id).current());
        UUID owner = rules.view(id).current();
        UUID target = owner.equals(a) ? b : a;
        var snapshot = new OperationRecord.Snapshot(op, null, id, owner, owner, target, 1,
            rules.view(id).version(), null, null, OperationRecord.Kind.ATTACK);
        assertTrue(rules.beginOperation(snapshot));
        rules.end(id);
        assertNull(rules.encounterOf(owner));
        assertEquals(OperationRecord.Outcome.UNKNOWN,
            rules.closedView(id).results().get(0).outcome());
    }
}
