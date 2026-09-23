package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.application.planning.BudgetedScan;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BudgetedScanTest {
    @Test void scanLargerThanTickBudgetEventuallyFinishesWithoutReplayingCompletedInputs() {
        var input = IntStream.range(0, 5000).boxed().toList();
        var scan = new BudgetedScan<Integer, Integer>(input, 5000);
        var calls = new AtomicInteger();
        for (int tick = 0; tick < 50; tick++) {
            var budget = new AtomicInteger(100);
            boolean done = scan.advance(() -> budget.getAndDecrement() > 0, i -> { calls.incrementAndGet(); return i * 2; });
            assertEquals(tick == 49, done);
        }
        assertEquals(5000, calls.get());
        assertEquals(9998, scan.results().get(4999));
        assertTrue(scan.advance(() -> false, i -> fail("completed scan evaluated twice")));
    }
    @Test void evaluationFailureKeepsEarlierProgressAndOutputCannotLeakBeforeCompletion() {
        var scan = new BudgetedScan<Integer, Integer>(List.of(1, 2, 3), 3);
        assertThrows(IllegalStateException.class, () -> scan.advance(() -> true, i -> {
            if (i == 2) throw new IllegalStateException("budget used inside evaluation");
            return i;
        }));
        assertEquals(1, scan.completed());
        assertThrows(IllegalStateException.class, scan::results);
        assertTrue(scan.advance(() -> true, i -> i));
        assertEquals(List.of(1, 2, 3), scan.results());
    }
}
