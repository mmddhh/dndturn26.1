package cc.sighs.dndturn.combat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsentWindowTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test void leaveAndReentryRevokesOnlyThatPlayersConsent() {
        var window = new ConsentWindow(UUID.randomUUID(), 10, 400, Set.of(a, b));
        window.respond(a, 0, true, 11);
        window.respond(b, 1, true, 12);
        assertTrue(window.ready(12));
        window.refresh(Set.of(b, c), 13);
        window.refresh(Set.of(a, b, c), 14);
        assertEquals(Set.of(b), window.view().approvals());
        assertFalse(window.ready(14));
        assertEquals(410, window.view().deadline());
    }

    @Test void overlapPreservesApprovalsAndEarliestDeadline() {
        var first = new ConsentWindow(UUID.randomUUID(), 0, 400, Set.of(a, b));
        var second = new ConsentWindow(UUID.randomUUID(), 50, 400, Set.of(b, c));
        first.respond(a, 0, true, 1);
        second.respond(c, 0, true, 51);
        first.absorb(second, Set.of(a, b, c), 52);
        assertEquals(Set.of(a, c), first.view().approvals());
        assertEquals(400, first.view().deadline());
        assertEquals(ConsentWindow.Status.CANCELED, second.view().status());
        first.respond(b, first.view().revision(), true, 399);
        assertTrue(first.ready(399));
        assertFalse(first.ready(400));
        assertThrows(IllegalStateException.class, () -> first.commit(400));
    }

    @Test void invalidReplyDoesNotPartiallyApproveAndDisconnectClosesWholeRequest() {
        var window = new ConsentWindow(UUID.randomUUID(), 0, 400, Set.of(a, b));
        var before = window.view();
        assertThrows(IllegalStateException.class, () -> window.respond(c, 0, true, 1));
        assertThrows(IllegalStateException.class, () -> window.respond(a, 9, true, 1));
        assertEquals(before, window.view());
        window.disconnected(c);
        assertEquals(before, window.view());
        window.disconnected(a);
        assertEquals(ConsentWindow.Status.DISCONNECTED, window.view().status());
        assertThrows(IllegalStateException.class, () -> window.respond(b, window.view().revision(), true, 2));
    }

    @Test void terminalCommitIsImmutableAndDeclineNeverStarts() {
        var window = new ConsentWindow(UUID.randomUUID(), 0, 400, Set.of(a));
        window.respond(a, 0, true, 1);
        window.commit(2);
        var committed = window.view();
        window.refresh(Set.of(b), 500);
        window.disconnected(a);
        assertEquals(committed, window.view());
        var refused = new ConsentWindow(UUID.randomUUID(), 0, 400, Set.of(a));
        refused.respond(a, 0, false, 1);
        assertFalse(refused.ready(2));
    }
}
