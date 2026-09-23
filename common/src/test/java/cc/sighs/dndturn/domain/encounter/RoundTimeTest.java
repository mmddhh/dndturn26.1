package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.domain.encounter.time.RoundTime;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoundTimeTest {
    @Test void durationChargeAndPeriodicRoundingAreDifferentPolicies() {
        for (int ticks : new int[] {30, 17}) {
            var time = new RoundTime(ticks);
            assertEquals(0, time.durationRounds(0));
            assertEquals(1, time.durationRounds(1));
            assertEquals(1, time.durationRounds(ticks));
            assertEquals(2, time.durationRounds(ticks + 1));
            assertEquals(ticks, time.completeChargeTicks(ticks * 2 - 1));
            assertEquals(0, time.completeChargeTicks(ticks - 1));
            assertEquals(ticks * 2, time.realtimeTicks(time.durationRounds(ticks + 1)));
            assertEquals(ticks / 25.0, time.periodicAmount(1, 25), 1e-12);
        }
        assertEquals(3, new RoundTime(30).durationRounds(90));
        assertEquals(3, new RoundTime(30).durationRounds(70));
        assertEquals(1.2, new RoundTime(30).periodicAmount(1, 25), 1e-12);
    }
    @Test void rejectsInvalidAndOverflowingConversions() {
        var time = new RoundTime(30);
        assertThrows(IllegalArgumentException.class, () -> new RoundTime(0));
        assertThrows(IllegalArgumentException.class, () -> time.durationRounds(-1));
        assertThrows(IllegalArgumentException.class, () -> time.periodicAmount(Double.NaN, 20));
        assertThrows(IllegalArgumentException.class, () -> time.periodicAmount(1, 0));
        assertThrows(ArithmeticException.class, () -> time.realtimeTicks(Integer.MAX_VALUE));
        assertEquals(71582789, time.durationRounds(Integer.MAX_VALUE));
    }
    @Test void capturedTimeSurvivesRestoreAndDoesNotFollowEngineDefaults() {
        UUID id = UUID.randomUUID(), member = UUID.randomUUID();
        var engine = new EncounterAuthority(new Random(184), 28, 20);
        var region = EncounterRegion.generate("test:world", new EncounterRegion.Discovery(-4,-4,-4,4,4,4),
            List.of(new EncounterRegion.Anchor(member, new EncounterRegion.Point(0,0,0))), 3, 1);
        engine.beginCandidate(id, region, Set.of(member), new RoundTime(17));
        assertEquals(17, engine.movementTicksPerTurn(id));
        assertEquals(17, engine.environmentTicks(id));
        engine.endTurn(id, member);
        UUID step = UUID.randomUUID();
        engine.authorizeEnvironmentStep(id, step); engine.commitEnvironmentStep(id, step);
        var restored = EncounterAuthority.restoreSnapshot(engine.exportSnapshot(), new Random(184));
        assertEquals(new RoundTime(17), restored.roundTime(id));
        assertEquals(16, restored.stateView(id).environmentRemaining());
        assertFalse(restored.commitEnvironmentStep(id, step));
        assertEquals(16, restored.stateView(id).environmentRemaining());
    }
}
