package cc.sighs.dndturn.platform.server.persistence;

import cc.sighs.dndturn.domain.ability.ProcessState;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterStateSnapshot;
import cc.sighs.dndturn.platform.server.action.AbilityCheckpoint;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

/** Server-thread owner of checkpoint writing and evidence; never restores live execution control. */
public final class EncounterPersistence {
    public record Opened(EncounterPersistence persistence, EncounterAuthority authority,
                         CombatPersistenceEnvelope checkpoint) {}

    private final CombatSavedData savedData;
    private final boolean recoveryFailed;
    private EncounterAuthority.Revision persistedRevision;
    private EncounterAuthority.Revision cachedRevision;
    private EncounterStateSnapshot cachedRules;
    private long persistedClock;
    private final Map<UUID, AbilityCheckpoint> checkpoints = new HashMap<>();
    private final Map<UUID, CombatPersistenceEnvelope.RecoveryAudit> audits = new HashMap<>();
    private Map<UUID, ProcessState> processes = Map.of();

    private EncounterPersistence(CombatSavedData savedData, boolean recoveryFailed,
                                 CombatPersistenceEnvelope checkpoint, EncounterAuthority.Revision revision) {
        this.savedData = savedData;
        this.recoveryFailed = recoveryFailed;
        this.persistedRevision = savedData.json().isBlank() ? null : revision;
        if (checkpoint != null) {
            checkpoints.putAll(checkpoint.abilities());
            audits.putAll(checkpoint.recoveryAudits());
            processes = checkpoint.processes();
            persistedClock = checkpoint.cumulativeServerTicks();
        }
    }

    /** Validates detached candidates before the runtime installs owners or publishes projections. */
    public static Opened open(CombatSavedData savedData, int roundTicks) {
        Objects.requireNonNull(savedData);
        EncounterAuthority authority = null;
        CombatPersistenceEnvelope checkpoint = null;
        boolean failed = false;
        try {
            checkpoint = savedData.envelope();
            if (checkpoint != null) authority = CombatRecoveryCandidate.validate(checkpoint, new Random());
        } catch (RuntimeException invalid) {
            checkpoint = null;
            failed = true;
            LogUtils.getLogger().error("Combat save cannot be safely restored; preserving it and disabling new encounters", invalid);
        }
        if (authority == null) authority = new EncounterAuthority(new Random(), roundTicks, roundTicks);
        return new Opened(new EncounterPersistence(savedData, failed, checkpoint, authority.revision()), authority, checkpoint);
    }

    public boolean recoveryFailed() { return recoveryFailed; }
    public void changed() { persistedRevision = null; }
    public AbilityCheckpoint checkpoint(UUID operation) { return checkpoints.get(operation); }
    public Map<UUID, AbilityCheckpoint> checkpoints() { return Map.copyOf(checkpoints); }
    public Map<UUID, CombatPersistenceEnvelope.RecoveryAudit> audits() { return Map.copyOf(audits); }
    public Map<UUID, ProcessState> processes() { return processes; }

    public void recordCheckpoint(AbilityCheckpoint evidence) {
        var previous = checkpoints.get(evidence.operation());
        if (previous != null && previous.phase() == AbilityCheckpoint.Phase.TERMINAL) return;
        checkpoints.put(evidence.operation(), evidence);
        changed();
    }

    /** Reconciliation appends evidence instead of rewriting terminal results or checkpoints. */
    public void appendAudit(CombatPersistenceEnvelope.RecoveryAudit audit) {
        audits.put(UUID.randomUUID(), Objects.requireNonNull(audit));
        changed();
    }

    /** SavedData.update marks data dirty; it does not promise disk durability. */
    public void persistIfChanged(EncounterAuthority authority, long clock,
                                 Function<EncounterStateSnapshot, CombatPersistenceEnvelope> capture) {
        if (recoveryFailed) return;
        var revision = authority.revision();
        if (revision.equals(persistedRevision) && clock - persistedClock < 20) return;
        if (cachedRules == null || !revision.equals(cachedRevision)) {
            cachedRules = authority.exportSnapshot();
            cachedRevision = revision;
        }
        savedData.update(capture.apply(cachedRules));
        // A failed capture or write must remain eligible for retry.
        persistedRevision = revision;
        persistedClock = clock;
    }
}
