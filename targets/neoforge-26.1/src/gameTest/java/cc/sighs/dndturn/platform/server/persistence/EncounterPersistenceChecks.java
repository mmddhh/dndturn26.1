package cc.sighs.dndturn.platform.server.persistence;

import cc.sighs.dndturn.domain.encounter.EncounterStateSnapshot;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.server.action.AbilityCheckpoint;
import cc.sighs.dndturn.platform.server.ability.AbilityExecutor;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;

public final class EncounterPersistenceChecks {
    private final GameTestHelper helper;
    private EncounterPersistenceChecks(GameTestHelper helper) { this.helper = helper; }
    public static void verify(GameTestHelper helper) {
        var checks = new EncounterPersistenceChecks(helper);
        checks.savesMetadataImmediatelyAndClockPeriodicallyWithDetachedEvidence();
        checks.failedCaptureDoesNotAcknowledgeDirtyEvidence();
        checks.terminalCheckpointCannotBeRewritten();
        checks.invalidSaveRemainsUntouchedEvenAfterExplicitDirtyRequest();
    }
    private <T> T fail(String message) { throw new AssertionError(message); }
    private void assertTrue(boolean value) { helper.assertTrue(value, "persistence invariant failed"); }
    private void assertFalse(boolean value) { assertTrue(!value); }
    private void assertNull(Object value) { assertTrue(value == null); }
    private void assertEquals(Object expected, Object actual) {
        helper.assertTrue(java.util.Objects.equals(expected, actual), "expected " + expected + "; actual " + actual);
    }
    private void assertThrows(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            helper.assertTrue(type.isInstance(failure), "unexpected exception: " + failure);
            return;
        }
        fail("expected " + type.getName());
    }

    private static CombatPersistenceEnvelope envelope(EncounterStateSnapshot rules, long clock,
                                                       EncounterPersistence persistence) {
        return new CombatPersistenceEnvelope(rules, clock, 0, Map.of(), Map.of(), Map.of(), Map.of(),
            Map.of(), List.of(), Map.of(), Map.of(), Map.of(), List.of(), Map.of(), List.of(),
            persistence.checkpoints(), Map.of(), Map.of(), persistence.audits(), Map.of(), Map.of());
    }

    private void savesMetadataImmediatelyAndClockPeriodicallyWithDetachedEvidence() {
        var data = new CombatSavedData();
        var opened = EncounterPersistence.open(data, 30);
        var persistence = opened.persistence();
        var authority = opened.authority();
        persistence.persistIfChanged(authority, 0, rules -> envelope(rules, 0, persistence));
        persistence.persistIfChanged(authority, 19, rules -> fail("unchanged checkpoint captured too early"));
        var before = data.envelope();
        var audit = new CombatPersistenceEnvelope.RecoveryAudit(UUID.randomUUID(), UUID.randomUUID(), 19,
            ActionFailure.Code.UNKNOWN_EFFECT, "confirmed evidence retained; action not replayed");
        persistence.appendAudit(audit);
        var detached = persistence.audits();
        persistence.persistIfChanged(authority, 19, rules -> envelope(rules, 19, persistence));
        assertTrue(before.recoveryAudits().isEmpty());
        assertEquals(List.of(audit), List.copyOf(data.envelope().recoveryAudits().values()));
        persistence.persistIfChanged(authority, 38, rules -> fail("clock interval must start at last write"));
        persistence.persistIfChanged(authority, 39, rules -> envelope(rules, 39, persistence));
        assertEquals(39L, data.envelope().cumulativeServerTicks());
        var restored = EncounterPersistence.open(data, 30);
        assertFalse(restored.persistence().recoveryFailed());
        assertEquals(detached, restored.persistence().audits());
        persistence.appendAudit(audit);
        assertEquals(1, detached.size());
        assertEquals(2, persistence.audits().size());
        assertThrows(UnsupportedOperationException.class, detached::clear);
    }

    private void failedCaptureDoesNotAcknowledgeDirtyEvidence() {
        var data = new CombatSavedData();
        var opened = EncounterPersistence.open(data, 30);
        var persistence = opened.persistence();
        var authority = opened.authority();
        persistence.persistIfChanged(authority, 0, rules -> envelope(rules, 0, persistence));
        persistence.changed();
        var original = data.json();
        assertThrows(IllegalStateException.class, () -> persistence.persistIfChanged(authority, 1,
            rules -> { throw new IllegalStateException("capture failed"); }));
        assertEquals(original, data.json());
        persistence.persistIfChanged(authority, 1, rules -> envelope(rules, 1, persistence));
        assertEquals(1L, data.envelope().cumulativeServerTicks());
    }

    private void terminalCheckpointCannotBeRewritten() {
        var opened = EncounterPersistence.open(new CombatSavedData(), 30);
        var persistence = opened.persistence();
        var intent = new ActionIntent("dndturn:move", 1, ActionIntent.Capability.MOVE,
            new ActionIntent.Target(ActionIntent.TargetKind.GROUND, "minecraft:overworld", null,
                new GridCell(0, 0, 0), -1, 0, 0, 0), GrantEvidence.basic());
        UUID operation = UUID.randomUUID(), encounter = UUID.randomUUID();
        UUID actor = UUID.randomUUID(), instance = UUID.randomUUID();
        var terminal = new AbilityCheckpoint(operation, encounter, actor, instance, intent,
            AbilityCheckpoint.Phase.TERMINAL, AbilityExecutor.ExecutionClock.AUTHORIZED_EXECUTION_STEP,
            1, 0, null, null, null, null, null, "", AbilityCheckpoint.Release.RELEASED, "finished", null);
        persistence.recordCheckpoint(terminal);
        var before = persistence.checkpoints();
        persistence.recordCheckpoint(new AbilityCheckpoint(operation, encounter, actor, instance, intent,
            AbilityCheckpoint.Phase.EXECUTE, AbilityExecutor.ExecutionClock.AUTHORIZED_EXECUTION_STEP,
            2, 0, null, null, null, null, null, "", AbilityCheckpoint.Release.HELD, "late evidence", null));
        assertEquals(terminal, persistence.checkpoint(operation));
        persistence.appendAudit(new CombatPersistenceEnvelope.RecoveryAudit(operation, UUID.randomUUID(), 3,
            ActionFailure.Code.NONE, "control release reconciled"));
        assertEquals(before, persistence.checkpoints());
        assertThrows(UnsupportedOperationException.class, before::clear);
    }

    private void invalidSaveRemainsUntouchedEvenAfterExplicitDirtyRequest() {
        var data = new CombatSavedData("{\"rules\":{}}");
        var original = data.json();
        var opened = EncounterPersistence.open(data, 30);
        assertTrue(opened.persistence().recoveryFailed());
        assertNull(opened.checkpoint());
        opened.persistence().changed();
        opened.persistence().persistIfChanged(opened.authority(), 100,
            rules -> fail("invalid source must never be overwritten"));
        assertEquals(original, data.json());
    }
}
