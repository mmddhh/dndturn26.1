package cc.sighs.dndturn.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EncounterRegionSweepTest {
    private static EncounterRegion region() {
        return EncounterRegion.generate("overworld",
            new EncounterRegion.Discovery(-2, 0, -2, 12, 8, 12),
            List.of(
                new EncounterRegion.Anchor(UUID.randomUUID(), new EncounterRegion.Point(0, 4, 0)),
                new EncounterRegion.Anchor(UUID.randomUUID(), new EncounterRegion.Point(10, 4, 0)),
                new EncounterRegion.Anchor(UUID.randomUUID(), new EncounterRegion.Point(0, 4, 10))),
            2, 1);
    }

    @Test
    void fastSegmentCrossesRegionWithBothEndpointsOutside() {
        EncounterRegion area = region();
        assertTrue(area.intersectsSegment(new EncounterRegion.Point(-20, 4, 2),
            new EncounterRegion.Point(20, 4, 2)));
        assertFalse(area.intersectsSegment(new EncounterRegion.Point(-20, 4, -15),
            new EncounterRegion.Point(20, 4, -15)));
    }

    @Test
    void verticalEnvelopeIsClippedBeforeHorizontalSweep() {
        EncounterRegion area = region();
        assertFalse(area.intersectsSegment(new EncounterRegion.Point(-20, 25, 2),
            new EncounterRegion.Point(20, 25, 2)));
        assertTrue(area.intersectsSegment(new EncounterRegion.Point(-20, 25, 2),
            new EncounterRegion.Point(20, 4, 2)));
        assertFalse(area.intersectsSegment(new EncounterRegion.Point(-20, 25, 2),
            new EncounterRegion.Point(-18, 4, 2)));
    }

    @Test
    void firstContactIsNearLeadingBoundary() {
        EncounterRegion area = region();
        double entry = area.firstIntersectionFraction(new EncounterRegion.Point(-20, 4, 0),
            new EncounterRegion.Point(20, 4, 0));
        assertTrue(entry > 0.44 && entry < 0.46);
        assertTrue(Double.isInfinite(area.firstIntersectionFraction(
            new EncounterRegion.Point(-20, 4, -15), new EncounterRegion.Point(20, 4, -15))));
    }
}
