package cc.sighs.dndturn.combat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepairRegressionTest {
    @Test void readinessRevisionPreservesResultsAndRejectsStalePackets() {
        UUID generation = UUID.randomUUID(), encounter = UUID.randomUUID();
        var pending = new EncounterProjectionOrder.Stamp(generation, encounter, 4, 7, true, 1);
        var ready = new EncounterProjectionOrder.Stamp(generation, encounter, 4, 7, true, 2);
        assertEquals(new EncounterProjectionOrder.Update(
            EncounterProjectionOrder.Decision.ACCEPT_KEEP_RESULTS, 13, true),
            EncounterProjectionOrder.advance(pending, 13, true, ready));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE, EncounterProjectionOrder.decide(ready, pending));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE, EncounterProjectionOrder.decide(ready, ready));
    }

    @Test void damageOverloadsRejectTheSameInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damageAfterReduction(-1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damageAfterReduction(-1.0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damageAfterReduction(1, -1, false));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damageAfterReduction(1.0, -1, false));
        assertEquals(CombatRules.damageAfterReduction(7.0, 2, true), CombatRules.damageAfterReduction(7, 2, true));
    }
}
