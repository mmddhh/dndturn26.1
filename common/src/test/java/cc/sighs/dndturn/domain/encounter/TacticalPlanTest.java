package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.domain.action.ActionIntent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TacticalPlanTest {
    @Test void previewPositionsRejectNonFiniteAndOutOfBoundsValues() {
        assertThrows(IllegalArgumentException.class, () -> new ActionIntent.Point(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ActionIntent.Point(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new ActionIntent.Approach(UUID.randomUUID(), 256, new ActionIntent.Point(0, 0, 0)));
    }
}
