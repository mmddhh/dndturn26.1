package cc.sighs.dndturn.platform.server.world;

import java.util.UUID;

/** Bounded cross-owner WorldOutcomeHost commands; implementations retain thread and identity validation. */
public interface WorldOutcomeHost {
    UUID canonicalOutcomeEncounter(UUID encounter);
    void completeWorldOutcome(Runnable completion);
}
