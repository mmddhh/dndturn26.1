package cc.sighs.dndturn.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EncounterProjectionOrderTest {
    private static final UUID GENERATION = new UUID(0, 100);
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static EncounterProjectionOrder.Stamp stamp(UUID id, long sequence,
                                                        long version, boolean active) {
        return new EncounterProjectionOrder.Stamp(GENERATION, id, sequence, version, active);
    }

    @Test
    void olderPrimaryBecomesTheNewProjectionAfterSourceTombstone() {
        var sourceTombstone = stamp(B, 2, 7, false);
        var mergedPrimary = stamp(A, 3, 9, true);
        assertEquals(EncounterProjectionOrder.Decision.ACCEPT_RESET_RESULTS,
            EncounterProjectionOrder.decide(sourceTombstone, mergedPrimary));
        assertEquals(new EncounterProjectionOrder.Update(
                EncounterProjectionOrder.Decision.ACCEPT_RESET_RESULTS, 0, false),
            EncounterProjectionOrder.advance(sourceTombstone, 4, true, mergedPrimary));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE,
            EncounterProjectionOrder.decide(mergedPrimary, sourceTombstone));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE,
            EncounterProjectionOrder.decide(mergedPrimary, stamp(B, 2, 6, true)));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE,
            EncounterProjectionOrder.decide(mergedPrimary, mergedPrimary));
    }

    @Test
    void primaryResultPrefixSurvivesReprojectionAndChainMerge() {
        var primary = stamp(A, 1, 4, true);
        var firstMerge = stamp(A, 4, 5, true);
        var secondMerge = stamp(A, 6, 6, true);
        assertEquals(EncounterProjectionOrder.Decision.ACCEPT_KEEP_RESULTS,
            EncounterProjectionOrder.decide(primary, firstMerge));
        assertEquals(new EncounterProjectionOrder.Update(
                EncounterProjectionOrder.Decision.ACCEPT_KEEP_RESULTS, 5, true),
            EncounterProjectionOrder.advance(primary, 5, true, firstMerge));
        assertEquals(EncounterProjectionOrder.Decision.ACCEPT_KEEP_RESULTS,
            EncounterProjectionOrder.decide(firstMerge, secondMerge));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE,
            EncounterProjectionOrder.decide(secondMerge, stamp(C, 5, 8, false)));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE,
            EncounterProjectionOrder.decide(secondMerge, stamp(A, 4, 5, true)));
    }

    @Test
    void NewerPrimaryAndClosedProjectionOrderingAreStable() {
        assertEquals(EncounterProjectionOrder.Decision.ACCEPT_RESET_RESULTS,
            EncounterProjectionOrder.decide(stamp(A, 1, 6, false), stamp(B, 3, 2, true)));
        assertEquals(EncounterProjectionOrder.Decision.ACCEPT_KEEP_RESULTS,
            EncounterProjectionOrder.decide(stamp(B, 3, 2, true), stamp(B, 3, 2, false)));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE,
            EncounterProjectionOrder.decide(stamp(B, 3, 2, false), stamp(B, 3, 2, true)));
    }
}
