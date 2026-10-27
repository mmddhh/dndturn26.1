package cc.sighs.dndturn.platform.server.effect.vanilla;
import cc.sighs.dndturn.platform.server.checkpoint.VanillaEffectSettlementState;

import cc.sighs.dndturn.domain.encounter.time.RoundDuration;
import cc.sighs.dndturn.domain.encounter.time.RoundTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VanillaEffectSettlementStateTest {
    @Test void rejectsDurationEvidenceThatDoesNotMatchCapturedRounds() {
        assertThrows(IllegalArgumentException.class, () -> new VanillaEffectSettlementState.Timer(
                UUID.randomUUID(), 0, 31, RoundDuration.capture(new RoundTime(30), 31), "dndturn:basic_effect", 1));
    }
}
